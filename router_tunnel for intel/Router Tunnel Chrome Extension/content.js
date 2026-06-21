/**
 * Router Tunnel content script
 * Marks routed tabs with a green favicon dot + title prefix.
 * Idempotent — safe to inject multiple times.
 */
(function () {
  if (document.documentElement.hasAttribute("rt-injected")) return;
  document.documentElement.setAttribute("rt-injected", "");

  const PREFIX = "\u{1F7E2} "; /* green circle emoji */
  let isRouted = false;
  let originalTitle = "";
  let titleObserver = null;
  let originalFaviconHref = null;
  let greenFaviconUrl = null;

  /* ---- Favicon overlay ---- */

  function createGreenFavicon(srcUrl) {
    return new Promise((resolve) => {
      const size = 32;
      const canvas = document.createElement("canvas");
      canvas.width = size;
      canvas.height = size;
      const ctx = canvas.getContext("2d");

      const drawDot = () => {
        /* Green dot in bottom-right corner */
        ctx.beginPath();
        ctx.arc(size - 7, size - 7, 6, 0, Math.PI * 2);
        ctx.fillStyle = "#00c853";
        ctx.fill();
        ctx.strokeStyle = "#fff";
        ctx.lineWidth = 1.5;
        ctx.stroke();
        resolve(canvas.toDataURL("image/png"));
      };

      if (srcUrl) {
        const img = new Image();
        img.crossOrigin = "anonymous";
        img.onload = () => {
          ctx.drawImage(img, 0, 0, size, size);
          drawDot();
        };
        img.onerror = () => {
          /* No favicon or CORS blocked — draw dot on blank */
          drawDot();
        };
        img.src = srcUrl;
      } else {
        drawDot();
      }
    });
  }

  async function setFavicon(routed) {
    const links = document.querySelectorAll("link[rel*='icon']");
    if (routed) {
      /* Save originals */
      if (!originalFaviconHref && links.length > 0) {
        originalFaviconHref = Array.from(links).map((l) => l.href);
      }
      /* Create green overlay */
      const srcHref =
        originalFaviconHref && originalFaviconHref.length > 0
          ? originalFaviconHref[0]
          : null;
      greenFaviconUrl = await createGreenFavicon(srcHref);

      /* Remove existing, add new */
      links.forEach((l) => l.remove());
      const newLink = document.createElement("link");
      newLink.rel = "icon";
      newLink.type = "image/png";
      newLink.href = greenFaviconUrl;
      newLink.setAttribute("rt-favicon", "1");
      document.head.appendChild(newLink);
    } else {
      /* Restore originals */
      document.querySelectorAll("link[rt-favicon]").forEach((l) => l.remove());
      if (originalFaviconHref) {
        for (const href of originalFaviconHref) {
          const link = document.createElement("link");
          link.rel = "icon";
          link.href = href;
          document.head.appendChild(link);
        }
      }
      originalFaviconHref = null;
      greenFaviconUrl = null;
    }
  }

  /* ---- Title prefix ---- */

  function stripPrefix(title) {
    return title.startsWith(PREFIX) ? title.slice(PREFIX.length) : title;
  }

  function setTitle(routed) {
    const current = document.title || "";
    const base = stripPrefix(current);
    originalTitle = base;
    document.title = routed ? PREFIX + base : base;
  }

  function startTitleObserver() {
    if (titleObserver) return;
    const el = document.querySelector("title");
    if (!el) return;

    titleObserver = new MutationObserver(() => {
      if (!isRouted) return;
      const current = document.title || "";
      if (!current.startsWith(PREFIX)) {
        const base = stripPrefix(current);
        originalTitle = base;
        document.title = PREFIX + base;
      }
    });
    titleObserver.observe(el, { childList: true, characterData: true, subtree: true });
  }

  /* ---- Show / Hide ---- */

  async function show() {
    if (isRouted) return;
    isRouted = true;
    setTitle(true);
    await setFavicon(true);
    startTitleObserver();
  }

  async function hide() {
    if (!isRouted) return;
    isRouted = false;
    if (titleObserver) {
      titleObserver.disconnect();
      titleObserver = null;
    }
    setTitle(false);
    await setFavicon(false);
  }

  /* ---- Listen for updates from background ---- */

  chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
    if (msg.type === "setRouted") {
      if (msg.routed) show();
      else hide();
      sendResponse({ ok: true });
    }
    return false;
  });

  /* ---- On load, ask background whether this page is routed ---- */

  chrome.runtime.sendMessage({ type: "checkRouted" }, (resp) => {
    if (chrome.runtime.lastError) return;
    if (resp && resp.routed) show();
  });
})();
