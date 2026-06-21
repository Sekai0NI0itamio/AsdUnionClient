const DEFAULT_PORT = 25561;

/* ---- storage / proxy helpers ---- */

function storageGet(keys) {
  return new Promise((resolve) => chrome.storage.local.get(keys, resolve));
}
function storageSet(obj) {
  return new Promise((resolve) => chrome.storage.local.set(obj, resolve));
}
function proxySet(value) {
  return new Promise((resolve) =>
    chrome.proxy.settings.set({ value, scope: "regular" }, resolve)
  );
}

/* ---- domain helpers ---- */

function normalizeHost(host) {
  return (host || "").trim().toLowerCase().replace(/\.+$/, "");
}

function isIp(host) {
  if (!host) return false;
  if (host.includes(":")) return true;
  return /^\d{1,3}(\.\d{1,3}){3}$/.test(host);
}

function baseDomain(host) {
  if (!host) return "";
  if (host === "localhost") return host;
  if (isIp(host)) return host;
  const parts = host.split(".").filter(Boolean);
  if (parts.length <= 2) return host;
  return parts.slice(-2).join(".");
}

function parseInputToDomain(input) {
  let raw = (input || "").trim();
  if (!raw) return { error: "Empty" };
  let host = "";
  try {
    if (raw.includes("://")) host = new URL(raw).hostname;
    else if (raw.includes("/") || raw.includes("?"))
      host = new URL("http://" + raw).hostname;
    else host = raw;
  } catch {
    return { error: "Invalid URL" };
  }
  host = normalizeHost(host);
  if (!host) return { error: "Invalid host" };
  if (host.startsWith("[")) host = host.replace(/^\[|\]$/g, "");
  return { domain: baseDomain(host) };
}

/* ---- PAC builder ---- */

function buildPac(domains, port) {
  const list = JSON.stringify(domains);
  return (
    `var ROUTER_DOMAINS = ${list};\n` +
    `function FindProxyForURL(url, host) {\n` +
    `  for (var i = 0; i < ROUTER_DOMAINS.length; i++) {\n` +
    `    var d = ROUTER_DOMAINS[i];\n` +
    `    if (host === d || dnsDomainIs(host, "." + d)) {\n` +
    `      return "PROXY 127.0.0.1:${port}";\n` +
    `    }\n` +
    `  }\n` +
    `  return "DIRECT";\n` +
    `}\n`
  );
}

/* ---- state management ---- */

let cachedState = null;

async function loadState() {
  const res = await storageGet(["domains", "enabled", "proxyPort", "rules"]);
  let domains = Array.isArray(res.domains) ? res.domains : null;
  let enabled = typeof res.enabled === "boolean" ? res.enabled : false;

  if (!domains) {
    if (res.rules && typeof res.rules === "object") {
      domains = Object.keys(res.rules || {});
      enabled = domains.length > 0;
    } else {
      domains = [];
    }
  }

  domains = domains.map((d) => normalizeHost(d)).filter(Boolean);
  const proxyPort = res.proxyPort || DEFAULT_PORT;
  cachedState = { domains, enabled, proxyPort };
  return cachedState;
}

async function saveState(state) {
  cachedState = state;
  await storageSet({
    domains: state.domains,
    enabled: state.enabled,
    proxyPort: state.proxyPort,
  });
}

/* ---- URL → routed check ---- */

function isRoutedUrl(url, domains) {
  try {
    const host = new URL(url).hostname.toLowerCase();
    for (const d of domains) {
      if (host === d || host.endsWith("." + d)) return true;
    }
  } catch {
    /* ignore bad URLs */
  }
  return false;
}

/* ---- badge on extension icon ---- */

function updateBadge(state) {
  if (state.enabled && state.domains.length > 0) {
    chrome.action.setBadgeText({ text: "ON" });
    chrome.action.setBadgeBackgroundColor({ color: "#00c853" });
  } else {
    chrome.action.setBadgeText({ text: "" });
  }
}

/* ---- proxy + outline sync ---- */

async function applyProxy(state) {
  const domains = state.domains || [];
  if (!state.enabled || domains.length === 0) {
    await proxySet({ mode: "direct" });
  } else {
    const pac = buildPac(domains, state.proxyPort);
    await proxySet({ mode: "pac_script", pacScript: { data: pac } });
  }
  updateBadge(state);
  /* Push outline state to every open tab */
  updateAllTabs(state);
}

async function refreshState() {
  const state = await loadState();
  await applyProxy(state);
  return state;
}

/* ---- tab outline management ---- */

function sendRoutedToTab(tabId, routed) {
  chrome.tabs.sendMessage(tabId, { type: "setRouted", routed }).catch(() => {});
}

async function updateAllTabs(state) {
  if (!state) state = cachedState || (await loadState());
  const tabs = await chrome.tabs.query({
    url: ["http://*/*", "https://*/*"],
  });
  for (const tab of tabs) {
    const routed = state.enabled && isRoutedUrl(tab.url, state.domains);
    sendRoutedToTab(tab.id, routed);
  }
}

/* ---- navigation listeners (handle full navigations + SPA pushState) ---- */

chrome.webNavigation.onCommitted.addListener(async (details) => {
  if (details.frameId !== 0) return;
  const state = cachedState || (await loadState());
  const routed = state.enabled && isRoutedUrl(details.url, state.domains);
  sendRoutedToTab(details.tabId, routed);
});

chrome.webNavigation.onHistoryStateUpdated.addListener(async (details) => {
  if (details.frameId !== 0) return;
  const state = cachedState || (await loadState());
  const routed = state.enabled && isRoutedUrl(details.url, state.domains);
  sendRoutedToTab(details.tabId, routed);
});

/* ---- inject content script into existing tabs on install ---- */

async function injectExistingTabs() {
  const tabs = await chrome.tabs.query({
    url: ["http://*/*", "https://*/*"],
  });
  for (const tab of tabs) {
    chrome.scripting
      .executeScript({
        target: { tabId: tab.id },
        files: ["content.js"],
      })
      .catch(() => {});
  }
}

/* ---- migration from old Router Tunnel Toggle v1.x ---- */

const MIGRATED_KEY = "migrated_v1";

const LEGACY_DOMAINS = [
  "163.com",
  "alibabacloud.com",
  "aliyun.com",
  "bigmodel.cn",
  "bilibili.com",
  "bilivideo.com",
  "dmxapi.cn",
  "microsoftonline.com",
  "qq.com",
  "ssis-suzhou.net",
  "taobao.com",
  "z.ai",
  "zcode-ai.com",
];

async function migrateFromV1() {
  const res = await storageGet([MIGRATED_KEY, "domains"]);
  if (res[MIGRATED_KEY]) return; /* already migrated */
  if (Array.isArray(res.domains) && res.domains.length > 0) return; /* has data already */

  const domains = LEGACY_DOMAINS.map((d) => normalizeHost(d)).filter(Boolean);
  domains.sort();
  const state = { domains, enabled: true, proxyPort: DEFAULT_PORT };
  await saveState(state);
  await storageSet({ [MIGRATED_KEY]: true });
  console.log("[RouterTunnel] Migrated", domains.length, "domains from v1");
}

/* ---- lifecycle ---- */

chrome.runtime.onInstalled.addListener(() => {
  migrateFromV1().then(() => {
    refreshState();
    injectExistingTabs();
  });
});

chrome.runtime.onStartup.addListener(() => {
  refreshState();
});

/* ---- message handler ---- */

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  (async () => {
    /* Content script asks "is this page routed?" */
    if (msg.type === "checkRouted") {
      const state = cachedState || (await loadState());
      const url = sender.tab ? sender.tab.url : "";
      const routed = state.enabled && isRoutedUrl(url, state.domains);
      sendResponse({ routed });
      return;
    }

    if (msg.type === "getState") {
      const state = await refreshState();
      sendResponse({ ok: true, state });
      return;
    }

    if (msg.type === "setEnabled") {
      const state = await loadState();
      state.enabled = !!msg.enabled;
      await saveState(state);
      await applyProxy(state);
      sendResponse({ ok: true });
      return;
    }

    if (msg.type === "addDomain") {
      const state = await loadState();
      const parsed = parseInputToDomain(msg.input);
      if (parsed.error) {
        sendResponse({ ok: false, error: parsed.error });
        return;
      }
      if (!state.domains.includes(parsed.domain))
        state.domains.push(parsed.domain);
      state.domains.sort();
      await saveState(state);
      await applyProxy(state);
      sendResponse({ ok: true, domain: parsed.domain });
      return;
    }

    if (msg.type === "removeDomain") {
      const state = await loadState();
      const domain = normalizeHost(msg.domain);
      state.domains = state.domains.filter((d) => d !== domain);
      await saveState(state);
      await applyProxy(state);
      sendResponse({ ok: true });
      return;
    }

    if (msg.type === "setPort") {
      const state = await loadState();
      const port = Number(msg.proxyPort || 0);
      if (port > 0 && port <= 65535) {
        state.proxyPort = port;
        await saveState(state);
        await applyProxy(state);
      }
      sendResponse({ ok: true });
      return;
    }

    sendResponse({ ok: false, error: "Unknown message" });
  })();
  return true;
});
