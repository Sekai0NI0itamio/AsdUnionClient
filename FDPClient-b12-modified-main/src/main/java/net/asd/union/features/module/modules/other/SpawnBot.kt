// FDPClient Hacked Client
// SpawnBot module - spawns lightweight Mineflayer (Node.js) bots that run JS scripts.
package net.asd.union.features.module.modules.other

import net.asd.union.config.*
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.file.FileManager
import net.asd.union.handler.network.ConnectToRouter
import net.asd.union.ui.client.hud.HUD.addNotification
import net.asd.union.ui.client.hud.element.elements.Notification
import net.asd.union.ui.client.hud.element.elements.Type
import net.asd.union.utils.client.chat
import net.asd.union.utils.kotlin.RandomUtils
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

object SpawnBot : Module("SpawnBot", Category.OTHER, gameDetecting = false, hideModule = false) {

    // ── Directories ──────────────────────────────────────────────────────────
    val botsDir: File get() = File(FileManager.dir, "bots")
    val scriptsDir: File get() = File(botsDir, "scripts")

    // ── Settings ─────────────────────────────────────────────────────────────

    private val scriptValue = object : ListValue("Script", arrayOf("(none)"), "(none)") {
        override fun isSupported() = true
    }

    private val maxBots by int("MaxBots", 3, 1..20)
    private val spawnDelay by int("SpawnDelay", 1500, 0..10000, "ms")
    private val useRouter by boolean("UseRouter", true)

    // ── Active bot registry ───────────────────────────────────────────────────

    val activeBots: CopyOnWriteArrayList<BotEntry> = CopyOnWriteArrayList()
    private val botIdCounter = AtomicInteger(0)

    // ── Module lifecycle ──────────────────────────────────────────────────────

    override fun onInitialize() {
        ensureDirectories()
        ensureDefaultScript()
        ensureManual()
        refreshScriptList()
    }

    override fun onEnable() {
        // Enabling the module just arms it — no bots are spawned automatically.
        // The user must right-click the module in the ClickGUI and press "Spawn Bot".
        refreshScriptList()
    }

    override fun onDisable() {
        killAllBots()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun refreshScriptList() {
        val files = scriptsDir.listFiles { f -> f.extension == "js" }
            ?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()
        val options = if (files.isEmpty()) arrayOf("(none)") else files.toTypedArray()
        scriptValue.updateValues(options)
        if (scriptValue.value !in options) scriptValue.changeValue(options.first())
    }

    fun spawnBot() {
        if (activeBots.size >= maxBots) {
            chat("§c§lSpawnBot §7» §cMax bots ($maxBots) already running.")
            return
        }
        val scriptName = scriptValue.value
        if (scriptName == "(none)") {
            chat("§c§lSpawnBot §7» §cNo script selected. Add .js files to bots/scripts/.")
            return
        }
        val scriptFile = File(scriptsDir, "$scriptName.js")
        if (!scriptFile.exists()) {
            chat("§c§lSpawnBot §7» §cScript not found: $scriptName.js")
            return
        }
        val serverIp = mc.currentServerData?.serverIP ?: run {
            chat("§c§lSpawnBot §7» §cNot connected to a server.")
            return
        }
        val botName = RandomUtils.randomUsername()
        val botId = botIdCounter.incrementAndGet()
        val entry = BotEntry(id = botId, name = botName, serverIp = serverIp, scriptName = scriptName)
        activeBots += entry
        addNotification(Notification("Spawning bot §a$botName§r on §e$serverIp", "SpawnBot", Type.INFO, 3000))
        Thread({ runBot(entry, scriptFile, serverIp, botName) }, "SpawnBot-$botId").apply {
            isDaemon = true
            start()
        }
    }

    fun killBot(botId: Int) {
        val entry = activeBots.firstOrNull { it.id == botId } ?: return
        entry.process?.destroyForcibly()
        activeBots.remove(entry)
        addNotification(Notification("Terminated bot §c${entry.name}", "SpawnBot", Type.ERROR, 2000))
    }

    fun killAllBots() {
        activeBots.forEach { it.process?.destroyForcibly() }
        activeBots.clear()
    }

    // ── Bot process runner ────────────────────────────────────────────────────

    private fun runBot(entry: BotEntry, scriptFile: File, serverIp: String, botName: String) {
        try {
            val colonIdx = serverIp.lastIndexOf(':')
            val host = if (colonIdx > 0) serverIp.substring(0, colonIdx) else serverIp
            val port = if (colonIdx > 0) serverIp.substring(colonIdx + 1).toIntOrNull() ?: 25565 else 25565

            val env = buildBotEnv(botName, host, port)
            val nodeExe = resolveNodeExecutable()

            val pb = ProcessBuilder(nodeExe, scriptFile.absolutePath)
                .directory(scriptsDir)
                .redirectErrorStream(true)
            pb.environment().putAll(env)

            val process = pb.start()
            entry.process = process
            entry.status = BotStatus.CONNECTING
            // Log the script source so the popup viewer can show it
            entry.appendLog("=== Script: ${scriptFile.name} ===")
            try { scriptFile.readLines().forEach { entry.appendLog(it) } } catch (_: Exception) {}
            entry.appendLog("=== End of script ===")
            entry.appendLog("--- Bot output ---")

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                handleBotOutput(entry, line ?: continue)
            }

            val exitCode = process.waitFor()
            entry.status = BotStatus.DISCONNECTED
            entry.pingMs = -1
            activeBots.remove(entry)

            if (exitCode != 0) {
                addNotification(Notification("Bot §c${entry.name}§r exited (code $exitCode)", "SpawnBot", Type.ERROR, 3000))
            } else {
                addNotification(Notification("Bot §a${entry.name}§r disconnected cleanly.", "SpawnBot", Type.INFO, 2000))
            }
        } catch (e: Exception) {
            entry.status = BotStatus.ERROR
            activeBots.remove(entry)
            addNotification(Notification("Bot §c${entry.name}§r crashed: ${e.message}", "SpawnBot", Type.ERROR, 4000))
        }
    }

    private fun handleBotOutput(entry: BotEntry, line: String) {
        // Always log the raw line
        entry.appendLog(line)

        val trimmed = line.trim()
        if (!trimmed.startsWith("{")) return
        try {
            val obj = com.google.gson.JsonParser().parse(trimmed).asJsonObject
            when (obj.get("type")?.asString) {
                "status" -> entry.status = when (obj.get("value")?.asString) {
                    "online"       -> BotStatus.ONLINE
                    "connecting"   -> BotStatus.CONNECTING
                    "disconnected" -> BotStatus.DISCONNECTED
                    else           -> BotStatus.ONLINE
                }
                "ping" -> entry.pingMs = obj.get("value")?.asInt ?: -1
                "cmd"  -> entry.currentCommand = obj.get("value")?.asString ?: entry.currentCommand
                "chat" -> entry.appendLog("[chat] ${obj.get("value")?.asString ?: ""}")
            }
        } catch (_: Exception) {}
    }

    // ── Environment builder ───────────────────────────────────────────────────

    private fun buildBotEnv(botName: String, host: String, port: Int): Map<String, String> {
        val env = mutableMapOf(
            "BOT_NAME"    to botName,
            "BOT_HOST"    to host,
            "BOT_PORT"    to port.toString(),
            "BOT_VERSION" to "1.8.9",
            "BOT_AUTH"    to "offline"
        )
        val at = AutoText
        if (at.state) {
            env["AUTOTEXT_ENABLED"]  = "true"
            env["AUTOTEXT_BOT_ONLY"] = "false"
            env["AUTOTEXT_MESSAGES"] = at.getMessages().joinToString("||") { it.second }
        }
        val aa = AutoAccount
        if (aa.state) env["AUTOACCOUNT_ENABLED"] = "true"
        if (useRouter && ConnectToRouter.isTunnelMode()) {
            env["ROUTER_TUNNEL_IP"]   = ConnectToRouter.tunnelIp
            env["ROUTER_TUNNEL_PORT"] = ConnectToRouter.TUNNEL_PORT.toString()
        }
        return env
    }

    // ── Node.js resolution ────────────────────────────────────────────────────

    private fun resolveNodeExecutable(): String {
        for (candidate in listOf("node", "/usr/local/bin/node", "/usr/bin/node", "/opt/homebrew/bin/node")) {
            try {
                if (ProcessBuilder(candidate, "--version").redirectErrorStream(true).start().waitFor() == 0)
                    return candidate
            } catch (_: Exception) {}
        }
        return "node"
    }

    // ── Directory / file setup ────────────────────────────────────────────────

    private fun ensureDirectories() {
        if (!botsDir.exists()) botsDir.mkdirs()
        if (!scriptsDir.exists()) scriptsDir.mkdirs()
    }

    private fun ensureDefaultScript() {
        val f = File(scriptsDir, "ultimis.js")
        if (!f.exists()) f.writeText(buildUltimisScript())
    }

    private fun ensureManual() {
        val f = File(botsDir, "manual.md")
        if (!f.exists()) f.writeText(buildManual())
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    enum class BotStatus { CONNECTING, ONLINE, DISCONNECTED, ERROR }

    data class BotEntry(
        val id: Int,
        val name: String,
        val serverIp: String,
        val scriptName: String,
        var process: Process? = null,
        var status: BotStatus = BotStatus.CONNECTING,
        var pingMs: Int = -1,
        /** Rolling log of all stdout lines, chat messages, and errors from this bot. */
        val log: ArrayDeque<String> = ArrayDeque(500),
        /** The last "command" line the script reported (e.g. "goto(skypvp)"). */
        var currentCommand: String = "starting..."
    ) {
        /** Append a line to the log, capping at 500 entries. */
        fun appendLog(line: String) {
            if (log.size >= 500) log.removeFirst()
            log.addLast(line)
        }
    }

    // ── Default script builder ────────────────────────────────────────────────
    // Note: block-comment markers are split to avoid confusing the Kotlin lexer
    // when this source file is compiled. The output file is valid JS.

    private fun buildUltimisScript(): String {
        val cb = "/" + "*"   // /*
        val ce = "*" + "/"   // */
        return """
$cb*
 * ultimis.js - Default SpawnBot script for AsdUnionClient
 *
 * Environment variables injected by the client:
 *   BOT_NAME, BOT_HOST, BOT_PORT, BOT_VERSION, BOT_AUTH
 *   AUTOTEXT_ENABLED, AUTOTEXT_BOT_ONLY, AUTOTEXT_MESSAGES  (|| separated)
 *   AUTOACCOUNT_ENABLED
 *   ROUTER_TUNNEL_IP, ROUTER_TUNNEL_PORT  (if router is active)
 *
 * Structured output (JSON lines) is parsed by the client:
 *   {"type":"status","value":"online"}
 *   {"type":"ping","value":42}
 *
 * See bots/manual.md for the full scripting API reference.
 $ce

const mineflayer = require('mineflayer');
const { pathfinder, Movements, goals } = require('mineflayer-pathfinder');

// Config from client
const BOT_NAME    = process.env.BOT_NAME    || 'UltimisBot';
const BOT_HOST    = process.env.BOT_HOST    || 'localhost';
const BOT_PORT    = parseInt(process.env.BOT_PORT || '25565', 10);
const BOT_VERSION = process.env.BOT_VERSION || '1.8.9';
const BOT_AUTH    = process.env.BOT_AUTH    || 'offline';

const AUTOTEXT_ENABLED   = process.env.AUTOTEXT_ENABLED === 'true';
const AUTOTEXT_BOT_ONLY  = process.env.AUTOTEXT_BOT_ONLY === 'true';
const AUTOTEXT_MESSAGES  = (process.env.AUTOTEXT_MESSAGES || '').split('||').filter(Boolean);
const AUTOACCOUNT_ENABLED = process.env.AUTOACCOUNT_ENABLED === 'true';

function log(type, value) {
  process.stdout.write(JSON.stringify({ type, value }) + '\n');
}

function wait(seconds) {
  return new Promise(resolve => setTimeout(resolve, seconds * 1000));
}

function waitUntil(nameContains, timeoutSeconds = 60) {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + timeoutSeconds * 1000;
    const interval = setInterval(() => {
      const entity = Object.values(bot.entities).find(e =>
        e.username && e.username.toLowerCase().includes(nameContains.toLowerCase())
      );
      if (entity) { clearInterval(interval); resolve(entity); return; }
      if (Date.now() > deadline) { clearInterval(interval); reject(new Error('waitUntil timeout: ' + nameContains)); }
    }, 500);
  });
}

function findEntity(nameContains) {
  return Object.values(bot.entities).find(e =>
    e.username && e.username.toLowerCase().includes(nameContains.toLowerCase())
  ) || null;
}

async function gotoEntity(nameContains) {
  const entity = findEntity(nameContains);
  if (!entity) return;
  const goal = new goals.GoalNear(entity.position.x, entity.position.y, entity.position.z, 2);
  bot.pathfinder.setGoal(goal);
  await new Promise(resolve => {
    bot.once('goal_reached', resolve);
    setTimeout(resolve, 15000);
  });
}

async function attackEntity(nameContains) {
  const entity = findEntity(nameContains);
  if (!entity) return;
  const dx = entity.position.x - bot.entity.position.x;
  const dz = entity.position.z - bot.entity.position.z;
  const yaw = Math.atan2(-dx, -dz);
  const dy = (entity.position.y + entity.height / 2) - (bot.entity.position.y + bot.entity.height);
  const dist = Math.sqrt(dx * dx + dz * dz);
  const pitch = -Math.atan2(dy, dist);
  await bot.look(yaw, pitch, true);
  bot.attack(entity);
}

async function walk(blocks, direction) {
  const dir = (direction || 'w').toLowerCase();
  const controls = { w: 'forward', s: 'back', a: 'left', d: 'right' };
  const ctrl = controls[dir] || 'forward';
  bot.setControlState(ctrl, true);
  await wait(blocks * 0.45);
  bot.setControlState(ctrl, false);
}

let autoTextInterval = null;
function startAutoText() {
  if (!AUTOTEXT_ENABLED || !AUTOTEXT_BOT_ONLY || AUTOTEXT_MESSAGES.length === 0) return;
  autoTextInterval = setInterval(() => {
    const msg = AUTOTEXT_MESSAGES[Math.floor(Math.random() * AUTOTEXT_MESSAGES.length)];
    if (msg) bot.chat(msg);
  }, 2400 + Math.random() * 600);
}

function stopAutoText() {
  if (autoTextInterval) { clearInterval(autoTextInterval); autoTextInterval = null; }
}

function setupAutoAccount() {
  if (!AUTOACCOUNT_ENABLED) return;
  bot.on('message', (jsonMsg) => {
    const msg = jsonMsg.toString().toLowerCase();
    if (msg.includes('/register') || msg.includes('register first') || msg.includes('must register')) {
      const pw = 'zywl1337#';
      bot.chat('/register ' + pw + ' ' + pw);
    } else if (msg.includes('/login') || msg.includes('login first') || msg.includes('must login')) {
      bot.chat('/login zywl1337#');
    }
  });
}

const bot = mineflayer.createBot({
  host:     BOT_HOST,
  port:     BOT_PORT,
  username: BOT_NAME,
  version:  BOT_VERSION,
  auth:     BOT_AUTH,
});

bot.loadPlugin(pathfinder);

bot.once('spawn', async () => {
  log('status', 'online');
  const defaultMove = new Movements(bot);
  bot.pathfinder.setMovements(defaultMove);
  setupAutoAccount();

  // ultimis sequence
  try {
    // 1. wait autoAccount - give AutoAccount time to register/login
    await wait(3);
    // 2. waitUntil(skypvp) appears
    await waitUntil('skypvp');
    // 3. goto(skypvp)
    await gotoEntity('skypvp');
    // 4. attack(skypvp)
    await attackEntity('skypvp');
    // 5. wait(2)
    await wait(2);
    // 6. walk(1)(w) - walk forward one block
    await walk(1, 'w');
    // 7. sync autotext - start sending AutoText messages if BotOnly is enabled
    startAutoText();
  } catch (err) {
    log('status', 'error: ' + err.message);
  }
});

bot.on('physicsTick', () => {
  log('ping', bot.player ? (bot._client.latency || 0) : -1);
});

bot.on('end', () => {
  stopAutoText();
  log('status', 'disconnected');
  process.exit(0);
});

bot.on('error', (err) => {
  log('status', 'error: ' + err.message);
  process.exit(1);
});
""".trimIndent()
    }

    // ── Manual builder ────────────────────────────────────────────────────────

    private fun buildManual(): String = """
# SpawnBot - Scripting Manual

SpawnBot spawns lightweight Mineflayer (Node.js) bots that run user-defined JS scripts.
Scripts live in: `<minecraft data dir>/AsdUnionClient/bots/scripts/*.js`

---

## Getting Started

1. Install Node.js (https://nodejs.org) and mineflayer:
   ```
   npm install -g mineflayer mineflayer-pathfinder
   ```
2. Place your `.js` script in the `bots/scripts/` folder.
3. Open the SpawnBot module in the ClickGUI, select your script, and enable the module.

---

## Environment Variables (injected by the client)

| Variable              | Description                                      |
|-----------------------|--------------------------------------------------|
| `BOT_NAME`            | Random username generated by the client          |
| `BOT_HOST`            | Server hostname the main account is connected to |
| `BOT_PORT`            | Server port (default 25565)                      |
| `BOT_VERSION`         | Always `1.8.9`                                   |
| `BOT_AUTH`            | Always `offline`                                 |
| `AUTOTEXT_ENABLED`    | `true` if AutoText module is on                  |
| `AUTOTEXT_BOT_ONLY`   | `true` if AutoText BotOnly option is enabled     |
| `AUTOTEXT_MESSAGES`   | `||`-separated list of messages from AutoText    |
| `AUTOACCOUNT_ENABLED` | `true` if AutoAccount module is on               |
| `ROUTER_TUNNEL_IP`    | Tunnel IP (if router is active)                  |
| `ROUTER_TUNNEL_PORT`  | Tunnel port (if router is active)                |

---

## Structured Output (JSON lines - client parses these)

Emit JSON lines to stdout to communicate status back to the client HUD:

```js
// Report online status
process.stdout.write(JSON.stringify({ type: 'status', value: 'online' }) + '\n');

// Report ping
process.stdout.write(JSON.stringify({ type: 'ping', value: 42 }) + '\n');
```

---

## Built-in Helper Functions (available in ultimis.js template)

### `wait(seconds)`
Pause execution for the given number of seconds.
```js
await wait(2);
```

### `waitUntil(nameContains, timeoutSeconds = 60)`
Wait until a player whose username contains `nameContains` appears in the world.
```js
await waitUntil('skypvp');
```

### `gotoEntity(nameContains)`
Walk to the entity whose username contains `nameContains` (uses mineflayer-pathfinder).
```js
await gotoEntity('skypvp');
```

### `attackEntity(nameContains)`
Slowly turn toward the entity and left-click attack it.
```js
await attackEntity('skypvp');
```

### `walk(blocks, direction)`
Walk a number of blocks in a direction (`w`, `s`, `a`, `d`).
```js
await walk(1, 'w'); // walk forward 1 block
```

---

## Module Integrations

### AutoText (sync autotext)
When the main account has **AutoText** enabled with **BotOnly** turned on:
- The main account will NOT send any messages.
- The bot will inherit the full AutoText message list and send them itself.

To activate in your script:
```js
startAutoText(); // starts the inherited AutoText loop
```

### AutoAccount (wait autoAccount)
When **AutoAccount** is enabled, the bot will automatically respond to `/register` and
`/login` prompts from the server using the same password logic as the main account.

To activate in your script:
```js
setupAutoAccount(); // call once after spawn
```

---

## Script Sequence Syntax (ultimis.js style)

The default `ultimis.js` script demonstrates the recommended sequence pattern:

```
wait autoAccount          -> await wait(3)
waitUntil(skypvp) appears -> await waitUntil('skypvp')
goto(skypvp)              -> await gotoEntity('skypvp')
attack(skypvp)            -> await attackEntity('skypvp')
wait(2)                   -> await wait(2)
walk(1)(w)                -> await walk(1, 'w')
sync autotext             -> startAutoText()
```

---

## HUD Display

Active bots appear in the BotStatus HUD element with:
- Bot name and connection status dot
- Ping in ms (color-coded: green < 80ms, yellow < 200ms, red >= 200ms)
- Server IP shown on hover
- x button to forcefully terminate the bot

---

## Tips

- Use `bot.chat('/command')` to send chat messages or commands.
- Use `bot.pathfinder.setGoal(new goals.GoalNear(x, y, z, radius))` for custom navigation.
- The bot runs as a completely separate Node.js process with no access to Minecraft client memory.
  All communication is via environment variables (in) and stdout JSON lines (out).
- You can spawn multiple bots simultaneously (up to the MaxBots setting).
- Scripts are hot-reloaded from disk each time you spawn a bot - no restart needed.
- The bot's account name, password, and server are documented in AutoAccount's password store
  when AutoAccount is enabled with RecordPasswords on.
""".trimIndent()
}
