/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.utils.client.ClientUtils.LOGGER
import net.asd.union.utils.client.ClientUtils.displayChatMessage
import net.asd.union.utils.extensions.getDistanceToEntityBox
import net.minecraft.client.Minecraft
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.Packet
import net.minecraft.util.Vec3

/**
 * Comprehensive debug logger for [KillAura] and its sub-objects.
 *
 * All KillAura logic dispatches to this logger, so flipping [enabled] to
 * `false` silences every debug line. The default state is **on** so the
 * user can immediately see what the aura is doing. Set the field to
 * `false` to switch off.
 *
 * Each sub-object has its own [Category] so log lines can be filtered:
 *   `[K-AURA|CFG]`      - configuration dump, per-tick
 *   `[K-AURA|RNG]`      - range calculations
 *   `[K-AURA|GATE]`     - cancel / prioritize gating
 *   `[K-AURA|TRACK]`    - target selection
 *   `[K-AURA|HIT]`      - hittable ray-cast
 *   `[K-AURA|ROT]`      - rotation update
 *   `[K-AURA|CLK]`      - click scheduling
 *   `[K-AURA|ATK]`      - attack pipeline
 *   `[K-AURA|BLK]`      - auto-block
 *   `[K-AURA|PKT]`      - server-bound packet trace
 *   `[K-AURA|EVT]`      - event hooks
 *   `[K-AURA|MISS]`     - fail-swing / miss
 */
internal object KillAuraDebug {

    /**
     * Master switch. Set to `false` to silence every KillAura debug log.
     *
     * The user has requested this be **on** while we are still investigating
     * issues, so the default is `true`. Flip to `false` once a session is
     * stable to cut the log spam.
     */
    var enabled: Boolean = true

    /** If true, also echo the most important log lines to the in-game chat. */
    var chatEcho: Boolean = false

    /** Log categories — used as a tag prefix on every line. */
    enum class Category(val tag: String) {
        CFG("CFG"),
        RNG("RNG"),
        GATE("GATE"),
        TRACK("TRACK"),
        HIT("HIT"),
        ROT("ROT"),
        CLK("CLK"),
        ATK("ATK"),
        BLK("BLK"),
        PKT("PKT"),
        EVT("EVT"),
        MISS("MISS"),
    }

    // region: core -----------------------------------------------------------

    /**
     * Emit a log line. Goes to:
     *  - the Log4j logger (always, when [enabled])
     *  - in-game chat (when [enabled] and [chatEcho])
     *
     * @param category log category (used as prefix).
     * @param message  already-formatted message.
     */
    fun log(category: Category, message: String) {
        if (!enabled) return
        val line = "[K-AURA|${category.tag}] $message"
        LOGGER.info(line)
        if (chatEcho) displayChatMessage("§8$line")
    }

    /**
     * Convenience wrapper — only logs if [enabled] is true.
     */
    fun cat(category: Category, msg: () -> String) {
        if (!enabled) return
        val line = "[K-AURA|${category.tag}] ${msg()}"
        LOGGER.info(line)
        if (chatEcho) displayChatMessage("§8$line")
    }

    // endregion: core --------------------------------------------------------

    // region: formatters -----------------------------------------------------

    /** Short id for an entity, e.g. `Player 'Notch'#42 (h=20.0/20.0)`. */
    fun describeEntity(entity: Entity?): String {
        if (entity == null) return "<null>"
        val name = when (entity) {
            is EntityPlayer -> entity.name
            else -> entity.javaClass.simpleName
        }
        return if (entity is EntityLivingBase) {
            "${entity.javaClass.simpleName} '$name'#${entity.entityId} h=${format(entity.health)}/${format(entity.maxHealth)} " +
                "abs=${format(entity.absorptionAmount)} hurtTime=${entity.hurtTime} hurtResist=${entity.hurtResistantTime}"
        } else {
            "${entity.javaClass.simpleName} '$name'#${entity.entityId}"
        }
    }

    /** Pretty Vec3 (rounded to 2 decimal places). */
    fun describeVec(vec: Vec3?): String {
        if (vec == null) return "<null>"
        return "(${format(vec.xCoord)}, ${format(vec.yCoord)}, ${format(vec.zCoord)})"
    }

    /** Format a double to 2 decimal places. */
    fun format(value: Double): String = "%.2f".format(value)

    /** Format a float to 2 decimal places. */
    fun format(value: Float): String = "%.2f".format(value)

    /** Format an integer (no rounding). */
    fun format(value: Int): String = value.toString()

    /** Distance from local player to entity, in blocks. */
    fun distanceTo(entity: Entity): String {
        val player = Minecraft.getMinecraft().thePlayer ?: return "?"
        return format(player.getDistanceToEntityBox(entity))
    }

    /** Boolean as a 1-char `Y/N` for tighter columns. */
    fun yn(value: Boolean): String = if (value) "Y" else "N"

    // endregion: formatters --------------------------------------------------

    // region: per-category shortcuts -----------------------------------------

    fun cfg(message: String) = log(Category.CFG, message)
    fun rng(message: String) = log(Category.RNG, message)
    fun gate(message: String) = log(Category.GATE, message)
    fun track(message: String) = log(Category.TRACK, message)
    fun hit(message: String) = log(Category.HIT, message)
    fun rot(message: String) = log(Category.ROT, message)
    fun clk(message: String) = log(Category.CLK, message)
    fun atk(message: String) = log(Category.ATK, message)
    fun blk(message: String) = log(Category.BLK, message)
    fun pkt(message: String) = log(Category.PKT, message)
    fun evt(message: String) = log(Category.EVT, message)
    fun miss(message: String) = log(Category.MISS, message)

    // endregion: per-category shortcuts --------------------------------------

    // region: packet wrappers -----------------------------------------------

    /**
     * Wrap a [Packet] so we can trace what KillAura (and its sub-objects)
     * is sending to the server. The wrapper delegates to the original
     * packet unchanged — only the [toString] is enriched for the log.
     */
    fun tracePacket(packet: Packet<*>?, label: String) {
        if (!enabled || packet == null) return
        pkt("$label sent → ${packet::class.java.simpleName} ${packet.javaClass.simpleName} :: $packet")
    }

    // endregion: packet wrappers ---------------------------------------------

    // region: dump helpers ---------------------------------------------------

    /**
     * Print every relevant KillAura setting as a single multi-line dump.
     * Called from `onToggle(true)` so the user gets a snapshot of their
     * current configuration when they turn the module on.
     */
    fun dumpConfig() {
        if (!enabled) return
        val k = KillAura
        val lines = listOf(
            "──── KillAura configuration dump ────",
            "targetMode = ${k.targetMode}  priority = ${k.priority}  fov = ${format(k.fov)}°",
            "range = ${format(k.range)}  throughWallsRange = ${format(k.throughWallsRange)}  " +
                "scanRange = ${format(k.scanRange)}  rangeSprintReduction = ${format(k.rangeSprintReduction)}",
            "minCPS = ${k.minCPS}  maxCPS = ${k.maxCPS}  simulateCooldown = ${yn(k.simulateCooldown)}  " +
                "simulateDoubleClicking = ${yn(k.simulateDoubleClicking)}  hurtTime ≤ ${k.hurtTime}",
            "options.rotationsActive = ${yn(k.options.rotationsActive)}  raycast = ${yn(k.raycast)}  " +
                "raycastIgnored = ${yn(k.raycastIgnored)}  livingRaycast = ${yn(k.livingRaycast)}",
            "predictClientMovement = ${k.predictClientMovement}  " +
                "predictOnlyWhenOutOfRange = ${yn(k.predictOnlyWhenOutOfRange)}  " +
                "predictEnemyPosition = ${format(k.predictEnemyPosition)}",
            "generateSpotBasedOnDistance = ${yn(k.generateSpotBasedOnDistance)}  outborder = ${yn(k.outborder)}",
            "highestBodyPoint = ${k.highestBodyPointToTarget}  lowestBodyPoint = ${k.lowestBodyPointToTarget}",
            "useHitDelay = ${yn(k.useHitDelay)}  hitDelayTicks = ${k.hitDelayTicks}",
            "swing = ${yn(k.swing)}  failSwing = ${yn(k.failSwing)}  " +
                "respectMissCooldown = ${yn(k.respectMissCooldown)}  " +
                "swingOnlyInAir = ${yn(k.swingOnlyInAir)}  " +
                "maxRotationDifferenceToSwing = ${format(k.maxRotationDifferenceToSwing)}°",
            "swingWhenTicksLate = ${yn(k.swingWhenTicksLate.isActive())}  " +
                "ticksLateToSwing = ${k.ticksLateToSwing}  " +
                "renderBoxOnSwingFail = ${yn(k.renderBoxOnSwingFail)}  " +
                "renderBoxFadeSeconds = ${format(k.renderBoxFadeSeconds)}",
            "autoBlock = ${k.autoBlock}  blockMaxRange = ${format(k.blockMaxRange)}  " +
                "unblockMode = ${k.unblockMode}  releaseAutoBlock = ${yn(k.releaseAutoBlock)}",
            "forceBlockRender = ${yn(k.forceBlockRender)}  ignoreTickRule = ${yn(k.ignoreTickRule)}  " +
                "blockRate = ${k.blockRate}%  uncpAutoBlock = ${yn(k.uncpAutoBlock)}  " +
                "switchStartBlock = ${yn(k.switchStartBlock)}  interactAutoBlock = ${yn(k.interactAutoBlock)}",
            "blinkAutoBlock = ${yn(k.blinkAutoBlock)}  blinkBlockTicks = ${k.blinkBlockTicks}",
            "smartAutoBlock = ${yn(k.smartAutoBlock)}  forceBlock = ${yn(k.forceBlock)}  " +
                "checkWeapon = ${yn(k.checkWeapon)}  blockRange = ${format(k.blockRange)}",
            "maxOwnHurtTime = ${k.maxOwnHurtTime}  maxDirectionDiff = ${format(k.maxDirectionDiff)}°  " +
                "maxSwingProgress = ${k.maxSwingProgress}",
            "simulateClosingInventory = ${yn(k.simulateClosingInventory)}  " +
                "noInventoryAttack = ${yn(k.noInventoryAttack)}  noConsumeAttack = ${k.noConsumeAttack}",
            "displayDebug = ${yn(k.displayDebug)}  onSwording = ${yn(k.onSwording)}  " +
                "onScaffold = ${yn(k.onScaffold)}  onDestroyBlock = ${yn(k.onDestroyBlock)}  " +
                "noScaffold = ${yn(k.noScaffold)}  noFly = ${yn(k.noFly)}  noEat = ${yn(k.noEat)}  noBlocking = ${yn(k.noBlocking)}",
            "activationSlot = ${yn(k.activationSlot)}  preferredSlot = ${k.preferredSlot}  " +
                "autoF5 = ${yn(k.autoF5)}  clickOnly = ${yn(k.clickOnly)}",
            "keepSprint = ${yn(k.keepSprint)}  blinkCheck = ${yn(k.blinkCheck)}",
            "targetCandidates.size = ${KillAuraTargetTracker.targetCandidates.size}",
            "─── KillAuraDebug.enabled = $enabled  chatEcho = $chatEcho ───",
        )
        lines.forEach { cfg(it) }
    }

    // endregion: dump helpers ------------------------------------------------
}
