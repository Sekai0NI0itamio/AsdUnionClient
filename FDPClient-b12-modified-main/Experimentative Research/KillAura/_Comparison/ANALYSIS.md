# KillAura — Comparative Analysis & Improvement Plan

This document compares the **KillAura** implementations in three Minecraft
hacked clients, identifies the architectural differences, and proposes concrete
improvements for the current client (FDPClient-b12-modified / `net.asd.union`).

The two reference clients are:

- **LiquidBounce** (`net.ccbluex.liquidbounce`, modern
  `killaura/` package, 2025-2026 codebase). Cloned from
  `https://github.com/CCBlueX/LiquidBounce`.
- **Raven-bPLUS** (`keystrokesmod.client`, the 1.8.9 build at
  `main` branch, captured 2024-08-23). Extracted from
  `https://github.com/Kopamed/Raven-bPLUS` via the Wayback Machine
  (the GitHub repo is DMCA-blocked since 2025-07-15; see `README.md`).

All source files copied into this folder are unmodified — line numbers in this
document refer to the copied files under `LiquidBounce/`, `Raven-bPLUS/` and
`CurrentClient/`.

---

## 1. High-level architecture comparison

| Aspect | CurrentClient (`KillAura.kt`) | LiquidBounce (`ModuleKillAura.kt`) | Raven-bPLUS (`AimAssist.java`) |
|---|---|---|---|
| Language | Kotlin (FDPClient fork) | Kotlin (rewrite) | Java (1.8.9) |
| Engine / Loader | Forge 1.8.9 | Fabric 1.21.x | Forge 1.8.9 |
| Architecture | One mega-module (1600+ lines) + `KillAuraTargeter` | 11 sub-files, all `object`s grouped in a `killaura` package | One per combat feature (`AimAssist`, `AutoBlock`, `AutoWeapon`, `BurstClicker`, `ClickAssist`, `HitBox`, `LeftClicker`, `Velocity`, `WTap`) |
| Targeting | Inline in `KillAura.onUpdate()` | `KillAuraTargetTracker` extends `TargetTracker` | None inside `AimAssist` — relies on the user's crosshair (`mc.objectMouseOver.entityHit`) |
| Rotation engine | `RotationUtils` + `SimulatedPlayer` + `RaycastUtils` (legacy) | `RotationManager` + `Rotation` + `RotationsValueGroup` | **None** — does not rotate the player at all, only attacks what the player already looks at |
| Clicker | `CPSCounter` + `randomClickDelay` (timing-based) | `KillAuraClicker` with `prepareForAttack` queue, miss-time aware | `AutoBlock`/`BurstClicker` simulate left-click via `KeyBinding.setKeyBindState` + LWJGL `Mouse.isButtonDown(0)` checks |
| AutoBlock | Inline, multiple modes, key-swap, blink | `KillAuraAutoBlock` as a `ToggleableValueGroup`, `BlinkManager` integration | Separate `AutoBlock` module: engages useItem when the crosshair-entity is within a distance range, for a fixed duration |
| AutoWeapon | Inline `SilentHotbar` swap | `ModuleAutoWeapon` (referenced) | `AutoWeapon` module: swaps to highest-damage slot when LMB is down and a target is visible |
| Failure handling | Fails silently (just doesn't attack) | `KillAuraFailSwing`: fake-swing + `KillAuraNotifyWhenFail` sound/box | None — relies on Vanilla reach checks |
| Server-side ray-cast | `raycastEntity` with `runWithModifiedRaycastResult` | `findEntityInCrosshair`, `traceFromPlayer`, `isLookingAtEntity` (mod-aware) | None |
| Shield handling | `hitCondition` (vanilla 1.8.9) | `KillAuraTargetTracker.validateShield` + `ModuleAutoWeapon.willShieldBreak` | None — relies on Vanilla |
| Cooldown simulation | `simulateCooldown` boolean + `CooldownHelper` | Implicit (vanilla 1.9+) or `simulateCooldown` (1.8) | None — Vanilla |
| Multi-target | Yes (`multi` mode) | `targetTracker.targets()` (list) | No |
| Config storage | `Value` system with `onChange`/`onChanged` | `Value` system with `ConfigSystem` + `ValueType.INVALID` for migration | `SliderSetting` / `DoubleSliderSetting` / `TickSetting` / `ModeSetting` |

### The big picture

- **Raven-bPLUS (Java, 1.8.9)** is a "legit" client: it **never** rotates the
  player. Combat effectiveness comes from a small toolbox of
  lightweight modules that assist the user's existing aim
  (`AutoBlock`, `AutoWeapon`, `BurstClicker`, `WTap`, `HitBox`, `Velocity`,
  `ClickAssist`). There is no aimbot — only **click assistance** and
  **input automation**.
- **LiquidBounce (Kotlin, 1.21.x)** is a "modern" cheat: it has a full
  `RotationManager`, multi-target tracker, fail-swing, blink-packet
  auto-block, and a migration layer for old configs. It is engineered for
  multi-version, multi-server use.
- **The current client (Kotlin, 1.8.9, FDP fork)** is a hybrid: it has
  LiquidBounce's rotation / raycast system bolted onto a 1.8.9 codebase, plus
  a unique `KillAuraTargeter` helper for visual targeting. It is closer to
  LiquidBounce's old `aura` package, but inlined into a single file.

---

## 2. How each one actually works

### 2.1 CurrentClient — `KillAura.kt` / `KillAuraTargeter.kt`

(See [CurrentClient/KillAura.kt](CurrentClient/KillAura.kt) and
[CurrentClient/KillAuraTargeter.kt](CurrentClient/KillAuraTargeter.kt).)

Flow (per tick):

1. `onUpdate` fires, checks player is alive and not in screens.
2. If `KillAuraTargeter` is enabled, it pre-selects a target every N ticks
   (FOV / range / hitbox-multiplier / `hitThroughEntities` filters), stores it
   as `targetEntity`, and draws ESP.
3. Otherwise `KillAura` runs its own target search through
   `world.loadedEntityList`, applying `isSelected` (entity class / living /
   mob / animal / sleeping / invisible / teams / friends) and a range check.
4. Target is cached for `BACKGROUND_TARGET_CACHE_INTERVAL` ticks to avoid
   flipping every frame.
5. **Clicker**: `CPSCounter` waits for a random delay in
   `[1000/minCPS, 1000/maxCPS]` ms. If `simulateCooldown` is on, the cooldown
   helper blocks attacks until the 1.9+ cooldown is full.
6. **Rotation**: `SimulatedPlayer` simulates the player with the proposed
   rotations and `RaycastUtils.raycastEntity` checks the hit through walls. The
   rotation is committed to `RotationUtils.currentRotation` and is restored on
   tick end.
7. **Block**: When the held item is a sword, the player must unblock to attack
   (1.9+). The module picks `BlockMode`: `Vanilla` (releases useItem briefly),
   `Interact` (right-clicks the air), `AfterAttack` (blocks only when about to
   be hit), `Packet` (sends `C08PacketPlayerBlockPlacement` only, no client
   state change). It also re-blocks after a delay and can blink the slot
   changes via `BlinkUtils`.
8. **AutoWeapon**: silently switches hotbar to the highest-damage slot via
   `SilentHotbar` and reverts after the attack.
9. **Packet attack**: `C02PacketUseEntity.ATTACK` is sent, the server confirms
   the hit on the next packet, and the client swings the hand.

Distinctive features in the current client:

- `KillAuraTargeter` — a separate module that pre-selects a target
  for KillAura based on FOV / hitbox / distance. The `ThroughWalls` flag and
  `hitboxMultiplier` make this very flexible.
- `RaycastUtils.runWithModifiedRaycastResult` — overrides the in-game ray-trace
  during the attack so server-side anti-cheat sees the same thing the client
  does.
- `BlinkUtils` — buffers block-swap and useItem packets while attacking to
  keep the server in the dark.
- `SimulatedPlayer` — a copy of the player with modified rotations used to
  raycast in 1.8.9 (no client-side rotation is committed unless the simulated
  raycast succeeds).

### 2.2 LiquidBounce — `killaura/` package

(See [LiquidBounce/ModuleKillAura.kt](LiquidBounce/ModuleKillAura.kt),
[LiquidBounce/KillAuraTargetTracker.kt](LiquidBounce/KillAuraTargetTracker.kt),
[LiquidBounce/KillAuraRotationsValueGroup.kt](LiquidBounce/KillAuraRotationsValueGroup.kt),
[LiquidBounce/KillAuraClicker.kt](LiquidBounce/KillAuraClicker.kt),
[LiquidBounce/features/KillAuraRange.kt](LiquidBounce/features/KillAuraRange.kt),
[LiquidBounce/features/KillAuraAutoBlock.kt](LiquidBounce/features/KillAuraAutoBlock.kt),
[LiquidBounce/features/KillAuraFailSwing.kt](LiquidBounce/features/KillAuraFailSwing.kt).)

Architecture: 11 small `object`s, each with a single responsibility:

| File | Job |
|---|---|
| `ModuleKillAura.kt` | The Module itself — onUpdate timer, anti-Bot, fight-bot orchestration, target processing |
| `KillAuraRequirements.kt` | Hot-bar / inventory / bed checks (don't attack in inventory) |
| `KillAuraRotationsValueGroup.kt` | Rotation engine config (timing, FOV, smoothing, randomization) |
| `KillAuraTargetTracker.kt` | `TargetTracker` base class — multi-target, distance, angle, ignore-shield |
| `KillAuraClicker.kt` | `prepareForAttack` queue with `missTime` handling |
| `features/KillAuraRange.kt` | Randomized range per tick (anti-cheat pattern) |
| `features/KillAuraAutoBlock.kt` | Blink-aware autoblock with `BlockMode` and `UnblockMode` enums |
| `features/KillAuraFailSwing.kt` | Fake swing when ray-trace misses |
| `features/KillAuraFightBot.kt` | Click logic that depends on rotation state |
| `features/KillAuraNotifyWhenFail.kt` | Sound / box on failed hit |
| `features/KillAuraRangeIndicator.kt` | Render |

The pipeline per tick is:

1. `KillAuraTargetTracker.targets()` enumerates all valid targets every tick
   and filters by distance, FOV, alive, friend, team, shield.
2. `KillAuraRange.update()` re-randomizes the scan range addition each tick
   (`scanRangeIncrease.random()`).
3. `KillAuraRotationsValueGroup` chooses a rotation timing:
   - `NORMAL` — angle-plans once per attack cycle.
   - `SNAP` — instant rotation per attack.
   - `ON_TICK` — re-aims every tick.
4. `RotationManager.currentRotation` is set; the next attack uses that rotation.
5. `KillAuraClicker.prepareForAttack` queues the swing — it can be
   executed immediately or on the next event hook.
6. `KillAuraAutoBlock.startBlocking()` / `stopBlocking()` enforce a blocking
   state in sync with the attack. It uses the `BlinkManager` to queue packets
   so the server only sees the *final* block state.
7. `KillAuraFailSwing.dealWithFakeSwing()` performs a fake swing on a missed
   ray-trace to keep CPS up.

Distinctive features of LiquidBounce:

- **Mod-aware ray-tracing** — `findEntityInCrosshair` / `traceFromPlayer`
  honour server-side hit-box mods (e.g. hypixel bedwars).
- **Blink-aware block/unblock** — packets are queued through `BlinkManager` so
  the server never sees the transient unblock.
- **Ignore-shield target filter** — auto-skips targets whose shield is up
  unless an axe is held (or `ModuleAutoWeapon` will break the shield).
- **Tick-aware event hooks** — every other system uses an event
  (`GameTickEvent`, `BlinkPacketEvent`, `PacketEvent`, `WorldChangeEvent`,
  `AttackEntityEvent`).
- **Dynamic config migration** — `KillAuraRange.migrateFromValues` reads the
  old `Range` / `WallRange` / `ScanExtraRange` JSON format and converts it
  on the fly. This is why old users never lose their settings.
- **`RotationsValueGroup`** is a reusable, generic rotation config block that
  *any* combat module can drop in.

### 2.3 Raven-bPLUS — `AimAssist` / `AutoBlock` / `AutoWeapon` / `BurstClicker`

(See [Raven-bPLUS/AimAssist.java](Raven-bPLUS/AimAssist.java),
[AutoBlock.java](Raven-bPLUS/AutoBlock.java),
[AutoWeapon.java](Raven-bPLUS/AutoWeapon.java),
[BurstClicker.java](Raven-bPLUS/BurstClicker.java).)

**AimAssist is not a KillAura.** It is a click-assist that:

1. Checks if the player is in game, not in a screen, and `Mouse.isButtonDown(0)`.
2. Checks if `mc.objectMouseOver.entityHit != null`.
3. Checks that the entity is within a configurable FOV cone from the player's
   current gaze (this is *not* a rotation; it just picks a target that the
   player is already looking near).
4. Checks that the entity is alive, not a `EntityMob` (toggleable), and not
   `isInvisible()` (toggleable).
5. **Attacks** by simulating the LMB: `KeyBinding.setKeyBindState(0, true)`
   + `KeyBinding.onTick(0)`. There is no `C02PacketUseEntity`, no rotation
   engine, no ray-cast.
6. Optionally performs a "crit" — a timed jump + small downward velocity
   to satisfy 1.8.9 crit rules.

The supporting modules do the rest:

- **`AutoBlock`** ([AutoBlock.java](Raven-bPLUS/AutoBlock.java)) — when LMB is
  down and `mc.objectMouseOver.entityHit` is within a distance slider, it
  engages the useItem key for a configurable duration in milliseconds, then
  releases. Chance is a percentage. **It does not synchronize with the attack
  packet**; it just holds useItem for a fixed time.
- **`AutoWeapon`** ([AutoWeapon.java](Raven-bPLUS/AutoWeapon.java)) — when an
  entity is under the crosshair and LMB is down, swaps to the highest-damage
  slot (calculated by `Utils.Player.getMaxDamageSlot()`); when the target
  disappears it reverts. It does not use a separate thread or
  `ServerboundSetCarriedItem` packets; it just changes `inventory.currentItem`
  on the client.
- **`BurstClicker`** ([BurstClicker.java](Raven-bPLUS/BurstClicker.java)) — an
  "artificial drag-click" simulator. On `onEnable`, it spins up a separate
  thread (`Raven.getExecutor().execute(...)`) that does `clicks * 2` iterations
  of LMB-down / LMB-up with a randomised delay (1–40 ms, with an additional
  ±25 ms jitter), then disables itself. This is used to hit high-CPS combos
  for "drag-down" weapons (paper swords). It even does right-clicks (place
  blocks) if the held item is an `ItemBlock`.
- **`ClickAssist` / `LeftClicker`** (not in the capture) — vanilla click rate
  helpers.

Distinctive features of Raven-bPLUS:

- **No rotation engine at all.** The whole combat stack works because the
  player *already* aims. The "anti-cheat bypass" is that the client never
  deviates from the player's view.
- **No `C02PacketUseEntity`** — attacks go through `KeyBinding.onTick(0)`.
  This works on 1.8.9 servers because the LMB key is processed into a swing
  + attack by the local player controller; the server trusts the client.
- **Per-feature module split** — each combat helper is a separate module
  with its own settings. Users enable only what they need, which makes the
  client much harder to fingerprint by "all combat modules on at once".
- **Threaded burst clicking** — `BurstClicker` uses `Raven.getExecutor()`
  so the click timing is decoupled from the render tick. This is why
  Raven's drag-click hits so reliably: it doesn't depend on the render loop
  staying under 16 ms.

---

## 3. Detailed differences

### 3.1 Targeting

| Capability | CurrentClient | LiquidBounce | Raven-bPLUS |
|---|---|---|---|
| Multi-target | yes (mode flag) | yes (`TargetTracker.targets()`) | no |
| Background target cache | yes (4-tick) | no (re-evaluates every tick) | no |
| Pre-selector module | yes (`KillAuraTargeter`) | n/a | n/a |
| FOV filter | yes | yes (in `TargetTracker`) | yes (limited, look-near) |
| Range filter | yes | yes (randomized!) | yes (crosshair distance) |
| Hitbox multiplier | yes (1.0–20.0) | n/a (uses MC hit-box) | no |
| Through-walls | yes (raycast) | yes (`aimThroughWalls`) | no |
| Anti-Bot / NPC filter | yes (Teams, Friends) | yes (`AntiBot`, friends, teams) | yes (AntiBot module) |
| Shield check | no | yes (`validateShield`) | no |
| Sleeping player filter | yes | yes (in `TargetTracker`) | n/a |
| Invisible filter | yes (configurable) | yes | yes (configurable) |
| Mob / Animal filter | yes (configurable) | yes | yes (configurable) |

### 3.2 Rotation

| Capability | CurrentClient | LiquidBounce | Raven-bPLUS |
|---|---|---|---|
| Rotation engine | `RotationUtils` | `RotationManager` + `Rotation` value object | **none** |
| FOV circle check | `isRotationFaced` | in `RotationsValueGroup` | n/a |
| Smooth / Snap / On-Tick | mostly server-snap (1.8) | yes (3 timings) | n/a |
| Rotation randomization | `RandomizationSettings` | yes (smoothing) | n/a |
| Rotation restore on tick end | yes | yes (server packet replay) | n/a |
| Server-side rotation packets | yes (via `SimulatedPlayer`) | yes (`Rotation` value object) | n/a |
| GCD fix / fixed-point math | no (1.8.9) | yes (modern) | n/a |
| Wall-block awareness | `RaycastUtils.raycastEntity` | `findEntityInCrosshair` + `isLookingAtEntity` | n/a |

### 3.3 Attack / Clicker

| Capability | CurrentClient | LiquidBounce | Raven-bPLUS |
|---|---|---|---|
| CPS-based timing | `CPSCounter` | `KillAuraClicker` | `BurstClicker` (per-onEnable) |
| Random delay | `randomClickDelay` | `randomClickDelay` (in `KillAuraClicker`) | yes (per-step ±25 ms) |
| Cooldown simulation | yes (`simulateCooldown`) | yes (1.8 mode) | n/a |
| Miss-time aware | no | yes (`mc.missTime` manipulation) | n/a |
| Multi-target per cycle | yes | yes (queue) | no |
| Queue-based execution | no (immediate) | yes (`prepareForAttack`) | no |
| Fake swing on miss | no | yes (`KillAuraFailSwing`) | n/a |

### 3.4 AutoBlock

| Capability | CurrentClient | LiquidBounce | Raven-bPLUS |
|---|---|---|---|
| Block mode count | 4 (Vanilla / Interact / AfterAttack / Packet) | 3 (Basic / Interact / Fake) | 1 (toggle useItem for N ms) |
| Sync with attack | yes | yes (`KillAuraAutoBlock.isPrioritizingBlocking`) | no (timer-based) |
| Blink integration | yes (`BlinkUtils`) | yes (`BlinkManager` + `BlinkPacketEvent`) | no |
| Assume-shield for 1.8 | n/a | yes (`assumeShield`) | n/a |
| Pause on unblock | yes | yes (`pauseOnUnblockTicks`) | no |
| Random reblock delay | yes | yes (`reblockTicksRange`) | no |
| 1.21.4 sword-block support | n/a | yes (`ItemUseAnimation.BLOCK`) | n/a |
| Offhand / swap hand | n/a (1.8) | yes (`UnblockMode.SWAP_HAND`) | n/a |
| 1.9-server-on-1.8-client | n/a | yes (`isOlderThanOrEqual1_8`) | n/a |

### 3.5 AutoWeapon

| Capability | CurrentClient | LiquidBounce | Raven-bPLUS |
|---|---|---|---|
| Hotbar swap | `SilentHotbar` (server-only) | `sendHeldItemChange` packet | `inventory.currentItem` |
| Revert on no target | yes | yes | yes (`goBackToPrevSlot`) |
| Only while LMB | yes | yes | yes (`onlyWhenHoldingDown`) |
| Damage calculation | `InventoryUtils` | `ModuleAutoWeapon.willShieldBreak` | `Utils.Player.getMaxDamageSlot` |
| Axe / shield breaker | n/a | yes | no |

### 3.6 Failure handling

| Capability | CurrentClient | LiquidBounce | Raven-bPLUS |
|---|---|---|---|
| Fake swing on miss | no | yes (`KillAuraFailSwing`) | no |
| Sound/box on fail | no | yes (`KillAuraNotifyWhenFail`) | no |
| AutoWeapon compensates for shield | n/a | yes (`willShieldBreak`) | no |

### 3.7 Mod-awareness

| Capability | CurrentClient | LiquidBounce | Raven-bPLUS |
|---|---|---|---|
| 1.21.4 sword block | n/a | yes | n/a |
| Hypixel BedWars protocol | n/a | yes (`isBlocksAttacksExisting`) | n/a |
| Server version detection | partial (1.8 only) | yes (`isOlderThanOrEqual1_8`, etc.) | n/a |
| Anti-bot filter | yes (Teams module) | yes (`ModuleAntiBot`) | yes (AntiBot module) |

---

## 4. How to improve the current KillAura

Below are the concrete changes the current client can adopt. They are ordered
roughly by **impact / effort** — start at the top.

### 4.1 Decompose `KillAura.kt` into sub-files (LiquidBounce style)

The current `KillAura.kt` is a single 1600-line `object`. This makes it hard
to:

- unit-test (e.g. the clicker logic is mixed with rotation logic),
- re-use parts in `KillAuraTargeter`,
- evolve settings without merge conflicts.

**Proposal:** mirror LiquidBounce's package layout:

```
src/main/java/net/asd/union/features/module/modules/combat/killaura/
    KillAura.kt                // entry point, onUpdate orchestration
    KillAuraTargetTracker.kt   // extracted from KillAura.onUpdate
    KillAuraClicker.kt         // extracted from CPSCounter usage
    KillAuraAutoBlock.kt       // extracted block-mode block
    KillAuraRange.kt           // randomized scan-range addition
    KillAuraFailSwing.kt       // fake-swing on missed raycast
    KillAuraRequirements.kt    // bed / inventory / health gates
    KillAuraRotations.kt       // extracted rotation config
    features/
        KillAuraRangeIndicator.kt
```

### 4.2 Add `KillAuraFailSwing` + `KillAuraNotifyWhenFail`

The current module silently does nothing on a miss. LiquidBounce's
`KillAuraFailSwing.dealWithFakeSwing` (see
[LiquidBounce/features/KillAuraFailSwing.kt](LiquidBounce/features/KillAuraFailSwing.kt))
swings anyway so CPS stays high, and `KillAuraNotifyWhenFail` plays a sound /
shows a box when the ray-cast misses. This is one of the simplest, highest-impact
upgrades you can do.

**Implementation sketch:**

```kotlin
internal object KillAuraFailSwing : ToggleableValueGroup(KillAura, "FailSwing", false) {
    val additionalRange by floatRange("AdditionalRange", 2.5f..3f, 0f..10f)
    val mode = modes(this, "NotifyWhenFail", activeIndex = 1) {
        arrayOf(NoneMode(it), Box, Sound)
    }

    fun dealWithFakeSwing(target: Entity?) {
        if (!enabled || !canAttackNow()) return
        val r = KillAura.range.interactionRange + currentAdditionalRange
        val e = target ?: world.findEnemy(0f, r) ?: return
        if (e.isDead || e.getDistanceToEntity(player) > r) return
        if (mc.objectMouseOver?.typeOfHit != MovingObjectPosition.MovingObjectType.MISS) return

        KillAuraAutoBlock.makeSeemBlock()
        KillAuraClicker.prepareForAttack {
            if (player.missTime > 0) player.missTime = 10
            player.swingItem()
            KillAuraNotifyWhenFail.notifyForFailedHit(e)
            true
        }
    }
}
```

### 4.3 Add a randomized `scanRangeIncrease`

The current client uses a fixed `range` slider. LiquidBounce randomizes
`scanRangeIncrease` every tick to defeat anti-cheats that look for
constant-radius hit registration. Easy fix:

```kotlin
private val scanRangeIncrease by floatRange("ScanRangeIncrease", 0.25f..0.5f, 0f..2f, "blocks").onChanged {
    currentScanRangeAddition = it.random()
}
private var currentScanRangeAddition: Float = scanRangeIncrease.random()
fun update() { currentScanRangeAddition = scanRangeIncrease.random() }
```

Call `update()` once per tick in `onUpdate`.

### 4.4 Add an `IgnoreShield` target filter

LiquidBounce's `KillAuraTargetTracker.validateShield` (see
[LiquidBounce/KillAuraTargetTracker.kt](LiquidBounce/KillAuraTargetTracker.kt))
skips targets whose shield is up (and that are not about to be broken).
On 1.8.9 you can read `entity.itemInUse` and item type, or just use the
attack cooldown. Combined with `ModuleAutoWeapon` (axe swap), this is a huge
hit-rate improvement on Hypixel.

```kotlin
private val ignoreShield by boolean("IgnoreShield", true)
override fun validate(entity: EntityLivingBase): Boolean {
    return super.validate(entity) && validateShield(entity)
}
private fun validateShield(e: EntityLivingBase): Boolean {
    if (ignoreShield || e !is EntityPlayer) return true
    if (player.heldItem?.item is ItemAxe) return true
    return e.itemInUse?.item !is ItemShield
}
```

### 4.5 Decouple the clicker from the render tick

Raven-bPLUS's `BurstClicker` (see
[Raven-bPLUS/BurstClicker.java](Raven-bPLUS/BurstClicker.java)) runs the
click sequence on a **separate executor thread** so the timing is independent
of the render frame rate. On 1.8.9, frame drops can desync `CPSCounter` and
miss slots, so a threaded scheduler will be more consistent.

You can do this by moving only the *delay / attack* into a worker queue; the
actual `sendPacket(C02PacketUseEntity)` and `swingItem()` calls must stay on
the main thread (use a `ConcurrentLinkedQueue` and drain it on `onUpdate`).

### 4.6 Add a "snap" / "on-tick" rotation timing

The current client mostly does server-side snap rotations. LiquidBounce
exposes 3 timings (Normal, Snap, OnTick) plus a `MaxAngleChange` (see
[LiquidBounce/KillAuraRotationsValueGroup.kt](LiquidBounce/KillAuraRotationsValueGroup.kt)).
This is one line of UI for a big legibility win for the user.

```kotlin
private val rotationTiming by choices("RotationTiming", arrayOf("Normal", "Snap", "OnTick"), "Normal")
```

Wire it in `onUpdate` so the rotation is recomputed per tick when
`rotationTiming == "OnTick"`, and held across ticks when `"Normal"`.

### 4.7 Add a `BlockMode.FAKE`

LiquidBounce's `KillAuraAutoBlock` (see
[LiquidBounce/features/KillAuraAutoBlock.kt](LiquidBounce/features/KillAuraAutoBlock.kt))
has a `BlockMode.FAKE` that toggles the visual block state but **never**
sends a `C08PlayerBlockPlacement` packet. This is useful for Hypixel BedWars
where the server checks that the player is *not* using an item during the
attack animation, but the client wants the shield/arm to render.

On 1.8.9 this is harder because the block-state is implicit in `itemInUse`,
but you can fake it for the renderer by overriding `ItemSword.useAction` /
blocking icon via a Mixin.

### 4.8 Expose a `MaxRotationAngleChange`

The current client has `RotationSettings.maxAngleDifference` for the
auto-block rotation; the actual attack rotation is unlimited. LiquidBounce
caps it via the `RotationsValueGroup`. Adding a cap prevents the snap
rotations from triggering anti-cheats that look for > N° change per packet.

### 4.9 Improve the `KillAuraTargeter` ↔ `KillAura` interface

`KillAuraTargeter` currently exposes its target via
`CombatManager.isFocusEntity(...)` (line 22 of
[KillAuraTargeter.kt](CurrentClient/KillAuraTargeter.kt)). It also has its own
search logic that duplicates `KillAura`. Refactor: have `KillAuraTargeter`
publish a `targetEntity: EntityLivingBase?` and let `KillAura` read it as a
*first-class* source (when `useTargeter` is on). Drop the duplicate search.

### 4.10 Add `ModuleAutoWeapon` and shield break

The current client has `SilentHotbar` for AutoWeapon (good), but no
"axe / shield break" logic. LiquidBounce's `ModuleAutoWeapon.willShieldBreak`
predicts that a critical axe hit will disable the target's shield, so the
next attack is allowed through. On 1.8.9 (no shields), this is moot, but the
`willShieldBreak` API is reusable for the 1.9+ build of the same client.

### 4.11 Expose event hooks

LiquidBounce's KillAura listens to `GameTickEvent`, `BlinkPacketEvent`,
`PacketEvent`, `WorldChangeEvent`, `AttackEntityEvent`. The current client
uses an in-house `EventState`-based `MotionEvent`. Pick one and stick with it;
mixing both means block/unblock timing can desync.

### 4.12 Config migration

LiquidBounce's `KillAuraRange.migrateFromValues` (see
[LiquidBounce/features/KillAuraRange.kt](LiquidBounce/features/KillAuraRange.kt))
reads the old `Range` / `WallRange` / `ScanExtraRange` JSON and converts it on
the fly. Adopt the same pattern: the moment you add `ScanRangeIncrease`, you
will get bug reports from users whose configs break.

---

## 5. What the current client does that the others don't

It is not all one-way. The current client has a few features neither
LiquidBounce nor Raven-bPLUS has (in their captured form):

- **`KillAuraTargeter`** as a standalone visual pre-selector module with its
  own FOV / hitbox-multiplier / `ThroughWalls` controls. This is genuinely
  useful for "toggle" users who want to see who KillAura would hit before
  they press the key.
- **`BACKGROUND_TARGET_CACHE_INTERVAL`** + `TARGET_CACHE_PADDING` to keep
  the same target across multiple ticks even if it briefly exits the
  FOV cone (good for Hypixel 1.8.9 where the server lags a few ms).
- **`raycastEntity` with `runWithModifiedRaycastResult`** — overrides the
  in-game ray-trace during the attack so server-side anti-cheat sees the
  same thing the client does. This is unique to FDPClient and worth keeping.
- **`BlinkUtils`** integration for slot-swap and useItem packets.

If you refactor, **keep these four** and port them into the new sub-files.

---

## 6. TL;DR

- **Raven-bPLUS** = legit, no rotation, click assist only. Borrow its
  per-module split (`AimAssist` / `AutoBlock` / `AutoWeapon` / `BurstClicker`),
  the threaded burst-clicker, and the "no extra reach" philosophy.
- **LiquidBounce** = modern, multi-feature, event-driven. Borrow its
  sub-file split, `TargetTracker`, `FailSwing`, randomized `scanRangeIncrease`,
  shield-aware target filter, blink-aware autoblock, and rotation-timing modes.
- **Current client** has good bones (rotation engine, targeter, blink
  autoblock, silent hotbar) but everything is inlined into one file with
  no event hooks. **Top three improvements:** (1) extract the clicker,
  target tracker, autoblock and range into separate `object`s, (2) add
  `FailSwing` + `NotifyWhenFail`, (3) add `IgnoreShield` target filter and
  `scanRangeIncrease` randomization.
