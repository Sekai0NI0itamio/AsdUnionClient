/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.combat

import net.asd.union.config.*
import net.asd.union.event.*
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.ui.client.hud.element.elements.Text
import net.asd.union.ui.font.Fonts
import net.asd.union.utils.attack.CooldownHelper.getAttackCooldownProgress
import net.asd.union.utils.client.BlinkUtils
import net.asd.union.utils.client.ClientUtils.runTimeTicks
import net.asd.union.utils.extensions.getDistanceToEntityBox
import net.asd.union.utils.kotlin.RandomUtils.nextInt
import net.asd.union.utils.render.ColorSettingsInteger
import net.asd.union.utils.render.RenderUtils
import net.asd.union.utils.rotation.RandomizationSettings
import net.asd.union.utils.rotation.RotationSettings
import net.asd.union.utils.rotation.RotationUtils
import net.asd.union.utils.rotation.RotationUtils.BodyPoint
import net.asd.union.utils.timing.TimeUtils.randomClickDelay
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import org.lwjgl.input.Keyboard
import java.awt.Color

/**
 * KillAura — entry-point module. Owns the user-facing settings and
 * dispatches to the LiquidBounce-style sub-objects:
 *
 *  * [KillAuraTargetTracker] — target enumeration, hittable, prev-target list
 *  * [KillAuraRotations]     — rotation update logic
 *  * [KillAuraClicker]       — attack timing, miss-aware clicks, fail-swing render
 *  * [KillAuraAutoBlock]     — start / stop blocking, blink integration
 *  * [KillAuraRange]         — range math (per-entity reach)
 *  * [KillAuraRequirements]  — gating (shouldPrioritize, cancelRun, alive check)
 *
 * All settings still live on this object so the user's saved config stays
 * compatible with the new layout.
 */
object KillAura : Module("KillAura", Category.COMBAT, Keyboard.KEY_G, hideModule = false) {

    // region: OPTIONS ---------------------------------------------------------

    internal val simulateCooldown by boolean("SimulateCooldown", false)
    internal val simulateDoubleClicking by boolean("SimulateDoubleClicking", false) { !simulateCooldown }

    // CPS - Attack speed
    internal val maxCPSValue = object : IntegerValue("MaxCPS", 8, 0..50) {
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtLeast(minCPS)
        override fun onChanged(oldValue: Int, newValue: Int) {
            KillAuraClicker.attackDelay = randomClickDelay(minCPS, newValue)
        }
        override fun isSupported() = !simulateCooldown
    }
    internal val maxCPS by maxCPSValue

    internal val minCPS: Int by object : IntegerValue("MinCPS", 5, 0..50) {
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtMost(maxCPS)
        override fun onChanged(oldValue: Int, newValue: Int) {
            KillAuraClicker.attackDelay = randomClickDelay(newValue, maxCPS)
        }
        override fun isSupported() = !maxCPSValue.isMinimal() && !simulateCooldown
    }

    internal val hurtTime by int("HurtTime", 10, 0..10) { !simulateCooldown }

    internal val activationSlot by boolean("ActivationSlot", false)
    internal val preferredSlot by int("PreferredSlot", 1, 1..9) { activationSlot }

    internal val clickOnly by boolean("ClickOnly", false)

    // Range
    internal val range: Float by object : FloatValue("Range", 3.7f, 1f..8f) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            blockRange = blockRange.coerceAtMost(newValue)
        }
    }
    internal val scanRange by float("ScanRange", 2f, 0f..10f)
    internal val throughWallsRange by float("ThroughWallsRange", 3f, 0f..8f)
    internal val rangeSprintReduction by float("RangeSprintReduction", 0f, 0f..0.4f)

    // Modes
    internal val priority by choices(
        "Priority", arrayOf(
            "Health", "Distance", "Direction", "LivingTime", "Armor",
            "HurtResistance", "HurtTime", "HealthAbsorption", "RegenAmplifier",
            "OnLadder", "InLiquid", "InWeb"
        ), "Armor"
    )
    internal val targetMode by choices("TargetMode", arrayOf("Single", "Switch", "Multi"), "Switch")
    internal val limitedMultiTargets by int("LimitedMultiTargets", 0, 0..50) { targetMode == "Multi" }
    internal val maxSwitchFOV by float("MaxSwitchFOV", 90f, 30f..180f) { targetMode == "Switch" }

    // Delay
    internal val switchDelay by int("SwitchDelay", 15, 1..1000) { targetMode == "Switch" }

    // Bypass
    internal val swing by boolean("Swing", true)
    internal val keepSprint by boolean("KeepSprint", true)

    // Settings
    internal val autoF5 by boolean("AutoF5", false, subjective = true)
    internal val onSwording by boolean("OnSwording", true)
    internal val onScaffold by boolean("OnScaffold", false)
    internal val onDestroyBlock by boolean("OnDestroyBlock", false)
    internal val noScaffold by boolean("NoScaffold", false)
    internal val noFly by boolean("NoFly", false)
    internal val noEat by boolean("NoEat", false)
    internal val noBlocking by boolean("NoBlocking", false)
    internal val blinkCheck by boolean("BlinkCheck", false)

    // AutoBlock
    val autoBlock by choices("AutoBlock", arrayOf("Off", "Packet", "Fake"), "Packet")
    internal val blockMaxRange by float("BlockMaxRange", 3f, 0f..8f) { autoBlock == "Packet" }
    internal val unblockMode by choices(
        "UnblockMode", arrayOf("Stop", "Switch", "Empty"), "Stop"
    ) { autoBlock == "Packet" }
    internal val releaseAutoBlock by boolean("ReleaseAutoBlock", true) {
        autoBlock !in arrayOf("Off", "Fake")
    }
    val forceBlockRender by boolean("ForceBlockRender", true) {
        autoBlock !in arrayOf("Off", "Fake") && releaseAutoBlock
    }
    internal val ignoreTickRule by boolean("IgnoreTickRule", false) {
        autoBlock !in arrayOf("Off", "Fake") && releaseAutoBlock
    }
    internal val blockRate by int("BlockRate", 100, 1..100) {
        autoBlock !in arrayOf("Off", "Fake") && releaseAutoBlock
    }
    internal val uncpAutoBlock by boolean("UpdatedNCPAutoBlock", false) {
        autoBlock !in arrayOf("Off", "Fake") && !releaseAutoBlock
    }
    internal val switchStartBlock by boolean("SwitchStartBlock", false) {
        autoBlock !in arrayOf("Off", "Fake")
    }
    internal val interactAutoBlock by boolean("InteractAutoBlock", true) {
        autoBlock !in arrayOf("Off", "Fake")
    }
    val blinkAutoBlock by boolean("BlinkAutoBlock", false) {
        autoBlock !in arrayOf("Off", "Fake")
    }
    internal val blinkBlockTicks by int("BlinkBlockTicks", 3, 2..5) {
        autoBlock !in arrayOf("Off", "Fake") && blinkAutoBlock
    }

    // AutoBlock conditions
    internal val smartAutoBlock by boolean("SmartAutoBlock", false) { autoBlock == "Packet" }
    internal val forceBlock by boolean("ForceBlockWhenStill", true) { smartAutoBlock }
    internal val checkWeapon by boolean("CheckEnemyWeapon", true) { smartAutoBlock }
    internal var blockRange by object : FloatValue("BlockRange", range, 1f..8f) {
        override fun isSupported() = smartAutoBlock
        override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtMost(this@KillAura.range)
    }
    internal val maxOwnHurtTime by int("MaxOwnHurtTime", 3, 0..10) { smartAutoBlock }
    internal val maxDirectionDiff by float("MaxOpponentDirectionDiff", 60f, 30f..180f) { smartAutoBlock }
    internal val maxSwingProgress by int("MaxOpponentSwingProgress", 1, 0..5) { smartAutoBlock }

    // Rotations
    internal val options = RotationSettings(this).withoutKeepRotation()

    // Raycast
    internal val raycastValue = boolean("RayCast", true) { options.rotationsActive }
    internal val raycast by raycastValue
    internal val raycastIgnored by boolean(
        "RayCastIgnored", false
    ) { raycastValue.isActive() && options.rotationsActive }
    internal val livingRaycast by boolean("LivingRayCast", true) { raycastValue.isActive() && options.rotationsActive }

    // Hit delay
    internal val useHitDelay by boolean("UseHitDelay", false)
    internal val hitDelayTicks by int("HitDelayTicks", 1, 1..5) { useHitDelay }

    internal val generateSpotBasedOnDistance by boolean("GenerateSpotBasedOnDistance", false) { options.rotationsActive }

    internal val randomization = RandomizationSettings(this) { options.rotationsActive }
    internal val outborder by boolean("Outborder", false) { options.rotationsActive }

    internal val highestBodyPointToTargetValue: ListValue = object : ListValue(
        "HighestBodyPointToTarget", arrayOf("Head", "Body", "Feet"), "Head"
    ) {
        override fun isSupported() = options.rotationsActive

        override fun onChange(oldValue: String, newValue: String): String {
            val newPoint = BodyPoint.fromString(newValue)
            val lowestPoint = BodyPoint.fromString(lowestBodyPointToTarget)
            val coercedPoint = RotationUtils.coerceBodyPoint(newPoint, lowestPoint, BodyPoint.HEAD)
            return coercedPoint.name
        }
    }
    internal val highestBodyPointToTarget by highestBodyPointToTargetValue

    internal val lowestBodyPointToTargetValue: ListValue = object : ListValue(
        "LowestBodyPointToTarget", arrayOf("Head", "Body", "Feet"), "Feet"
    ) {
        override fun isSupported() = options.rotationsActive

        override fun onChange(oldValue: String, newValue: String): String {
            val newPoint = BodyPoint.fromString(newValue)
            val highestPoint = BodyPoint.fromString(highestBodyPointToTarget)
            val coercedPoint = RotationUtils.coerceBodyPoint(newPoint, BodyPoint.FEET, highestPoint)
            return coercedPoint.name
        }
    }
    internal val lowestBodyPointToTarget by lowestBodyPointToTargetValue

    internal val maxHorizontalBodySearch: FloatValue = object : FloatValue("MaxHorizontalBodySearch", 1f, 0f..1f) {
        override fun isSupported() = options.rotationsActive
        override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtLeast(minHorizontalBodySearch.get())
    }
    internal val minHorizontalBodySearch: FloatValue = object : FloatValue("MinHorizontalBodySearch", 0f, 0f..1f) {
        override fun isSupported() = options.rotationsActive
        override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtMost(maxHorizontalBodySearch.get())
    }

    internal val fov by float("FOV", 180f, 0f..180f)

    // Prediction
    internal val predictClientMovement by int("PredictClientMovement", 2, 0..5)
    internal val predictOnlyWhenOutOfRange by boolean(
        "PredictOnlyWhenOutOfRange", false
    ) { predictClientMovement != 0 }
    internal val predictEnemyPosition by float("PredictEnemyPosition", 1.5f, -1f..2f)

    // Extra swing
    internal val failSwing by boolean("FailSwing", true) { swing && options.rotationsActive }
    internal val respectMissCooldown by boolean(
        "RespectMissCooldown", false
    ) { swing && failSwing && options.rotationsActive }
    internal val swingOnlyInAir by boolean("SwingOnlyInAir", true) { swing && failSwing && options.rotationsActive }
    internal val maxRotationDifferenceToSwing by float(
        "MaxRotationDifferenceToSwing", 180f, 0f..180f
    ) { swing && failSwing && options.rotationsActive }
    internal val swingWhenTicksLate = object : BoolValue("SwingWhenTicksLate", false) {
        override fun isSupported() =
            swing && failSwing && maxRotationDifferenceToSwing != 180f && options.rotationsActive
    }
    internal val ticksLateToSwing by int(
        "TicksLateToSwing", 4, 0..20
    ) { swing && failSwing && swingWhenTicksLate.isActive() && options.rotationsActive }
    internal val renderBoxOnSwingFail by boolean("RenderBoxOnSwingFail", false) { failSwing }
    internal val renderBoxColor = ColorSettingsInteger(this, "RenderBoxColor") { renderBoxOnSwingFail }.with(0, 255, 255)
    internal val renderBoxFadeSeconds by float("RenderBoxFadeSeconds", 1f, 0f..5f) { renderBoxOnSwingFail }

    // Inventory
    internal val simulateClosingInventory by boolean("SimulateClosingInventory", false) { !noInventoryAttack }
    internal val noInventoryAttack by boolean("NoInvAttack", false)
    internal val noInventoryDelay by int("NoInvDelay", 200, 0..500) { noInventoryAttack }
    internal val noConsumeAttack by choices(
        "NoConsumeAttack", arrayOf("Off", "NoHits", "NoRotation"), "Off", subjective = true
    )

    internal val displayDebug by boolean("Debug", false)

    // endregion: OPTIONS ------------------------------------------------------

    // region: PUBLIC API (delegated to sub-objects) ---------------------------

    /**
     * Backwards-compatible `KillAura.target` accessor. External code
     * (`AuraBridge`, `FriendAdder`, etc.) reads this directly.
     */
    var target: EntityLivingBase?
        get() = KillAuraTargetTracker.target
        set(value) { KillAuraTargetTracker.target = value }

    /** Same as the original `KillAura.renderBlocking` public field. */
    var renderBlocking: Boolean
        get() = KillAuraAutoBlock.renderBlocking
        set(value) { KillAuraAutoBlock.renderBlocking = value }

    /** Same as the original `KillAura.blockStatus` public field. */
    var blockStatus: Boolean
        get() = KillAuraAutoBlock.blockStatus
        set(value) { KillAuraAutoBlock.blockStatus = value }

    /** Same as the original `cancelRun` inline getter. */
    val cancelRun
        get(): Boolean = KillAuraRequirements.cancelRun

    /**
     * True if KillAura is enabled, has a target, and would currently be
     * blocking. Used by other modules' chest-render / chest-esp logic.
     */
    val isBlockingChestAura
        get() = handleEvents() && target != null

    // endregion: PUBLIC API ----------------------------------------------------

    private val textElement = Text()

    // region: LIFECYCLE --------------------------------------------------------

    /**
     * Disable / enable handler. Wipes transient state on the sub-objects.
     */
    override fun onToggle(state: Boolean) {
        KillAuraDebug.evt("onToggle(state=$state)")

        KillAuraTargetTracker.reset()
        KillAuraClicker.reset()
        KillAuraAutoBlock.reset()
        KillAuraRequirements.reset()

        if (autoF5) mc.gameSettings.thirdPersonView = 0

        if (state) {
            KillAuraDebug.evt("onToggle → refreshing target candidates (forced)")
            KillAuraTargetTracker.refreshTargetCandidates(force = true)
            // Dump every setting so the user can see the active configuration
            // at the moment the module turned on.
            KillAuraDebug.dumpConfig()
        } else {
            KillAuraDebug.evt("onToggle → module disabled, transient state cleared")
        }
    }

    val onRotationUpdate = handler<RotationUpdateEvent> {
        update()
    }

    val onWorldChange = handler<WorldEvent> {
        KillAuraDebug.evt("onWorldChange → clearing transient state")

        KillAuraClicker.lastAttackTickData = null
        KillAuraTargetTracker.targetCandidates.clear()
        KillAuraTargetTracker.reset()
        if (blinkAutoBlock && BlinkUtils.isBlinking) {
            KillAuraDebug.blk("onWorldChange → releasing open blink")
            BlinkUtils.unblink()
        }
        KillAuraClicker.reset()
    }

    val onBackgroundTick = handler<GameTickEvent>(always = true, priority = 1) {
        KillAuraTargetTracker.refreshTargetCandidates()
    }

    /**
     * Main tick handler — dispatches to the sub-objects. The "butterfly"
     * click loop is preserved: extra clicks are scheduled, each click is
     * consumed in turn, and the clicker is short-circuited if blocking
     * transitions.
     */
    val onTick = handler<GameTickEvent>(priority = 2) {
        val player = mc.thePlayer ?: return@handler

        KillAuraDebug.evt("onTick(tick=$runTimeTicks) target=${KillAuraDebug.describeEntity(KillAuraTargetTracker.target)}")

        if (KillAuraRequirements.shouldPrioritize()) {
            KillAuraDebug.gate("onTick → shouldPrioritize=true, yielding (target=null, renderBlocking=false)")
            target = null
            renderBlocking = false
            return@handler
        }

        if (clickOnly && !mc.gameSettings.keyBindAttack.isKeyDown) {
            KillAuraDebug.gate("onTick → clickOnly=true but attack key not held, skipping")
            return@handler
        }

        if (blockStatus && autoBlock == "Packet" && releaseAutoBlock && !ignoreTickRule) {
            KillAuraDebug.blk("onTick → blockStatus=true, releasing block (clicks=0)")
            KillAuraClicker.clicks = 0
            KillAuraAutoBlock.stopBlocking()
            return@handler
        }

        if (cancelRun) {
            KillAuraDebug.gate("onTick → cancelRun=true, aborting (target=null, hittable=false, stopBlocking)")
            target = null
            KillAuraTargetTracker.hittable = false
            KillAuraAutoBlock.stopBlocking()
            return@handler
        }

        if (noInventoryAttack && (mc.currentScreen is GuiContainer ||
                System.currentTimeMillis() - KillAuraRequirements.containerOpen < noInventoryDelay)
        ) {
            KillAuraDebug.gate("onTick → noInventoryAttack gate active (screen=${mc.currentScreen?.javaClass?.simpleName})")
            target = null
            KillAuraTargetTracker.hittable = false
            if (mc.currentScreen is GuiContainer) KillAuraRequirements.containerOpen = System.currentTimeMillis()
            return@handler
        }

        if (simulateCooldown && getAttackCooldownProgress() < 1f) {
            KillAuraDebug.gate("onTick → simulateCooldown=1.0 not yet reached (cooldown=${"%.2f".format(getAttackCooldownProgress())}), waiting")
            return@handler
        }

        if (target == null && !KillAuraAutoBlock.blockStopInDead) {
            KillAuraDebug.gate("onTick → no target, blockStopInDead transition (stopBlocking)")
            KillAuraAutoBlock.blockStopInDead = true
            KillAuraAutoBlock.stopBlocking()
            return@handler
        }

        if (blinkAutoBlock) {
            KillAuraDebug.blk("onTick → running blinkAutoBlock tick (ticksExisted=${player.ticksExisted} blinkBlockTicks=$blinkBlockTicks)")
            KillAuraAutoBlock.tickBlinkAutoBlock()
        }

        if (target != null) {
            val tgt = target!!
            val dist = player.getDistanceToEntityBox(tgt)
            if (dist > blockMaxRange && blockStatus) {
                KillAuraDebug.blk("onTick → target dist=$dist > blockMaxRange=$blockMaxRange and blocking, force stop")
                KillAuraAutoBlock.stopBlocking(true)
                return@handler
            } else {
                if (autoBlock != "Off" && !releaseAutoBlock) {
                    KillAuraDebug.blk("onTick → renderBlocking=true (autoBlock=$autoBlock releaseAutoBlock=$releaseAutoBlock)")
                    renderBlocking = true
                }
            }

            // Butterfly click: simulate extra clicks in the same tick.
            val extraClicks = if (simulateDoubleClicking && !simulateCooldown) nextInt(-1, 1) else 0
            val maxClicks = KillAuraClicker.clicks + extraClicks
            KillAuraDebug.clk("onTick → click budget: clicks=${KillAuraClicker.clicks} extraClicks=$extraClicks maxClicks=$maxClicks")

            repeat(maxClicks) { i ->
                val wasBlocking = blockStatus
                KillAuraDebug.clk("onTick → runAttack #$i first=${i == 0} last=${i + 1 == maxClicks}")
                KillAuraClicker.runAttack(i == 0, i + 1 == maxClicks)
                KillAuraClicker.clicks--

                if (wasBlocking && !blockStatus &&
                    (releaseAutoBlock && !ignoreTickRule || autoBlock == "Off")
                ) {
                    KillAuraDebug.blk("onTick → block transition detected mid-tick, breaking out")
                    return@handler
                }
            }
        } else {
            KillAuraDebug.evt("onTick → target=null, renderBlocking=false")
            renderBlocking = false
        }
    }

    val onRender3D = handler<Render3DEvent> {
        KillAuraDebug.evt("onRender3D(tick=$runTimeTicks)")
        KillAuraClicker.handleFailedSwings()

        if (cancelRun) {
            KillAuraDebug.gate("onRender3D → cancelRun=true, aborting")
            target = null
            KillAuraTargetTracker.hittable = false
            return@handler
        }

        if (noInventoryAttack && (mc.currentScreen is GuiContainer ||
                System.currentTimeMillis() - KillAuraRequirements.containerOpen < noInventoryDelay)
        ) {
            KillAuraDebug.gate("onRender3D → noInventoryAttack gate active")
            target = null
            KillAuraTargetTracker.hittable = false
            if (mc.currentScreen is GuiContainer) KillAuraRequirements.containerOpen = System.currentTimeMillis()
            return@handler
        }

        val renderTarget = target
        if (renderTarget == null) {
            KillAuraDebug.evt("onRender3D → no target, skipping clicker tick")
            return@handler
        }
        KillAuraDebug.evt("onRender3D → onRenderTick for target ${KillAuraDebug.describeEntity(renderTarget)}")
        KillAuraClicker.onRenderTick()
    }

    val onRender2D = handler<Render2DEvent> {
        if (displayDebug) {
            val sr = ScaledResolution(mc)
            val blockingStatus = blockStatus
            val maxRangeValue = KillAuraRange.maxRange
            val reach = if (target != null) {
                mc.thePlayer.getDistanceToEntityBox(target!!)
            } else 0.0
            val formattedReach = String.format("%.2f", reach)

            val rangeString = "Range: $maxRangeValue"
            val reachString = "Reach: $formattedReach"
            val cpsString = textElement.getReplacement("cps")
            val status =
                "Blocking: ${if (blockingStatus) "Yes" else "No"}, CPS: $cpsString, $reachString, $rangeString"

            Fonts.minecraftFont.drawStringWithShadow(
                status,
                sr.scaledWidth / 2f - Fonts.minecraftFont.getStringWidth(status) / 2f,
                sr.scaledHeight / 2f - 60f,
                Color.orange.rgb
            )
        }
    }

    val onPacket = handler<PacketEvent> { event ->
        val player = mc.thePlayer ?: return@handler
        val packet = event.packet

        // Log every server-bound packet that KillAura might care about so
        // the user can see exactly what is being sent to the server while
        // the aura is running.
        if (KillAuraDebug.enabled && event.isCancelled.not() &&
            (packet is C02PacketUseEntity ||
                packet is C07PacketPlayerDigging ||
                packet is C08PacketPlayerBlockPlacement)
        ) {
            KillAuraDebug.tracePacket(packet, "onPacket")
        }

        if (autoBlock == "Off" || !blinkAutoBlock || !KillAuraAutoBlock.blinked) return@handler

        if (player.isDead || player.ticksExisted < 20) {
            KillAuraDebug.blk("onPacket → releasing open blink (dead=${player.isDead} ticksExisted=${player.ticksExisted})")
            BlinkUtils.unblink()
            return@handler
        }

        KillAuraDebug.blk("onPacket → queuing packet for blink: ${packet.javaClass.simpleName}")
        BlinkUtils.blink(packet, event)
    }

    /**
     * Per-tick "update" (also fired on `RotationUpdateEvent`). Picks a new
     * target and toggles F5.
     */
    fun update() {
        KillAuraDebug.evt("update() enter cancelRun=$cancelRun noInvAttack=$noInventoryAttack screen=${mc.currentScreen?.javaClass?.simpleName}")

        if (cancelRun ||
            (noInventoryAttack && (mc.currentScreen is GuiContainer ||
                System.currentTimeMillis() - KillAuraRequirements.containerOpen < noInventoryDelay))
        ) {
            KillAuraDebug.evt("update() → gate active, no target update")
            return
        }

        val targetBefore = KillAuraTargetTracker.target
        KillAuraTargetTracker.update()
        val targetAfter = KillAuraTargetTracker.target

        if (targetBefore != targetAfter) {
            KillAuraDebug.track("update() → target changed: ${KillAuraDebug.describeEntity(targetBefore)} → ${KillAuraDebug.describeEntity(targetAfter)}")
        }

        if (autoF5 && target != null && mc.gameSettings.thirdPersonView != 1) {
            KillAuraDebug.evt("update() → autoF5: thirdPersonView 0 → 1")
            mc.gameSettings.thirdPersonView = 1
        }
    }

    override val tag
        get() = targetMode
}
