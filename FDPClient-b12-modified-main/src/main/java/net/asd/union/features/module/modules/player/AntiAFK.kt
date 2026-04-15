/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.features.module.modules.player

import net.asd.union.config.boolean
import net.asd.union.config.choices
import net.asd.union.config.float
import net.asd.union.config.intRange
import net.asd.union.event.MovementInputEvent
import net.asd.union.event.UpdateEvent
import net.asd.union.event.handler
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.extensions.fixedSensitivityYaw
import net.asd.union.utils.extensions.eyes
import net.asd.union.utils.extensions.reset
import net.asd.union.utils.extensions.sendUseItem
import net.asd.union.utils.extensions.setSprintSafely
import net.asd.union.utils.extensions.tryJump
import net.asd.union.utils.rotation.RotationSettings
import net.asd.union.utils.rotation.RotationUtils.setTargetRotation
import net.asd.union.utils.rotation.RotationUtils.toRotation
import net.asd.union.utils.kotlin.RandomUtils.nextBoolean
import net.asd.union.utils.kotlin.RandomUtils.nextInt
import net.asd.union.utils.movement.MovementUtils.updateControls
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.util.Vec3
import kotlin.math.sqrt

object AntiAFK : Module("AntiAFK", Category.PLAYER, gameDetecting = false, hideModule = false) {

    private val direction by choices("Direction", arrayOf("Left", "Right", "Swap"), "Swap")
    private val yawStep by float("YawStep", 6.5f, 0.5f..30f, "deg")
    private val jump by boolean("Jump", true)
    private val sprint by boolean("Sprint", true)

    private val centerPositionValue = boolean("Center Position", false)
    private val centerPosition by centerPositionValue
    private val blockRadius by float("Block Radius", 5f, 1f..20f, "blocks") { centerPositionValue.isActive() }

    private val returnRotationOptions = RotationSettings(this).withoutKeepRotation().apply {
        minHorizontalAngleChangeValue.set(6f)
        minVerticalAngleChangeValue.set(6f)
        maxHorizontalAngleChangeValue.set(12f)
        maxVerticalAngleChangeValue.set(12f)
    }

    private val switchItemsValue = boolean("SwitchItems", true)
    private val switchItems by switchItemsValue
    private val switchDelay by intRange("SwitchDelay", 3500..7000, 250..30000, "ms") { switchItemsValue.isActive() }

    private val pauseValue = boolean("Pause", true)
    private val pause by pauseValue
    private val pauseEvery by intRange("PauseEvery", 15000..30000, 1000..120000, "ms") { pauseValue.isActive() }
    private val pauseDuration by intRange("PauseDuration", 5000..10000, 1000..30000, "ms") { pauseValue.isActive() }

    private val clickMode by choices("ClickMode", arrayOf("Off", "Left", "Right", "Random"), "Random")
    private val clicksAfterPause by intRange("ClicksAfterPause", 1..2, 1..5) { clickMode != "Off" }
    private val clickDelay by intRange("ClickDelay", 200..450, 50..2000, "ms") { clickMode != "Off" }

    private var strafeRight = false
    private var isPaused = false
    private var pauseUntil = 0L
    private var nextPauseAt = 0L
    private var nextSwitchAt = 0L
    private var pendingClicks = 0
    private var nextClickAt = 0L
    private var centerPoint: Vec3? = null
    private var returningToCenter = false

    val onUpdate = handler<UpdateEvent> {
        val player = mc.thePlayer ?: return@handler

        if (mc.theWorld == null || player.isDead) {
            returningToCenter = false
            centerPoint = null
            resetInputs()
            return@handler
        }

        if (!centerPosition) {
            centerPoint = null
            returningToCenter = false
        } else if (centerPoint == null) {
            captureCenter(player)
        }

        if (centerPosition && centerPoint != null) {
            val distanceToCenter = distanceToCenter(player)

            if (returningToCenter || distanceToCenter > blockRadius) {
                returningToCenter = true

                if (distanceToCenter <= returnReleaseDistance()) {
                    returningToCenter = false
                } else {
                    applyReturnMovement(player)
                    return@handler
                }
            }
        }

        val now = System.currentTimeMillis()
        val screenOpen = mc.currentScreen != null
        updateSchedules(now)

        if (isPaused) {
            if (now >= pauseUntil) {
                finishPause(now)
            } else {
                applyStoppedState(player)
                return@handler
            }
        }

        if (pause && nextPauseAt > 0L && now >= nextPauseAt) {
            startPause(now)
            applyStoppedState(player)
            return@handler
        }

        // Keep movement active even when a GUI is open, but don't run item/click
        // automation from inside menus.
        if (!screenOpen && switchItems && nextSwitchAt > 0L && now >= nextSwitchAt) {
            switchHotbarSlot()
            nextSwitchAt = now + randomDelay(switchDelay)
        }

        if (!screenOpen && pendingClicks > 0 && now >= nextClickAt) {
            performClick()
            pendingClicks--
            nextClickAt = if (pendingClicks > 0) now + randomDelay(clickDelay) else 0L
        }

        applyCircleMovement(player)
    }

    val onMovementInput = handler<MovementInputEvent> { event ->
        val player = mc.thePlayer ?: return@handler

        if (mc.theWorld == null || player.isDead) {
            return@handler
        }

        if (returningToCenter) {
            event.originalInput.moveForward = 1f
            event.originalInput.moveStrafe = 0f
            event.originalInput.jump = false
            event.originalInput.sneak = false
            return@handler
        }

        if (isPaused) {
            event.originalInput.reset()
            return@handler
        }

        event.originalInput.moveForward = 1f
        event.originalInput.moveStrafe = if (strafeRight) 1f else -1f
        event.originalInput.jump = jump && player.onGround
        event.originalInput.sneak = false
    }

    override fun onEnable() {
        val now = System.currentTimeMillis()

        strafeRight = when (direction.lowercase()) {
            "right" -> true
            "left" -> false
            else -> nextBoolean()
        }

        isPaused = false
        pauseUntil = 0L
        pendingClicks = 0
        nextClickAt = 0L
        nextPauseAt = if (pause) now + randomDelay(pauseEvery) else 0L
        nextSwitchAt = if (switchItems) now + randomDelay(switchDelay) else 0L

        returningToCenter = false

        if (centerPosition) {
            mc.thePlayer?.let { captureCenter(it) }
        } else {
            centerPoint = null
        }
    }

    override fun onDisable() {
        isPaused = false
        pauseUntil = 0L
        pendingClicks = 0
        nextClickAt = 0L
        nextPauseAt = 0L
        nextSwitchAt = 0L
        returningToCenter = false
        centerPoint = null
        resetInputs()
    }

    private fun updateSchedules(now: Long) {
        if (!pause) {
            isPaused = false
            pauseUntil = 0L
            nextPauseAt = 0L
        } else if (!isPaused && nextPauseAt == 0L) {
            nextPauseAt = now + randomDelay(pauseEvery)
        }

        if (!switchItems) {
            nextSwitchAt = 0L
        } else if (nextSwitchAt == 0L) {
            nextSwitchAt = now + randomDelay(switchDelay)
        }

        if (clickMode.equals("Off", ignoreCase = true)) {
            pendingClicks = 0
            nextClickAt = 0L
        }
    }

    private fun startPause(now: Long) {
        isPaused = true
        pauseUntil = now + randomDelay(pauseDuration)
    }

    private fun finishPause(now: Long) {
        isPaused = false
        pauseUntil = 0L
        nextPauseAt = if (pause) now + randomDelay(pauseEvery) else 0L

        if (direction.equals("Swap", ignoreCase = true)) {
            strafeRight = !strafeRight
        }

        if (!clickMode.equals("Off", ignoreCase = true)) {
            pendingClicks = randomInt(clicksAfterPause)
            nextClickAt = if (pendingClicks > 0) now + randomDelay(clickDelay) else 0L
        }
    }

    private fun applyCircleMovement(player: net.minecraft.client.entity.EntityPlayerSP) {
        mc.gameSettings.keyBindForward.pressed = true
        mc.gameSettings.keyBindBack.pressed = false
        mc.gameSettings.keyBindLeft.pressed = !strafeRight
        mc.gameSettings.keyBindRight.pressed = strafeRight
        mc.gameSettings.keyBindJump.pressed = false
        mc.gameSettings.keyBindSprint.pressed = sprint

        player setSprintSafely sprint

        if (jump && player.onGround) {
            player.tryJump()
        }

        player.fixedSensitivityYaw += if (strafeRight) yawStep else -yawStep
    }

    private fun applyReturnMovement(player: EntityPlayerSP) {
        val center = centerPoint ?: return

        setTargetRotation(
            toRotation(Vec3(center.xCoord, player.eyes.yCoord, center.zCoord), false, player),
            options = returnRotationOptions,
        )

        mc.gameSettings.keyBindForward.pressed = true
        mc.gameSettings.keyBindBack.pressed = false
        mc.gameSettings.keyBindLeft.pressed = false
        mc.gameSettings.keyBindRight.pressed = false
        mc.gameSettings.keyBindJump.pressed = false
        mc.gameSettings.keyBindSprint.pressed = false

        player setSprintSafely false
    }

    private fun applyStoppedState(player: net.minecraft.client.entity.EntityPlayerSP) {
        mc.gameSettings.keyBindForward.pressed = false
        mc.gameSettings.keyBindBack.pressed = false
        mc.gameSettings.keyBindLeft.pressed = false
        mc.gameSettings.keyBindRight.pressed = false
        mc.gameSettings.keyBindJump.pressed = false
        mc.gameSettings.keyBindSprint.pressed = false

        player setSprintSafely false
    }

    private fun captureCenter(player: EntityPlayerSP) {
        centerPoint = Vec3(player.posX, player.posY, player.posZ)
    }

    private fun distanceToCenter(player: EntityPlayerSP): Double {
        val center = centerPoint ?: return 0.0
        val deltaX = player.posX - center.xCoord
        val deltaZ = player.posZ - center.zCoord

        return sqrt(deltaX * deltaX + deltaZ * deltaZ)
    }

    private fun returnReleaseDistance() = (blockRadius * 0.8f).coerceAtLeast(0.5f).toDouble()

    private fun switchHotbarSlot() {
        val player = mc.thePlayer ?: return
        val filledSlots = (0..8).filter { player.inventory.getStackInSlot(it) != null }
        val availableSlots = (filledSlots.ifEmpty { (0..8).toList() }).filter { it != player.inventory.currentItem }

        if (availableSlots.isEmpty()) {
            return
        }

        player.inventory.currentItem = availableSlots[nextInt(0, availableSlots.size)]
        mc.playerController?.syncCurrentPlayItem()
    }

    private fun performClick() {
        val player = mc.thePlayer ?: return

        when (resolveClickMode()) {
            "left" -> mc.clickMouse()
            "right" -> player.heldItem?.let(player::sendUseItem)
        }
    }

    private fun resolveClickMode(): String {
        return when (clickMode.lowercase()) {
            "random" -> if (nextBoolean()) "left" else "right"
            else -> clickMode.lowercase()
        }
    }

    private fun randomDelay(range: IntRange): Long = randomInt(range).toLong()

    private fun randomInt(range: IntRange): Int = nextInt(range.first, range.last + 1)

    private fun resetInputs() {
        updateControls()

        mc.thePlayer?.let {
            it setSprintSafely mc.gameSettings.keyBindSprint.isKeyDown
        }
    }
}
