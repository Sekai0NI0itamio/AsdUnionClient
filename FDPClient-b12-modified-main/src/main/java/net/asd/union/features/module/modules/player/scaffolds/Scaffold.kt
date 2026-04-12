package net.asd.union.features.module.modules.player.scaffolds

import net.asd.union.config.*
import net.asd.union.event.*
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.attack.CPSCounter
import net.asd.union.utils.block.*
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.asd.union.utils.extensions.*
import net.asd.union.utils.inventory.InventoryUtils
import net.asd.union.utils.inventory.InventoryUtils.blocksAmount
import net.asd.union.utils.inventory.SilentHotbar
import net.asd.union.utils.inventory.hotBarSlot
import net.asd.union.utils.kotlin.RandomUtils
import net.asd.union.utils.movement.MovementUtils
import net.asd.union.utils.render.RenderUtils
import net.asd.union.utils.rotation.PlaceRotation
import net.asd.union.utils.rotation.Rotation
import net.asd.union.utils.rotation.RotationSettingsWithRotationModes
import net.asd.union.utils.rotation.RotationUtils
import net.asd.union.utils.rotation.RotationUtils.canUpdateRotation
import net.asd.union.utils.rotation.RotationUtils.getFixedAngleDelta
import net.asd.union.utils.rotation.RotationUtils.getVectorForRotation
import net.asd.union.utils.rotation.RotationUtils.rotationDifference
import net.asd.union.utils.rotation.RotationUtils.setTargetRotation
import net.asd.union.utils.rotation.RotationUtils.toRotation
import net.asd.union.utils.simulation.SimulatedPlayer
import net.asd.union.utils.timing.DelayTimer
import net.asd.union.utils.timing.MSTimer
import net.asd.union.utils.timing.TickDelayTimer
import net.asd.union.utils.timing.TimeUtils
import net.asd.union.utils.timing.WaitTickUtils
import net.minecraft.block.BlockBush
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.settings.GameSettings
import net.minecraft.init.Blocks.air
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.util.*
import net.minecraft.world.WorldSettings
import net.minecraftforge.event.ForgeEventFactory
import org.lwjgl.input.Keyboard
import java.awt.Color
import kotlin.math.*

object Scaffold : Module("Scaffold", Category.PLAYER, Keyboard.KEY_V, hideModule = false) {

    private val towerMode by Tower.towerModeValues
    private val stopWhenBlockAbove by Tower.stopWhenBlockAboveValues
    private val onJump by Tower.onJumpValues
    private val notOnMove by Tower.notOnMoveValues
    private val jumpMotion by Tower.jumpMotionValues
    private val jumpDelay by Tower.jumpDelayValues
    private val constantMotion by Tower.constantMotionValues
    private val constantMotionJumpGround by Tower.constantMotionJumpGroundValues
    private val constantMotionJumpPacket by Tower.constantMotionJumpPacketValues
    private val triggerMotion by Tower.triggerMotionValues
    private val dragMotion by Tower.dragMotionValues
    private val teleportHeight by Tower.teleportHeightValues
    private val teleportDelay by Tower.teleportDelayValues
    private val teleportGround by Tower.teleportGroundValues
    private val teleportNoMotion by Tower.teleportNoMotionValues

    val scaffoldMode by choices(
        "ScaffoldMode",
        arrayOf("JumpBridge", "Normal", "Rewinside", "Expand", "Telly", "GodBridge"),
        "GodBridge"
    )

    private val isJumpBridgeEnabled: Boolean
        get() = scaffoldMode == "JumpBridge"

    // JumpBridge (standalone mode)
    private val jumpBridgeCps by int("CPS", 20, 1..50) { isJumpBridgeEnabled }
    private val jumpBridgeJump by boolean("Jump", true) { isJumpBridgeEnabled }
    private val jumpBridgeWalk by boolean("Walk", true) { isJumpBridgeEnabled }

    private val jumpBridgePlaceTimer = MSTimer()
    private var lastJumpBridgeFacing: EnumFacing? = null

    // Expand
    private val omniDirectionalExpand by boolean("OmniDirectionalExpand", false) { scaffoldMode == "Expand" }
    private val expandLength by int("ExpandLength", 1, 1..6) { scaffoldMode == "Expand" }

    // Place delay
    private val placeDelayValue = boolean("PlaceDelay", true) { scaffoldMode != "GodBridge" && !isJumpBridgeEnabled }
    private val maxDelayValue: IntegerValue = object : IntegerValue("MaxDelay", 0, 0..1000) {
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtLeast(minDelay)
        override fun isSupported() = placeDelayValue.isActive()
    }
    private val maxDelay by maxDelayValue

    private val minDelayValue: IntegerValue = object : IntegerValue("MinDelay", 0, 0..1000) {
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtMost(maxDelay)
        override fun isSupported() = placeDelayValue.isActive() && !maxDelayValue.isMinimal()
    }
    private val minDelay by minDelayValue

    // Extra clicks
    private val extraClicks by boolean("DoExtraClicks", true) { !isJumpBridgeEnabled }
    private val simulateDoubleClicking by boolean("SimulateDoubleClicking", true) { extraClicks }

    private val extraClickMaxCPSValue: IntegerValue = object : IntegerValue("ExtraClickMaxCPS", 27, 0..50) {
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtLeast(extraClickMinCPS)
        override fun isSupported() = extraClicks
    }
    private val extraClickMaxCPS by extraClickMaxCPSValue

    private val extraClickMinCPSValue: IntegerValue = object : IntegerValue("ExtraClickMinCPS", 19, 0..50) {
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtMost(extraClickMaxCPS)
        override fun isSupported() = extraClicks && !extraClickMaxCPSValue.isMinimal()
    }
    private val extraClickMinCPS by extraClickMinCPSValue

    private val placementAttempt by choices("PlacementAttempt", arrayOf("Fail", "Independent"), "Independent") { extraClicks }

    // Autoblock
    private val autoBlock by choices("AutoBlock", arrayOf("Off", "Pick", "Spoof", "Switch"), "Spoof") { !isJumpBridgeEnabled }
    private val sortByHighestAmount by boolean("SortByHighestAmount", false) { autoBlock != "Off" && !isJumpBridgeEnabled }
    private val earlySwitch by boolean("EarlySwitch", true) { autoBlock != "Off" && !sortByHighestAmount && !isJumpBridgeEnabled }
    private val amountBeforeSwitch by int("SlotAmountBeforeSwitch", 3, 1..10) { earlySwitch && !sortByHighestAmount }

    // Misc
    private val autoF5 by boolean("AutoF5", false, subjective = true) { !isJumpBridgeEnabled }

    // Movement
    val sprint by boolean("Sprint", true) { !isJumpBridgeEnabled }
    val sprintMode by choices("SprintMode", arrayOf("Normal", "OnlyOnGround", "OnlyInAir"), "Normal") { sprint }

    private val swing by boolean("Swing", true, subjective = true) { !isJumpBridgeEnabled }
    private val down by boolean("Down", true) { !sameY && scaffoldMode !in arrayOf("GodBridge", "Telly") && !isJumpBridgeEnabled }

    private val ticksUntilRotationValue: IntegerValue = object : IntegerValue("TicksUntilRotation", 3, 1..5) {
        override fun isSupported() = scaffoldMode == "Telly"
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceIn(range)
    }
    private val ticksUntilRotation by ticksUntilRotationValue

    // GodBridge
    private val waitForRots by boolean("WaitForRotations", true) { isGodBridgeEnabled }
    private val useOptimizedPitch by boolean("UseOptimizedPitch", true) { isGodBridgeEnabled }
    private val customGodPitch by float("GodBridgePitch", 73.5f, 0f..90f) { isGodBridgeEnabled && !useOptimizedPitch }

    val jumpAutomatically by boolean("JumpAutomatically", true) { scaffoldMode == "GodBridge" }

    private val maxBlocksToJumpValue: IntegerValue = object : IntegerValue("MaxBlocksToJump", 4, 1..8) {
        override fun isSupported() = scaffoldMode == "GodBridge" && !jumpAutomatically
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtLeast(minBlocksToJumpValue.get())
    }
    private val maxBlocksToJump by maxBlocksToJumpValue

    private val minBlocksToJumpValue: IntegerValue = object : IntegerValue("MinBlocksToJump", 4, 1..8) {
        override fun isSupported() = scaffoldMode == "GodBridge" && !jumpAutomatically && !maxBlocksToJumpValue.isMinimal()
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtMost(maxBlocksToJumpValue.get())
    }
    private val minBlocksToJump by minBlocksToJumpValue

    // Telly
    private val startHorizontally by boolean("StartHorizontally", true) { scaffoldMode == "Telly" }
    private val maxHorizontalPlacementsValue: IntegerValue = object : IntegerValue("MaxHorizontalPlacements", 1, 1..10) {
        override fun isSupported() = scaffoldMode == "Telly"
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtLeast(minHorizontalPlacementsValue.get())
    }
    private val maxHorizontalPlacements by maxHorizontalPlacementsValue

    private val minHorizontalPlacementsValue: IntegerValue = object : IntegerValue("MinHorizontalPlacements", 1, 1..10) {
        override fun isSupported() = scaffoldMode == "Telly"
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtMost(maxHorizontalPlacementsValue.get())
    }
    private val minHorizontalPlacements by minHorizontalPlacementsValue

    private val maxVerticalPlacementsValue: IntegerValue = object : IntegerValue("MaxVerticalPlacements", 1, 1..10) {
        override fun isSupported() = scaffoldMode == "Telly"
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtLeast(minVerticalPlacementsValue.get())
    }
    private val maxVerticalPlacements by maxVerticalPlacementsValue

    private val minVerticalPlacementsValue: IntegerValue = object : IntegerValue("MinVerticalPlacements", 1, 1..10) {
        override fun isSupported() = scaffoldMode == "Telly"
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtMost(maxVerticalPlacementsValue.get())
    }
    private val minVerticalPlacements by minVerticalPlacementsValue

    private val maxJumpTicksValue: IntegerValue = object : IntegerValue("MaxJumpTicks", 0, 0..10) {
        override fun isSupported() = scaffoldMode == "Telly"
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtLeast(minJumpTicksValue.get())
    }
    private val maxJumpTicks by maxJumpTicksValue

    private val minJumpTicksValue: IntegerValue = object : IntegerValue("MinJumpTicks", 0, 0..10) {
        override fun isSupported() = scaffoldMode == "Telly"
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtMost(maxJumpTicksValue.get())
    }
    private val minJumpTicks by minJumpTicksValue

    // Clutching / safety
    private val allowClutching by boolean("AllowClutching", false) { scaffoldMode !in arrayOf("Telly", "Expand") && !isJumpBridgeEnabled }
    private val horizontalClutchBlocksValue: IntegerValue = object : IntegerValue("HorizontalClutchBlocks", 3, 1..5) {
        override fun isSupported() = allowClutching && scaffoldMode !in arrayOf("Telly", "Expand")
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceIn(range)
    }
    private val horizontalClutchBlocks by horizontalClutchBlocksValue

    private val verticalClutchBlocksValue: IntegerValue = object : IntegerValue("VerticalClutchBlocks", 2, 1..3) {
        override fun isSupported() = allowClutching && scaffoldMode !in arrayOf("Telly", "Expand")
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceIn(range)
    }
    private val verticalClutchBlocks by verticalClutchBlocksValue

    private val blockSafe by boolean("BlockSafe", false) { !isGodBridgeEnabled && !isJumpBridgeEnabled }

    // Eagle
    private val eagleValue =
        ListValue("Eagle", arrayOf("Normal", "Silent", "Off"), "Normal", false) { scaffoldMode != "GodBridge" && !isJumpBridgeEnabled }
    val eagle by eagleValue

    private val eagleMode by choices("EagleMode", arrayOf("Both", "OnGround", "InAir"), "Both") {
        eagle != "Off" && scaffoldMode != "GodBridge" && !isJumpBridgeEnabled
    }
    private val adjustedSneakSpeed by boolean("AdjustedSneakSpeed", true) { eagle == "Silent" && scaffoldMode != "GodBridge" && !isJumpBridgeEnabled }
    private val eagleSpeed by float("EagleSpeed", 0.3f, 0.3f..1.0f) { eagle != "Off" && scaffoldMode != "GodBridge" && !isJumpBridgeEnabled }
    val eagleSprint by boolean("EagleSprint", false) { eagle == "Normal" && scaffoldMode != "GodBridge" && !isJumpBridgeEnabled }
    private val blocksToEagle by int("BlocksToEagle", 0, 0..10) { eagle != "Off" && scaffoldMode != "GodBridge" && !isJumpBridgeEnabled }
    private val edgeDistance by float("EagleEdgeDistance", 0f, 0f..0.5f) { eagle != "Off" && scaffoldMode != "GodBridge" && !isJumpBridgeEnabled }
    private val useMaxSneakTime by boolean("UseMaxSneakTime", true) { eagle != "Off" && scaffoldMode != "GodBridge" && !isJumpBridgeEnabled }
    private val maxSneakTicks by int("MaxSneakTicks", 3, 0..10) { useMaxSneakTime }
    private val blockSneakingAgainUntilOnGround by boolean("BlockSneakingAgainUntilOnGround", true) {
        useMaxSneakTime && eagleMode != "OnGround"
    }

    // Rotation options
    private val modeList =
        choices("Rotations", arrayOf("Off", "Normal", "Stabilized", "ReverseYaw", "GodBridge"), "GodBridge") { !isJumpBridgeEnabled }
    private val options = RotationSettingsWithRotationModes(this, modeList).apply {
        strictValue.excludeWithState()
        resetTicksValue.setSupport { it && scaffoldMode != "Telly" }
        applyServerSideValue.setSupport { supported -> supported || isJumpBridgeEnabled }
        simulateShortStopValue.setSupport { it && !isJumpBridgeEnabled }
        rotationDiffBuildUpToStopValue.setSupport { it && !isJumpBridgeEnabled }
        maxThresholdAttemptsToStopValue.setSupport { it && !isJumpBridgeEnabled }
        shortStopDurationValue.setSupport { it && !isJumpBridgeEnabled }
        strafeValue.setSupport { it && !isJumpBridgeEnabled }
        keepRotationValue.setSupport { it && !isJumpBridgeEnabled }
        resetTicksValue.setSupport { it && !isJumpBridgeEnabled }
        legitimizeValue.setSupport { it && !isJumpBridgeEnabled }
        maxHorizontalAngleChangeValue.setSupport { it && !isJumpBridgeEnabled }
        minHorizontalAngleChangeValue.setSupport { it && !isJumpBridgeEnabled }
        maxVerticalAngleChangeValue.setSupport { it && !isJumpBridgeEnabled }
        minVerticalAngleChangeValue.setSupport { it && !isJumpBridgeEnabled }
        angleResetDifferenceValue.setSupport { it && !isJumpBridgeEnabled }
        minRotationDifferenceValue.setSupport { it && !isJumpBridgeEnabled }
    }

    // Search mode
    val searchMode by choices("SearchMode", arrayOf("Area", "Center"), "Area") { scaffoldMode != "GodBridge" && !isJumpBridgeEnabled }
    private val minDist by float("MinDist", 0f, 0f..0.2f) { scaffoldMode !in arrayOf("GodBridge", "Telly") && !isJumpBridgeEnabled }

    // Zitter
    private val zitterMode by choices("Zitter", arrayOf("Off", "Teleport", "Smooth"), "Off") { !isJumpBridgeEnabled }
    private val zitterSpeed by float("ZitterSpeed", 0.13f, 0.1f..0.3f) { zitterMode == "Teleport" && !isJumpBridgeEnabled }
    private val zitterStrength by float("ZitterStrength", 0.05f, 0.0f..0.2f) { zitterMode == "Teleport" && !isJumpBridgeEnabled }

    private val maxZitterTicksValue: IntegerValue = object : IntegerValue("MaxZitterTicks", 3, 0..6) {
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtLeast(minZitterTicks)
        override fun isSupported() = zitterMode == "Smooth" && !isJumpBridgeEnabled
    }
    private val maxZitterTicks by maxZitterTicksValue

    private val minZitterTicksValue: IntegerValue = object : IntegerValue("MinZitterTicks", 2, 0..6) {
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtMost(maxZitterTicks)
        override fun isSupported() = zitterMode == "Smooth" && !maxZitterTicksValue.isMinimal() && !isJumpBridgeEnabled
    }
    private val minZitterTicks by minZitterTicksValue

    private val useSneakMidAir by boolean("UseSneakMidAir", false) { zitterMode == "Smooth" && !isJumpBridgeEnabled }

    // Timer / speed
    private val timer by float("Timer", 1f, 0.1f..10f) { !isJumpBridgeEnabled }
    private val speedModifier by float("SpeedModifier", 1f, 0f..2f) { !isJumpBridgeEnabled }

    private val speedLimiter by boolean("SpeedLimiter", false) { !slow && !isJumpBridgeEnabled }
    private val speedLimit by float("SpeedLimit", 0.11f, 0.01f..0.12f) { !slow && speedLimiter && !isJumpBridgeEnabled }

    private val slow by boolean("Slow", false) { !isJumpBridgeEnabled }
    private val slowGround by boolean("SlowOnlyGround", false) { slow }
    private val slowSpeed by float("SlowSpeed", 0.6f, 0.1f..1.0f) { slow }

    // Jump strafe
    private val jumpStrafe by boolean("JumpStrafe", false) { !isJumpBridgeEnabled }
    private val maxJumpStraightStrafeValue: FloatValue = object : FloatValue("MaxStraightStrafe", 0.45f, 0.1f..1.0f) {
        override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtLeast(minJumpStraightStrafeValue.get())
        override fun isSupported() = jumpStrafe
    }
    private val maxJumpStraightStrafe by maxJumpStraightStrafeValue

    private val minJumpStraightStrafeValue: FloatValue = object : FloatValue("MinStraightStrafe", 0.4f, 0.1f..1.0f) {
        override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtMost(maxJumpStraightStrafeValue.get())
        override fun isSupported() = jumpStrafe
    }
    private val minJumpStraightStrafe by minJumpStraightStrafeValue

    private val maxJumpDiagonalStrafeValue: FloatValue = object : FloatValue("MaxDiagonalStrafe", 0.45f, 0.1f..1.0f) {
        override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtLeast(minJumpDiagonalStrafeValue.get())
        override fun isSupported() = jumpStrafe
    }
    private val maxJumpDiagonalStrafe by maxJumpDiagonalStrafeValue

    private val minJumpDiagonalStrafeValue: FloatValue = object : FloatValue("MinDiagonalStrafe", 0.4f, 0.1f..1.0f) {
        override fun onChange(oldValue: Float, newValue: Float) = newValue.coerceAtMost(maxJumpDiagonalStrafeValue.get())
        override fun isSupported() = jumpStrafe
    }
    private val minJumpDiagonalStrafe by minJumpDiagonalStrafeValue

    // SameY
    private val sameY by boolean("SameY", false) { scaffoldMode != "GodBridge" && !isJumpBridgeEnabled }
    private val jumpOnUserInput by boolean("JumpOnUserInput", true) { sameY && scaffoldMode != "GodBridge" }

    // SafeWalk
    private val safeWalkValue = boolean("SafeWalk", true) { scaffoldMode != "GodBridge" && !isJumpBridgeEnabled }
    private val airSafe by boolean("AirSafe", false) { safeWalkValue.isActive() }

    // Visuals
    private val mark by boolean("Mark", false, subjective = true) { !isJumpBridgeEnabled }
    private val trackCPS by boolean("TrackCPS", false, subjective = true) { !isJumpBridgeEnabled }

    // Target placement
    var placeRotation: PlaceRotation? = null

    private var launchY = -999

    val shouldJumpOnInput: Boolean
        get() = !jumpOnUserInput || !mc.gameSettings.keyBindJump.isKeyDown && mc.thePlayer.posY >= launchY && !mc.thePlayer.onGround

    private val shouldKeepLaunchPosition: Boolean
        get() = sameY && shouldJumpOnInput && scaffoldMode != "GodBridge"

    // Zitter
    private var zitterDirection = false

    // Delay
    private val delayTimer = object : DelayTimer(minDelayValue, maxDelayValue, MSTimer()) {
        override fun hasTimePassed() = !placeDelayValue.isActive() || super.hasTimePassed()
    }
    private val zitterTickTimer = TickDelayTimer(minZitterTicksValue, maxZitterTicksValue)

    // Eagle state
    private var placedBlocksWithoutEagle = 0
    var eagleSneaking = false
        private set
    private var requestedStopSneak = false

    private val isEagleEnabled: Boolean
        get() = eagle != "Off" && !shouldGoDown && scaffoldMode != "GodBridge"

    // Downwards
    val shouldGoDown: Boolean
        get() = down
            && !sameY
            && GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)
            && scaffoldMode !in arrayOf("GodBridge", "Telly")
            && blocksAmount() > 1

    // Current rotation
    private val currRotation: Rotation
        get() = RotationUtils.currentRotation ?: mc.thePlayer.rotation

    // Extra clicks
    private var extraClick = ExtraClickInfo(TimeUtils.randomClickDelay(extraClickMinCPS, extraClickMaxCPS), 0L, 0)

    // GodBridge
    private var blocksPlacedUntilJump = 0
    private val isManualJumpOptionActive: Boolean
        get() = scaffoldMode == "GodBridge" && !jumpAutomatically
    private var blocksToJump = TimeUtils.randomDelay(minBlocksToJump, maxBlocksToJump)
    private var godBridgeTargetRotation: Rotation? = null

    private val isGodBridgeEnabled: Boolean
        get() = scaffoldMode == "GodBridge" || (scaffoldMode == "Normal" && options.rotationMode == "GodBridge")

    private val isLookingDiagonally: Boolean
        get() {
            val player = mc.thePlayer ?: return false
            val directionDegree = MovementUtils.direction.toDegreesF()
            val yaw = round(abs(MathHelper.wrapAngleTo180_float(directionDegree)) / 45f) * 45f
            val isYawDiagonal = yaw % 90 != 0f
            val isMovingDiagonal = player.movementInput.moveForward != 0f && player.movementInput.moveStrafe == 0f
            val isStrafing = mc.gameSettings.keyBindRight.isKeyDown || mc.gameSettings.keyBindLeft.isKeyDown
            return isYawDiagonal && (isMovingDiagonal || isStrafing)
        }

    private fun isHalfBlockLevel(y: Double) = abs(y - floor(y) - 0.5) <= 1E-3

    private fun isUsableBlockStack(stack: ItemStack?, fullCubeOnly: Boolean = false): Boolean {
        val itemBlock = stack?.item as? ItemBlock ?: return false
        val block = itemBlock.block

        return stack.stackSize > 0
            && block !in InventoryUtils.BLOCK_BLACKLIST
            && block !is BlockBush
            && (!fullCubeOnly || block.isFullCube)
    }

    // Telly
    private var offGroundTicks = 0
    private var ticksUntilJump = 0
    private var blocksUntilAxisChange = 0
    private var jumpTicks = TimeUtils.randomDelay(minJumpTicks, maxJumpTicks)
    private var horizontalPlacements = TimeUtils.randomDelay(minHorizontalPlacements, maxHorizontalPlacements)
    private var verticalPlacements = TimeUtils.randomDelay(minVerticalPlacements, maxVerticalPlacements)

    private val shouldPlaceHorizontally: Boolean
        get() = scaffoldMode == "Telly"
            && mc.thePlayer.isMoving
            && (startHorizontally && blocksUntilAxisChange <= horizontalPlacements || !startHorizontally && blocksUntilAxisChange > verticalPlacements)

    private fun jumpBridgeDelayMs(): Long =
        max(1L, (1000.0 / jumpBridgeCps.coerceAtLeast(1)).roundToLong())

    private fun jumpBridgeFacing(player: EntityPlayerSP): EnumFacing {
        if (player.isMoving) {
            return EnumFacing.fromAngle(MovementUtils.direction.toDegreesF().toDouble())
        }

        return lastJumpBridgeFacing ?: player.horizontalFacing
    }

    private fun updateJumpBridgeTarget() {
        val player = mc.thePlayer ?: return

        val holdingItem = player.heldItem?.item is ItemBlock
        if (!holdingItem && InventoryUtils.findBlockInHotbar() == null) {
            placeRotation = null
            return
        }

        val facing = jumpBridgeFacing(player)
        lastJumpBridgeFacing = facing

        placeRotation = null

        val basePos = BlockPos(player).down()
        val candidates = arrayOf(basePos, basePos.offset(facing))

        for (pos in candidates) {
            if (search(pos, raycast = true, area = false, horizontalOnly = true)) {
                return
            }
        }
    }

    override fun onEnable() {
        val player = mc.thePlayer ?: return

        launchY = player.posY.roundToInt()
        blocksUntilAxisChange = 0
        blocksPlacedUntilJump = 0
        blocksToJump = TimeUtils.randomDelay(minBlocksToJump, maxBlocksToJump)
        offGroundTicks = 0
        ticksUntilJump = 0
        godBridgeTargetRotation = null
        placeRotation = null
        jumpBridgePlaceTimer.zero()
        lastJumpBridgeFacing = null
    }

    val onUpdate = loopHandler {
        val player = mc.thePlayer ?: return@loopHandler

        if (mc.playerController.currentGameType == WorldSettings.GameType.SPECTATOR) {
            return@loopHandler
        }

        if (isJumpBridgeEnabled) {
            mc.timer.timerSpeed = 1f
            return@loopHandler
        }

        mc.timer.timerSpeed = timer

        if (player.onGround) {
            offGroundTicks = 0
            ticksUntilJump++
        } else {
            offGroundTicks++
        }

        if (shouldGoDown) {
            mc.gameSettings.keyBindSneak.pressed = false
        }

        if (slow && (!slowGround || player.onGround)) {
            player.motionX *= slowSpeed.toDouble()
            player.motionZ *= slowSpeed.toDouble()
        }

        if (isEagleEnabled) {
            var dif = 0.5
            val blockPos = BlockPos(player).down()

            for (side in EnumFacing.values()) {
                if (side.axis == EnumFacing.Axis.Y) continue

                val neighbor = blockPos.offset(side)
                if (!neighbor.isReplaceable) continue

                val calcDif = (if (side.axis == EnumFacing.Axis.Z) {
                    abs(neighbor.z + 0.5 - player.posZ)
                } else {
                    abs(neighbor.x + 0.5 - player.posX)
                }) - 0.5

                if (calcDif < dif) dif = calcDif
            }

            val blockSneaking = WaitTickUtils.hasScheduled("block")
            val alreadySneaking = WaitTickUtils.hasScheduled("sneak")

            val options = mc.gameSettings

            run {
                if (placedBlocksWithoutEagle < blocksToEagle
                    && !alreadySneaking
                    && !blockSneaking
                    && !eagleSneaking
                    && !requestedStopSneak
                ) {
                    return@run
                }

                val eagleCondition = when (eagleMode) {
                    "OnGround" -> player.onGround
                    "InAir" -> !player.onGround
                    else -> true
                }

                val pressedOnKeyboard = Keyboard.isKeyDown(options.keyBindSneak.keyCode)

                var shouldEagle = (eagleCondition && (blockPos.isReplaceable || dif < edgeDistance) || pressedOnKeyboard)
                val shouldSchedule = !requestedStopSneak

                if (requestedStopSneak) {
                    requestedStopSneak = false
                    if (!player.onGround) {
                        shouldEagle = pressedOnKeyboard
                    }
                } else if (blockSneaking || alreadySneaking) {
                    return@run
                }

                if (eagle == "Silent") {
                    if (eagleSneaking != shouldEagle) {
                        sendPacket(
                            C0BPacketEntityAction(
                                player,
                                if (shouldEagle) {
                                    C0BPacketEntityAction.Action.START_SNEAKING
                                } else {
                                    C0BPacketEntityAction.Action.STOP_SNEAKING
                                }
                            )
                        )

                        if (adjustedSneakSpeed && shouldEagle) {
                            player.motionX *= eagleSpeed
                            player.motionZ *= eagleSpeed
                        }
                    }

                    eagleSneaking = shouldEagle
                } else {
                    options.keyBindSneak.pressed = shouldEagle
                    eagleSneaking = shouldEagle
                }

                if (eagleSneaking && shouldSchedule) {
                    if (useMaxSneakTime) {
                        WaitTickUtils.conditionalSchedule(requester = "sneak") { elapsed ->
                            (elapsed >= maxSneakTicks + 1).also { requestedStopSneak = it }
                        }
                    }

                    if (blockSneakingAgainUntilOnGround && !player.onGround) {
                        WaitTickUtils.conditionalSchedule(requester = "block") {
                            mc.thePlayer?.onGround.also { if (it != false) requestedStopSneak = true } ?: true
                        }
                    }
                }

                placedBlocksWithoutEagle = 0
            }
        }

        if (player.onGround && scaffoldMode == "Rewinside") {
            MovementUtils.strafe(0.2F)
            player.motionY = 0.0
        }
    }

    val onStrafe = handler<StrafeEvent> {
        val player = mc.thePlayer ?: return@handler

        if (scaffoldMode == "Telly"
            && player.onGround
            && player.isMoving
            && currRotation == player.rotation
            && ticksUntilJump >= jumpTicks
        ) {
            player.tryJump()

            ticksUntilJump = 0
            jumpTicks = TimeUtils.randomDelay(minJumpTicks, maxJumpTicks)
        }
    }

    val onRotationUpdate = handler<RotationUpdateEvent> {
        val player = mc.thePlayer ?: return@handler

        if (player.ticksExisted == 1) {
            launchY = player.posY.roundToInt()
        }

        val rotation = RotationUtils.currentRotation

        if (isJumpBridgeEnabled) {
            if (modeList.get() == "Off") {
                modeList.set("Normal", saveImmediately = false)
            }

            updateJumpBridgeTarget()

            placeRotation?.rotation?.let { setTargetRotation(it, options, 1) }
            return@handler
        }

        update()

        val ticks = if (options.keepRotation) {
            if (scaffoldMode == "Telly") 1 else options.resetTicks
        } else {
            if (isGodBridgeEnabled) options.resetTicks else RotationUtils.resetTicks
        }

        if (!Tower.isTowering && isGodBridgeEnabled && options.rotationsActive) {
            generateGodBridgeRotations(ticks)
            return@handler
        }

        if (options.rotationsActive && rotation != null) {
            val placeRotation = this.placeRotation?.rotation ?: rotation

            if (RotationUtils.resetTicks != 0 || options.keepRotation) {
                setRotation(placeRotation, ticks)
            }
        }
    }

    val onTick = handler<GameTickEvent> {
        if (isJumpBridgeEnabled) {
            val target = placeRotation?.placeInfo ?: return@handler
            if (!jumpBridgePlaceTimer.hasTimePassed(jumpBridgeDelayMs())) return@handler

            val raycast = performBlockRaytrace(currRotation, mc.playerController.blockReachDistance)
            val result =
                if (raycast != null && raycast.typeOfHit.isBlock && raycast.blockPos == target.blockPos && raycast.sideHit == target.enumFacing) {
                    PlaceInfo(raycast.blockPos, raycast.sideHit, raycast.hitVec)
                } else {
                    target
                }

            if (placeJumpBridge(result)) {
                jumpBridgePlaceTimer.reset()
            }
            return@handler
        }

        val target = placeRotation?.placeInfo

        val raycastProperly =
            (scaffoldMode != "Expand" || expandLength <= 1) && !shouldGoDown && options.rotationsActive

        val raycast = performBlockRaytrace(currRotation, mc.playerController.blockReachDistance)

        var alreadyPlaced = false

        if (extraClicks) {
            val doubleClick = if (simulateDoubleClicking) RandomUtils.nextInt(-1, 1) else 0
            val clicks = extraClick.clicks + doubleClick

            repeat(clicks) {
                extraClick.clicks--

                doPlaceAttempt(raycast, it + 1 == clicks) { alreadyPlaced = true }
            }
        }

        if (target == null) {
            if (placeDelayValue.isActive()) {
                delayTimer.reset()
            }
            return@handler
        }

        if (alreadyPlaced || SilentHotbar.modifiedThisTick) {
            return@handler
        }

        raycast.let {
            if (!options.rotationsActive || it != null && it.typeOfHit.isBlock && it.blockPos == target.blockPos && (!raycastProperly || it.sideHit == target.enumFacing)) {
                val result = if (raycastProperly && it != null) {
                    PlaceInfo(it.blockPos, it.sideHit, it.hitVec)
                } else {
                    target
                }

                place(result)
            }
        }
    }

    val onSneakSlowDown = handler<SneakSlowDownEvent> { event ->
        if (isJumpBridgeEnabled) {
            return@handler
        }

        if (!isEagleEnabled || eagle != "Normal") {
            return@handler
        }

        event.forward *= eagleSpeed / 0.3f
        event.strafe *= eagleSpeed / 0.3f
    }

    val onMovementInput = handler<MovementInputEvent> { event ->
        val player = mc.thePlayer ?: return@handler

        if (!isGodBridgeEnabled || !player.onGround) return@handler

        if (waitForRots) {
            godBridgeTargetRotation?.run {
                event.originalInput.sneak =
                    event.originalInput.sneak || rotationDifference(this, currRotation) > getFixedAngleDelta()
            }
        }

        val simPlayer = SimulatedPlayer.fromClientPlayer(RotationUtils.modifiedInput)
        simPlayer.rotationYaw = currRotation.yaw
        simPlayer.tick()

        if (!simPlayer.onGround && !isManualJumpOptionActive || blocksPlacedUntilJump > blocksToJump) {
            event.originalInput.jump = true
            blocksPlacedUntilJump = 0
            blocksToJump = TimeUtils.randomDelay(minBlocksToJump, maxBlocksToJump)
        }
    }

    fun update() {
        val player = mc.thePlayer ?: return
        val holdingItem = player.heldItem?.item is ItemBlock

        if (!holdingItem && (autoBlock == "Off" || InventoryUtils.findBlockInHotbar() == null)) {
            return
        }

        findBlock(scaffoldMode == "Expand" && expandLength > 1, searchMode == "Area")
    }

    private fun setRotation(rotation: Rotation, ticks: Int) {
        val player = mc.thePlayer ?: return

        if (scaffoldMode == "Telly" && player.isMoving) {
            if (offGroundTicks < ticksUntilRotation && ticksUntilJump >= jumpTicks) {
                return
            }
        }

        setTargetRotation(rotation, options, ticks)
    }

    private fun findBlock(expand: Boolean, area: Boolean) {
        val player = mc.thePlayer ?: return

        if (!shouldKeepLaunchPosition) launchY = player.posY.roundToInt()

        val blockPosition = if (shouldGoDown) {
            if (isHalfBlockLevel(player.posY)) {
                BlockPos(player.posX, player.posY - 0.6, player.posZ)
            } else {
                BlockPos(player.posX, player.posY - 0.6, player.posZ).down()
            }
        } else if (shouldKeepLaunchPosition && launchY <= player.posY) {
            BlockPos(player.posX, launchY - 1.0, player.posZ)
        } else if (isHalfBlockLevel(player.posY)) {
            BlockPos(player)
        } else {
            BlockPos(player).down()
        }

        if (!expand && (!blockPosition.isReplaceable || search(blockPosition, !shouldGoDown, area, shouldPlaceHorizontally))) {
            return
        }

        if (expand) {
            val yaw = player.rotationYaw.toRadiansD()
            val x = if (omniDirectionalExpand) -sin(yaw).roundToInt() else player.horizontalFacing.directionVec.x
            val z = if (omniDirectionalExpand) cos(yaw).roundToInt() else player.horizontalFacing.directionVec.z

            repeat(expandLength) {
                if (search(blockPosition.add(x * it, 0, z * it), false, area)) return
            }
            return
        }

        val (horizontal, vertical) = if (scaffoldMode == "Telly") {
            5 to 3
        } else if (allowClutching) {
            horizontalClutchBlocks to verticalClutchBlocks
        } else {
            1 to 1
        }

        BlockPos.getAllInBox(
            blockPosition.add(-horizontal, 0, -horizontal), blockPosition.add(horizontal, -vertical, horizontal)
        ).sortedBy {
            BlockUtils.getCenterDistance(it)
        }.forEach {
            if (it.canBeClicked() || search(it, !shouldGoDown, area, shouldPlaceHorizontally)) {
                return
            }
        }
    }

    private fun place(placeInfo: PlaceInfo) {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        if (!delayTimer.hasTimePassed() || shouldKeepLaunchPosition && launchY - 1 != placeInfo.vec3.yCoord.toInt() && scaffoldMode != "Expand") {
            return
        }

        val currentSlot = SilentHotbar.currentSlot
        var stack = player.hotBarSlot(currentSlot).stack

        if (!isUsableBlockStack(stack) || sortByHighestAmount || earlySwitch) {
            val blockSlot = when {
                sortByHighestAmount -> InventoryUtils.findLargestBlockStackInHotbar()
                earlySwitch -> InventoryUtils.findBlockStackInHotbarGreaterThan(amountBeforeSwitch)
                    ?: InventoryUtils.findBlockInHotbar()
                else -> InventoryUtils.findBlockInHotbar()
            } ?: return

            stack = player.hotBarSlot(blockSlot).stack

            if ((stack.item as? ItemBlock)?.canPlaceBlockOnSide(world, placeInfo.blockPos, placeInfo.enumFacing, player, stack) == false) {
                return
            }

            if (autoBlock != "Off") {
                SilentHotbar.selectSlotSilently(
                    this,
                    blockSlot,
                    render = autoBlock == "Pick",
                    resetManually = true
                )
            }
        }

        tryToPlaceBlock(stack, placeInfo.blockPos, placeInfo.enumFacing, placeInfo.vec3)

        if (autoBlock == "Switch") {
            SilentHotbar.resetSlot(this, true)
        }

        findBlockToSwitchNextTick(stack)

        if (trackCPS) {
            CPSCounter.registerClick(CPSCounter.MouseButton.RIGHT)
        }
    }

    private fun placeJumpBridge(placeInfo: PlaceInfo): Boolean {
        val player = mc.thePlayer ?: return false
        val world = mc.theWorld ?: return false

        val currentSlot = SilentHotbar.currentSlot
        var stack = player.hotBarSlot(currentSlot).stack

        if (!isUsableBlockStack(stack)) {
            val blockSlot = InventoryUtils.findBlockInHotbar() ?: return false
            stack = player.hotBarSlot(blockSlot).stack ?: return false

            if ((stack.item as? ItemBlock)?.canPlaceBlockOnSide(world, placeInfo.blockPos, placeInfo.enumFacing, player, stack) == false) {
                return false
            }

            SilentHotbar.selectSlotSilently(this, blockSlot, render = false, resetManually = true)
        }

        return tryToPlaceBlock(stack, placeInfo.blockPos, placeInfo.enumFacing, placeInfo.vec3)
    }

    private fun doPlaceAttempt(raytrace: MovingObjectPosition?, lastClick: Boolean, onSuccess: () -> Unit = { }) {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        val stack = player.hotBarSlot(SilentHotbar.currentSlot).stack ?: return

        if (stack.item !is ItemBlock || InventoryUtils.BLOCK_BLACKLIST.contains((stack.item as ItemBlock).block)) {
            return
        }

        raytrace ?: return

        val block = stack.item as ItemBlock

        val canPlaceOnUpperFace = block.canPlaceBlockOnSide(world, raytrace.blockPos, EnumFacing.UP, player, stack)

        val shouldPlace = if (placementAttempt == "Fail") {
            !block.canPlaceBlockOnSide(world, raytrace.blockPos, raytrace.sideHit, player, stack)
        } else {
            if (shouldKeepLaunchPosition) {
                raytrace.blockPos.y == launchY - 1 && !canPlaceOnUpperFace
            } else if (shouldPlaceHorizontally) {
                !canPlaceOnUpperFace
            } else {
                raytrace.blockPos.y <= player.posY.toInt() - 1 && !(
                    raytrace.blockPos.y == player.posY.toInt() - 1 && canPlaceOnUpperFace && raytrace.sideHit == EnumFacing.UP
                    )
            }
        }

        if (!raytrace.typeOfHit.isBlock || !shouldPlace) {
            return
        }

        tryToPlaceBlock(stack, raytrace.blockPos, raytrace.sideHit, raytrace.hitVec, attempt = true) { onSuccess() }

        if (lastClick) {
            findBlockToSwitchNextTick(stack)
        }

        if (trackCPS) {
            CPSCounter.registerClick(CPSCounter.MouseButton.RIGHT)
        }
    }

    override fun onDisable() {
        val player = mc.thePlayer ?: return

        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)) {
            mc.gameSettings.keyBindSneak.pressed = false
            if (eagleSneaking && player.isSneaking) {
                player.isSneaking = false
            }
        }

        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindRight)) {
            mc.gameSettings.keyBindRight.pressed = false
        }
        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindLeft)) {
            mc.gameSettings.keyBindLeft.pressed = false
        }

        if (autoF5) {
            mc.gameSettings.thirdPersonView = 0
        }

        placeRotation = null
        godBridgeTargetRotation = null
        blocksPlacedUntilJump = 0
        jumpBridgePlaceTimer.zero()
        lastJumpBridgeFacing = null
        mc.timer.timerSpeed = 1f

        SilentHotbar.resetSlot(this)

        options.instant = false
    }

    val onMove = handler<MoveEvent> { event ->
        val player = mc.thePlayer ?: return@handler

        if (!safeWalkValue.isActive() || shouldGoDown) {
            return@handler
        }

        if (airSafe || player.onGround) {
            event.isSafeWalk = true
        }
    }

    val jumpHandler = handler<JumpEvent> { event ->
        if (!jumpStrafe) return@handler

        if (event.eventState == EventState.POST) {
            MovementUtils.strafe(
                if (!isLookingDiagonally) {
                    (minJumpStraightStrafe..maxJumpStraightStrafe).random()
                } else {
                    (minJumpDiagonalStrafe..maxJumpDiagonalStrafe).random()
                }
            )
        }
    }

    val onRender3D = handler<Render3DEvent> {
        val player = mc.thePlayer ?: return@handler

        val shouldBother =
            !shouldGoDown && (scaffoldMode != "Expand" || expandLength <= 1) && extraClicks && (player.isMoving || MovementUtils.speed > 0.03)

        if (shouldBother) {
            performBlockRaytrace(currRotation, mc.playerController.blockReachDistance)?.let { raytrace ->
                val timePassed = System.currentTimeMillis() - extraClick.lastClick >= extraClick.delay

                if (raytrace.typeOfHit.isBlock && timePassed) {
                    extraClick = ExtraClickInfo(
                        TimeUtils.randomClickDelay(extraClickMinCPS, extraClickMaxCPS),
                        System.currentTimeMillis(),
                        extraClick.clicks + 1
                    )
                }
            }
        }

        if (!mark) return@handler

        repeat(if (scaffoldMode == "Expand") expandLength + 1 else 2) {
            val yaw = player.rotationYaw.toRadiansD()
            val x = if (omniDirectionalExpand) -sin(yaw).roundToInt() else player.horizontalFacing.directionVec.x
            val z = if (omniDirectionalExpand) cos(yaw).roundToInt() else player.horizontalFacing.directionVec.z

            val blockPos = BlockPos(
                player.posX + x * it,
                if (shouldKeepLaunchPosition && launchY <= player.posY) {
                    launchY - 1.0
                } else {
                    player.posY - (if (isHalfBlockLevel(player.posY)) 0.0 else 1.0) - if (shouldGoDown) 1.0 else 0.0
                },
                player.posZ + z * it
            )

            val placeInfo = PlaceInfo.get(blockPos)

            if (blockPos.isReplaceable && placeInfo != null) {
                RenderUtils.drawBlockBox(blockPos, Color(68, 117, 255, 100), false)
                return@handler
            }
        }
    }

    fun search(blockPosition: BlockPos, raycast: Boolean, area: Boolean, horizontalOnly: Boolean = false): Boolean {
        val player = mc.thePlayer ?: return false

        options.instant = false

        if (!blockPosition.isReplaceable) {
            if (autoF5) mc.gameSettings.thirdPersonView = 0
            return false
        } else {
            if (autoF5 && mc.gameSettings.thirdPersonView != 1) mc.gameSettings.thirdPersonView = 1
        }

        val maxReach = mc.playerController.blockReachDistance
        val eyes = player.eyes

        var placeRotation: PlaceRotation? = null

        for (side in EnumFacing.values()) {
            if (horizontalOnly && side.axis == EnumFacing.Axis.Y) continue

            val neighbor = blockPosition.offset(side)
            if (!neighbor.canBeClicked()) continue

            val currPlaceRotation = if (!area || isGodBridgeEnabled) {
                findTargetPlace(blockPosition, neighbor, Vec3(0.5, 0.5, 0.5), side, eyes, maxReach, raycast)
            } else {
                var best: PlaceRotation? = null
                for (x in 0.1..0.9) {
                    for (y in 0.1..0.9) {
                        for (z in 0.1..0.9) {
                            val rotation =
                                findTargetPlace(blockPosition, neighbor, Vec3(x, y, z), side, eyes, maxReach, raycast)
                                    ?: continue
                            best = compareDifferences(rotation, best)
                        }
                    }
                }
                best
            } ?: continue

            placeRotation = compareDifferences(currPlaceRotation, placeRotation)
        }

        placeRotation ?: return false

        if (options.rotationsActive && !isGodBridgeEnabled) {
            val rotationDifference = rotationDifference(placeRotation.rotation, currRotation)
            val rotationDifference2 = rotationDifference(placeRotation.rotation / 90F, currRotation / 90F)

            val simPlayer = SimulatedPlayer.fromClientPlayer(player.movementInput)
            simPlayer.tick()

            options.instant =
                blockSafe && simPlayer.fallDistance > player.fallDistance + 0.05 && rotationDifference > rotationDifference2 / 2

            setRotation(placeRotation.rotation, if (scaffoldMode == "Telly") 1 else options.resetTicks)
        }

        this.placeRotation = placeRotation
        return true
    }

    private fun modifyVec(original: Vec3, direction: EnumFacing, pos: Vec3, shouldModify: Boolean): Vec3 {
        if (!shouldModify) {
            return original
        }

        val side = direction.opposite

        return when (side.axis ?: return original) {
            EnumFacing.Axis.Y -> Vec3(original.xCoord, pos.yCoord + side.directionVec.y.coerceAtLeast(0), original.zCoord)
            EnumFacing.Axis.X -> Vec3(pos.xCoord + side.directionVec.x.coerceAtLeast(0), original.yCoord, original.zCoord)
            EnumFacing.Axis.Z -> Vec3(original.xCoord, original.yCoord, pos.zCoord + side.directionVec.z.coerceAtLeast(0))
        }
    }

    private fun findTargetPlace(
        pos: BlockPos,
        offsetPos: BlockPos,
        vec3: Vec3,
        side: EnumFacing,
        eyes: Vec3,
        maxReach: Float,
        raycast: Boolean,
    ): PlaceRotation? {
        val world = mc.theWorld ?: return null

        val vec = (Vec3(pos) + vec3).addVector(
            side.directionVec.x * vec3.xCoord,
            side.directionVec.y * vec3.yCoord,
            side.directionVec.z * vec3.zCoord
        )

        val distance = eyes.distanceTo(vec)

        if (raycast) {
            if (distance > maxReach) return null

            val visibilityTrace = world.rayTraceBlocks(eyes, vec, false, false, true)
            if (visibilityTrace != null && (
                    !visibilityTrace.typeOfHit.isBlock
                        || visibilityTrace.blockPos != offsetPos
                        || visibilityTrace.sideHit != side.opposite
                    )
            ) {
                return null
            }
        }

        val diff = vec - eyes

        if (!isJumpBridgeEnabled && side.axis != EnumFacing.Axis.Y) {
            val dist = abs(if (side.axis == EnumFacing.Axis.Z) diff.zCoord else diff.xCoord)
            if (dist < minDist && scaffoldMode != "Telly") {
                return null
            }
        }

        val baseRotation = toRotation(vec, false).fixedSensitivity()
        val rotation = applyRotationMode(baseRotation)

        performBlockRaytrace(currRotation, maxReach)?.let { raytrace ->
            if (raytrace.typeOfHit.isBlock && raytrace.blockPos == offsetPos && (!raycast || raytrace.sideHit == side.opposite)) {
                return PlaceRotation(
                    PlaceInfo(
                        raytrace.blockPos,
                        side.opposite,
                        modifyVec(raytrace.hitVec, side, Vec3(offsetPos), !raycast)
                    ),
                    currRotation
                )
            }
        }

        val raytrace = performBlockRaytrace(rotation, maxReach) ?: return null

        val multiplier = if (options.legitimize) 3 else 1

        if (raytrace.typeOfHit.isBlock
            && raytrace.blockPos == offsetPos
            && (!raycast || raytrace.sideHit == side.opposite)
            && canUpdateRotation(currRotation, rotation, multiplier)
        ) {
            return PlaceRotation(
                PlaceInfo(
                    raytrace.blockPos,
                    side.opposite,
                    modifyVec(raytrace.hitVec, side, Vec3(offsetPos), !raycast)
                ),
                rotation
            )
        }

        return null
    }

    private fun performBlockRaytrace(rotation: Rotation, maxReach: Float): MovingObjectPosition? {
        val player = mc.thePlayer ?: return null
        val world = mc.theWorld ?: return null

        val eyes = player.eyes
        val rotationVec = getVectorForRotation(rotation)
        val reach = eyes + (rotationVec * maxReach.toDouble())

        return world.rayTraceBlocks(eyes, reach, false, false, true)
    }

    private fun compareDifferences(new: PlaceRotation, old: PlaceRotation?, rotation: Rotation = currRotation): PlaceRotation {
        if (old == null || rotationDifference(new.rotation, rotation) < rotationDifference(old.rotation, rotation)) {
            return new
        }

        return old
    }

    private fun findBlockToSwitchNextTick(stack: ItemStack) {
        if (autoBlock in arrayOf("Off", "Switch")) return

        val switchAmount = if (earlySwitch) amountBeforeSwitch else 0
        if (stack.stackSize > switchAmount) return

        val switchSlot = if (earlySwitch) {
            InventoryUtils.findBlockStackInHotbarGreaterThan(amountBeforeSwitch) ?: InventoryUtils.findBlockInHotbar()
        } else {
            InventoryUtils.findBlockInHotbar()
        } ?: return

        SilentHotbar.selectSlotSilently(
            this,
            switchSlot,
            render = autoBlock == "Pick",
            resetManually = true
        )
    }

    private fun updatePlacedBlocksForTelly() {
        if (blocksUntilAxisChange > horizontalPlacements + verticalPlacements) {
            blocksUntilAxisChange = 0
            horizontalPlacements = TimeUtils.randomDelay(minHorizontalPlacements, maxHorizontalPlacements)
            verticalPlacements = TimeUtils.randomDelay(minVerticalPlacements, maxVerticalPlacements)
            return
        }

        blocksUntilAxisChange++
    }

    private fun tryToPlaceBlock(
        stack: ItemStack,
        clickPos: BlockPos,
        side: EnumFacing,
        hitVec: Vec3,
        attempt: Boolean = false,
        onSuccess: () -> Unit = { },
    ): Boolean {
        val thePlayer = mc.thePlayer ?: return false

        val prevSize = stack.stackSize

        val clickedSuccessfully = thePlayer.onPlayerRightClick(clickPos, side, hitVec, stack)

        if (clickedSuccessfully) {
            if (!attempt) {
                delayTimer.reset()

                if (thePlayer.onGround) {
                    thePlayer.motionX *= speedModifier
                    thePlayer.motionZ *= speedModifier
                }
            }

            if (swing) {
                thePlayer.swingItem()
            } else {
                sendPacket(C0APacketAnimation())
            }

            if (isManualJumpOptionActive) blocksPlacedUntilJump++

            updatePlacedBlocksForTelly()

            if (stack.stackSize <= 0) {
                thePlayer.inventory.mainInventory[SilentHotbar.currentSlot] = null
                ForgeEventFactory.onPlayerDestroyItem(thePlayer, stack)
            } else if (stack.stackSize != prevSize || mc.playerController.isInCreativeMode) {
                mc.entityRenderer.itemRenderer.resetEquippedProgress()
            }

            placeRotation = null
            placedBlocksWithoutEagle++

            onSuccess()
        } else {
            if (thePlayer.sendUseItem(stack)) {
                mc.entityRenderer.itemRenderer.resetEquippedProgress2()
            }
        }

        return clickedSuccessfully
    }

    fun handleMovementOptions(input: MovementInput) {
        val player = mc.thePlayer ?: return

        if (!state) {
            return
        }

        if (isJumpBridgeEnabled) {
            if (jumpBridgeWalk && input.moveForward == 0f && input.moveStrafe == 0f) {
                input.moveForward = 1f
            }

            if (jumpBridgeJump) {
                input.jump = true
            }

            return
        }

        if (!slow && speedLimiter && MovementUtils.speed > speedLimit) {
            input.moveStrafe = 0f
            input.moveForward = 0f
            return
        }

        when (zitterMode.lowercase()) {
            "off" -> return

            "smooth" -> {
                val notOnGround = !player.onGround || !player.isCollidedVertically

                if (player.onGround) {
                    input.sneak = eagleSneaking || GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)
                }

                if (input.jump || mc.gameSettings.keyBindJump.isKeyDown || notOnGround) {
                    zitterTickTimer.reset()

                    if (useSneakMidAir) {
                        input.sneak = true
                    }

                    if (!notOnGround && !input.jump) {
                        input.moveStrafe = if (zitterDirection) 1f else -1f
                    } else {
                        input.moveStrafe = 0f
                    }

                    zitterDirection = !zitterDirection

                    if (mc.gameSettings.keyBindLeft.isKeyDown) {
                        input.moveStrafe++
                    }
                    if (mc.gameSettings.keyBindRight.isKeyDown) {
                        input.moveStrafe--
                    }
                    return
                }

                if (zitterTickTimer.hasTimePassed()) {
                    zitterDirection = !zitterDirection
                    zitterTickTimer.reset()
                } else {
                    zitterTickTimer.update()
                }

                input.moveStrafe = if (zitterDirection) -1f else 1f
            }

            "teleport" -> {
                MovementUtils.strafe(zitterSpeed)
                val yaw = (player.rotationYaw + if (zitterDirection) 90.0 else -90.0).toRadians()
                player.motionX -= sin(yaw) * zitterStrength
                player.motionZ += cos(yaw) * zitterStrength
                zitterDirection = !zitterDirection
            }
        }
    }

    private var isOnRightSide = false

    val canSprint: Boolean
        get() {
            val player = mc.thePlayer ?: return false

            return when (sprintMode) {
                "OnlyOnGround" -> player.onGround
                "OnlyInAir" -> !player.onGround
                else -> true
            }
        }

    private fun applyRotationMode(baseRotation: Rotation): Rotation {
        val player = mc.thePlayer ?: return baseRotation

        if (isJumpBridgeEnabled) {
            return baseRotation.fixedSensitivity()
        }

        val roundYaw90 = round(baseRotation.yaw / 90f) * 90f
        val roundYaw45 = round(baseRotation.yaw / 45f) * 45f

        val rotation = when (options.rotationMode) {
            "Stabilized" -> Rotation(roundYaw45, baseRotation.pitch)
            "ReverseYaw" -> Rotation(if (!isLookingDiagonally) roundYaw90 else roundYaw45, baseRotation.pitch)
            else -> baseRotation
        }

        return rotation.fixedSensitivity()
    }

    private fun isNearEdgeHorizontally(player: EntityPlayerSP, threshold: Float): Boolean {
        val blockPos = BlockPos(player.posX, player.posY, player.posZ)
        val px = player.posX
        val pz = player.posZ

        for (x in -3..3) {
            for (z in -3..3) {
                val checkPos = blockPos.add(x, -1, z)
                if (!player.worldObj.isAirBlock(checkPos)) continue

                val dx = (checkPos.x + 0.5) - px
                val dz = (checkPos.z + 0.5) - pz

                if (sqrt(dx * dx + dz * dz) <= threshold) {
                    return true
                }
            }
        }

        return false
    }

    private fun generateGodBridgeRotations(ticks: Int) {
        val player = mc.thePlayer ?: return

        val direction = if (options.applyServerSide) {
            MovementUtils.direction.toDegreesF() + 180f
        } else {
            MathHelper.wrapAngleTo180_float(player.rotationYaw)
        }

        val movingYaw = round(direction / 45f) * 45f

        val steps45 = arrayListOf(-135f, -45f, 45f, 135f)

        val isMovingStraight = if (options.applyServerSide) {
            movingYaw % 90 == 0f
        } else {
            movingYaw in steps45 && player.movementInput.isSideways
        }

        if (!isNearEdgeHorizontally(player, 2.5f)) return

        if (!player.isMoving) {
            placeRotation?.run {
                val axisMovement = floor(this.rotation.yaw / 90) * 90
                val yaw = axisMovement + 45f
                val pitch = 75f
                setRotation(Rotation(yaw, pitch), ticks)
                return
            }

            if (!options.keepRotation) return
        }

        val rotation = if (isMovingStraight) {
            if (player.onGround) {
                isOnRightSide =
                    floor(player.posX + cos(movingYaw.toRadians()) * 0.5) != floor(player.posX) ||
                        floor(player.posZ + sin(movingYaw.toRadians()) * 0.5) != floor(player.posZ)

                val posInDirection = BlockPos(player.positionVector.offset(EnumFacing.fromAngle(movingYaw.toDouble()), 0.6))

                val isLeaningOffBlock = player.position.down().block == air
                val nextBlockIsAir = posInDirection.down().block == air

                if (isLeaningOffBlock && nextBlockIsAir) {
                    isOnRightSide = !isOnRightSide
                }
            }

            val side = if (options.applyServerSide) {
                if (isOnRightSide) 45f else -45f
            } else {
                0f
            }

            Rotation(movingYaw + side, if (useOptimizedPitch) 73.5f else customGodPitch)
        } else {
            Rotation(movingYaw, 75.6f)
        }.fixedSensitivity()

        godBridgeTargetRotation = rotation

        setRotation(rotation, ticks)
    }

    override val tag: String
        get() = if (isJumpBridgeEnabled) scaffoldMode else if (towerMode != "None") "$scaffoldMode | $towerMode" else scaffoldMode

    data class ExtraClickInfo(val delay: Int, val lastClick: Long, var clicks: Int)
}
