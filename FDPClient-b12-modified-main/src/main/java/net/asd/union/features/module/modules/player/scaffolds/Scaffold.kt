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
import net.asd.union.utils.movement.MovementUtils
import net.asd.union.utils.render.RenderUtils
import net.asd.union.utils.rotation.PlaceRotation
import net.asd.union.utils.rotation.Rotation
import net.asd.union.utils.rotation.RotationSettingsWithRotationModes
import net.asd.union.utils.rotation.RotationUtils
import net.asd.union.utils.rotation.RotationUtils.getVectorForRotation
import net.asd.union.utils.rotation.RotationUtils.rotationDifference
import net.asd.union.utils.rotation.RotationUtils.setTargetRotation
import net.asd.union.utils.rotation.RotationUtils.toRotation
import net.asd.union.utils.timing.DelayTimer
import net.asd.union.utils.timing.MSTimer
import net.minecraft.block.BlockBush
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.settings.GameSettings
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.util.*
import net.minecraft.world.WorldSettings
import net.minecraftforge.event.ForgeEventFactory
import org.lwjgl.input.Keyboard
import java.awt.Color
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

object Scaffold : Module("Scaffold", Category.PLAYER, Keyboard.KEY_V, hideModule = false) {

    val scaffoldMode by choices(
        "ScaffoldMode",
        arrayOf("JumpBridge", "Normal", "Rewinside", "Expand", "Telly", "GodBridge"),
        "Normal"
    )

    private val jumpAutomaticallyValue = boolean("JumpAutomatically", false)
    val jumpAutomatically by jumpAutomaticallyValue

    private val placeHeight by float("PlaceHeight", 0.18f, 0f..0.42f)
    private val sameY by boolean("SameY", false)
    private val jumpOnUserInput by boolean("JumpOnUserInput", true) { sameY }
    private val down by boolean("Down", true) { !sameY }

    private val swing by boolean("Swing", true, subjective = true)
    private val timer by float("Timer", 1f, 0.1f..10f)
    private val speedModifier by float("SpeedModifier", 1f, 0f..2f)

    private val placeDelayValue = boolean("PlaceDelay", true)
    private val maxDelayValue: IntegerValue = object : IntegerValue("MaxDelay", 0, 0..1000) {
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtLeast(minDelay)
        override fun isSupported() = placeDelayValue.isActive()
    }
    private val maxDelay by maxDelayValue

    private val minDelayValue = object : IntegerValue("MinDelay", 0, 0..1000) {
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtMost(maxDelay)
        override fun isSupported() = placeDelayValue.isActive() && !maxDelayValue.isMinimal()
    }
    private val minDelay by minDelayValue

    private val autoBlock by choices("AutoBlock", arrayOf("Off", "Pick", "Spoof", "FakeSpoof", "Switch"), "Spoof")
    private val sortByHighestAmount by boolean("SortByHighestAmount", false) { autoBlock != "Off" }
    private val earlySwitch by boolean("EarlySwitch", false) { autoBlock != "Off" && !sortByHighestAmount }
    private val amountBeforeSwitch by int("SlotAmountBeforeSwitch", 3, 1..10) {
        earlySwitch && !sortByHighestAmount
    }

    val sprint by boolean("Sprint", false)
    val sprintMode by choices("SprintMode", arrayOf("Normal", "MotionSprint", "OnlyOnGround", "OnlyInAir"), "Normal") {
        sprint
    }

    private val eagleValue = choices("Eagle", arrayOf("Normal", "Silent", "Off"), "Off")
    val eagle by eagleValue
    private val eagleSpeed by float("EagleSpeed", 0.3f, 0.3f..1.0f) { eagle != "Off" }
    private val eagleEdgeDistance by float("EagleEdgeDistance", 0.12f, 0f..0.5f) { eagle != "Off" }
    val eagleSprint by boolean("EagleSprint", false) { eagle == "Normal" }

    val searchMode by choices("SearchMode", arrayOf("Area", "Center"), "Area")

    private val safeWalkValue = boolean("SafeWalk", true)
    private val airSafe by boolean("AirSafe", false) { safeWalkValue.isActive() }
    private val mark by boolean("Mark", false, subjective = true)

    private val modeList = choices("Rotations", arrayOf("Off", "Normal"), "Normal")
    private val options = RotationSettingsWithRotationModes(this, modeList)

    var placeRotation: PlaceRotation? = null
    var eagleSneaking = false

    private var targetBlockPos: BlockPos? = null
    private var launchY = -999
    private var bridgeStartY = -999.0
    private var lastGroundY = -999.0
    private var awaitingPlacement = false
    private var jumpedByModule = false
    private var placedThisJump = false
    private var lastOnGround = false
    private var silentSneakSent = false

    private val currRotation: Rotation
        get() = RotationUtils.currentRotation ?: mc.thePlayer.rotation

    private val currentRotationsActive: Boolean
        get() = options.rotationMode != "Off"

    private val usesJumpBridgePlacement: Boolean
        get() = scaffoldMode == "JumpBridge"

    val shouldJumpOnInput: Boolean
        get() = !jumpOnUserInput || !mc.gameSettings.keyBindJump.isKeyDown

    val shouldGoDown: Boolean
        get() = down && !sameY && GameSettings.isKeyDown(mc.gameSettings.keyBindSneak) && blocksAmount() > 1

    val canSprint: Boolean
        get() {
            val player = mc.thePlayer ?: return false

            return when (sprintMode) {
                "OnlyOnGround" -> player.onGround
                "OnlyInAir" -> !player.onGround
                else -> true
            }
        }

    private val delayTimer = object : DelayTimer(minDelayValue, maxDelayValue, MSTimer()) {
        override fun hasTimePassed() = !placeDelayValue.isActive() || super.hasTimePassed()
    }

    override fun onEnable() {
        val player = mc.thePlayer ?: return

        launchY = player.posY.roundToInt()
        bridgeStartY = player.posY
        lastGroundY = player.posY
        awaitingPlacement = false
        jumpedByModule = false
        placedThisJump = false
        lastOnGround = player.onGround
        clearPlacementTarget()
        delayTimer.reset()
    }

    override fun onDisable() {
        val player = mc.thePlayer ?: return

        mc.timer.timerSpeed = 1f
        awaitingPlacement = false
        jumpedByModule = false
        placedThisJump = false
        clearPlacementTarget()
        SilentHotbar.resetSlot(this)
        options.instant = false

        if (eagle == "Silent" && silentSneakSent) {
            sendPacket(C0BPacketEntityAction(player, C0BPacketEntityAction.Action.STOP_SNEAKING))
            silentSneakSent = false
        }

        eagleSneaking = false

        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)) {
            mc.gameSettings.keyBindSneak.pressed = false
            if (player.isSneaking) {
                player.isSneaking = false
            }
        }
    }

    val onUpdate = loopHandler {
        val player = mc.thePlayer ?: return@loopHandler

        if (mc.playerController.currentGameType == WorldSettings.GameType.SPECTATOR) {
            clearPlacementTarget()
            return@loopHandler
        }

        mc.timer.timerSpeed = timer

        updateJumpTracking(player)
        updateEagleState(player)

        if (!hasPlaceableBlocks()) {
            clearPlacementTarget()
            return@loopHandler
        }

        if (!shouldSearchForPlacement(player)) {
            if (player.onGround && !shouldGoDown) {
                clearPlacementTarget()
            }
            return@loopHandler
        }

        if (!updatePlacementTarget(player)) {
            clearPlacementTarget()
        }
    }

    val onStrafe = handler<StrafeEvent> {
        val player = mc.thePlayer ?: return@handler

        if (Tower.isTowering || !usesJumpBridgePlacement || !shouldStartAutoJump(player)) {
            return@handler
        }

        bridgeStartY = player.posY
        awaitingPlacement = true
        jumpedByModule = true
        placedThisJump = false
        clearPlacementTarget()
        player.tryJump()
    }

    val onRotationUpdate = handler<RotationUpdateEvent> {
        val player = mc.thePlayer ?: return@handler

        if (player.ticksExisted == 1) {
            launchY = player.posY.roundToInt()
            lastGroundY = player.posY
        }

        val rotation = placeRotation?.rotation ?: return@handler
        if (!currentRotationsActive) return@handler

        setTargetRotation(rotation, options, options.resetTicks)
    }

    val onTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler
        val placeRotation = placeRotation ?: return@handler

        targetBlockPos?.let {
            if (!it.isReplaceable) {
                clearPlacementTarget()
                return@handler
            }
        }

        if (!shouldAttemptPlacement(player) || !rotationReady(placeRotation.placeInfo)) {
            return@handler
        }

        place(placeRotation.placeInfo)
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

    val onSneakSlowDown = handler<SneakSlowDownEvent> { event ->
        if (eagle == "Off" || !eagleSneaking) {
            return@handler
        }

        event.forward *= eagleSpeed / 0.3f
        event.strafe *= eagleSpeed / 0.3f
    }

    val onRender3D = handler<Render3DEvent> {
        if (!mark) return@handler

        targetBlockPos?.let { RenderUtils.drawBlockBox(it, Color(68, 117, 255, 100), false) }
    }

    fun handleMovementOptions(input: MovementInput) {
        if (!state) {
            return
        }

        if (eagle == "Silent" && eagleSneaking) {
            input.sneak = true
        }
    }

    private fun updateJumpTracking(player: EntityPlayerSP) {
        if (player.onGround) {
            lastGroundY = player.posY

            if (!sameY) {
                launchY = player.posY.roundToInt()
            }

            if (!lastOnGround) {
                awaitingPlacement = false
                jumpedByModule = false
                placedThisJump = false
                clearPlacementTarget()
            }
        } else if (lastOnGround) {
            bridgeStartY = if (lastGroundY > -998.0) lastGroundY else player.posY

            if (jumpedByModule || GameSettings.isKeyDown(mc.gameSettings.keyBindJump) || player.motionY > 0.0) {
                awaitingPlacement = true
                placedThisJump = false
            }
        }

        lastOnGround = player.onGround
    }

    private fun updateEagleState(player: EntityPlayerSP) {
        val userSneaking = GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)
        val shouldSneak = userSneaking || (
            eagle != "Off"
                && player.onGround
                && !shouldGoDown
                && hasBridgeGap(player)
                && player.isNearEdge(1.1f + eagleEdgeDistance)
            )

        when (eagle) {
            "Silent" -> {
                if (silentSneakSent != shouldSneak) {
                    sendPacket(
                        C0BPacketEntityAction(
                            player,
                            if (shouldSneak) {
                                C0BPacketEntityAction.Action.START_SNEAKING
                            } else {
                                C0BPacketEntityAction.Action.STOP_SNEAKING
                            }
                        )
                    )
                    silentSneakSent = shouldSneak
                }
            }

            else -> if (!userSneaking || shouldSneak) {
                mc.gameSettings.keyBindSneak.pressed = shouldSneak
            }
        }

        eagleSneaking = shouldSneak
    }

    private fun shouldSearchForPlacement(player: EntityPlayerSP): Boolean {
        if (shouldGoDown) {
            return true
        }

        if (!usesJumpBridgePlacement) {
            return hasBridgeGap(player) || targetBlockPos?.isReplaceable == true
        }

        return awaitingPlacement && !placedThisJump && !player.onGround
    }

    private fun shouldAttemptPlacement(player: EntityPlayerSP): Boolean {
        if (!usesJumpBridgePlacement) {
            return shouldGoDown || targetBlockPos != null || hasBridgeGap(player)
        }

        if (placedThisJump) {
            return false
        }

        if (shouldGoDown) {
            return true
        }

        if (!awaitingPlacement || player.onGround) {
            return false
        }

        val referenceY = if (bridgeStartY > -998.0) bridgeStartY else lastGroundY
        return referenceY > -998.0 && player.posY - referenceY >= placeHeight
    }

    private fun shouldStartAutoJump(player: EntityPlayerSP): Boolean {
        if (!usesJumpBridgePlacement) {
            return false
        }

        if (!jumpAutomatically || shouldGoDown || !player.onGround || !player.isMoving) {
            return false
        }

        if (awaitingPlacement || GameSettings.isKeyDown(mc.gameSettings.keyBindJump) || !shouldJumpOnInput) {
            return false
        }

        return hasPlaceableBlocks() && hasBridgeGap(player)
    }

    private fun hasBridgeGap(player: EntityPlayerSP): Boolean {
        return candidateSearchPositions(player, groundTargetY(player)).any { candidate ->
            candidate.isReplaceable && hasClickableNeighbor(candidate)
        }
    }

    private fun updatePlacementTarget(player: EntityPlayerSP): Boolean {
        val area = searchMode == "Area"

        for (candidate in candidateSearchPositions(player, currentTargetY(player))) {
            if (!candidate.isReplaceable || !hasClickableNeighbor(candidate)) {
                continue
            }

            if (search(candidate, true, area)) {
                targetBlockPos = candidate
                return true
            }
        }

        return false
    }

    private fun groundTargetY(player: EntityPlayerSP): Int {
        return if (sameY && launchY != -999) {
            launchY - 1
        } else {
            floor(player.posY - 1.0).toInt()
        }
    }

    private fun currentTargetY(player: EntityPlayerSP): Int {
        return when {
            shouldGoDown -> floor(player.posY - 1.5).toInt()
            usesJumpBridgePlacement && bridgeStartY > -998.0 -> floor(bridgeStartY - 1.0).toInt()
            sameY && launchY != -999 -> launchY - 1
            else -> floor(player.posY - 1.0).toInt()
        }
    }

    private fun candidateSearchPositions(player: EntityPlayerSP, y: Int): List<BlockPos> {
        val basePositions = linkedSetOf<BlockPos>()

        fun addPos(x: Double, z: Double) {
            basePositions += BlockPos(x, y.toDouble(), z)
        }

        addPos(player.posX, player.posZ)
        addPos(player.posX + player.motionX * 2.0, player.posZ + player.motionZ * 2.0)

        if (player.isMoving) {
            val direction = MovementUtils.direction
            val directionX = -sin(direction)
            val directionZ = cos(direction)

            for (distance in arrayOf(0.35, 0.7, 1.05, 1.4)) {
                addPos(player.posX + directionX * distance, player.posZ + directionZ * distance)
            }
        }

        val expanded = linkedSetOf<BlockPos>()

        for (base in basePositions) {
            expanded += base

            BlockPos.getAllInBox(base.add(-1, -1, -1), base.add(1, 0, 1))
                .sortedBy { player.getDistanceSq(it.x + 0.5, it.y + 0.5, it.z + 0.5) }
                .forEach { expanded += it }
        }

        return expanded.toList()
    }

    private fun hasPlaceableBlocks(): Boolean {
        val player = mc.thePlayer ?: return false

        if (isUsableBlockStack(player.heldItem)) {
            return true
        }

        return autoBlock != "Off" && InventoryUtils.findBlockInHotbar() != null
    }

    private fun hasClickableNeighbor(pos: BlockPos): Boolean {
        return EnumFacing.values().any { pos.offset(it).canBeClicked() }
    }

    private fun clearPlacementTarget() {
        placeRotation = null
        targetBlockPos = null
    }

    fun search(
        blockPosition: BlockPos,
        raycast: Boolean,
        area: Boolean,
        horizontalOnly: Boolean = false,
    ): Boolean {
        val player = mc.thePlayer ?: return false

        options.instant = false

        if (!blockPosition.isReplaceable) {
            return false
        }

        val maxReach = mc.playerController.blockReachDistance
        val eyes = player.eyes
        var bestRotation: PlaceRotation? = null

        for (side in EnumFacing.values()) {
            if (horizontalOnly && side.axis == EnumFacing.Axis.Y) {
                continue
            }

            val neighbor = blockPosition.offset(side)
            if (!neighbor.canBeClicked()) {
                continue
            }

            if (!area) {
                val placeRotation =
                    findTargetPlace(blockPosition, neighbor, Vec3(0.5, 0.5, 0.5), side, eyes, maxReach, raycast)
                        ?: continue

                bestRotation = compareDifferences(placeRotation, bestRotation)
                continue
            }

            for (x in 0.1..0.9) {
                for (y in 0.1..0.9) {
                    for (z in 0.1..0.9) {
                        val placeRotation =
                            findTargetPlace(blockPosition, neighbor, Vec3(x, y, z), side, eyes, maxReach, raycast)
                                ?: continue

                        bestRotation = compareDifferences(placeRotation, bestRotation)
                    }
                }
            }
        }

        bestRotation ?: return false

        placeRotation = bestRotation
        targetBlockPos = blockPosition

        if (currentRotationsActive) {
            setTargetRotation(bestRotation.rotation, options, options.resetTicks)
        }

        return true
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

        if (eyes.distanceTo(vec) > maxReach) {
            return null
        }

        if (raycast) {
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

        performBlockRaytrace(currRotation, maxReach)?.let { currentTrace ->
            if (currentTrace.typeOfHit.isBlock
                && currentTrace.blockPos == offsetPos
                && (!raycast || currentTrace.sideHit == side.opposite)
            ) {
                return PlaceRotation(
                    PlaceInfo(
                        currentTrace.blockPos,
                        side.opposite,
                        modifyVec(currentTrace.hitVec, side, Vec3(offsetPos), !raycast)
                    ),
                    currRotation
                )
            }
        }

        if (!currentRotationsActive) {
            return null
        }

        val desiredRotation = toRotation(vec, false).fixedSensitivity()
        val targetTrace = performBlockRaytrace(desiredRotation, maxReach) ?: return null

        if (!targetTrace.typeOfHit.isBlock || targetTrace.blockPos != offsetPos || raycast && targetTrace.sideHit != side.opposite) {
            return null
        }

        return PlaceRotation(
            PlaceInfo(
                targetTrace.blockPos,
                side.opposite,
                modifyVec(targetTrace.hitVec, side, Vec3(offsetPos), !raycast)
            ),
            desiredRotation
        )
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

    private fun performBlockRaytrace(rotation: Rotation, maxReach: Float): MovingObjectPosition? {
        val player = mc.thePlayer ?: return null
        val world = mc.theWorld ?: return null

        val eyes = player.eyes
        val reach = eyes + (getVectorForRotation(rotation) * maxReach.toDouble())

        return world.rayTraceBlocks(eyes, reach, false, false, true)
    }

    private fun compareDifferences(new: PlaceRotation, old: PlaceRotation?): PlaceRotation {
        if (old == null || rotationDifference(new.rotation, currRotation) < rotationDifference(old.rotation, currRotation)) {
            return new
        }

        return old
    }

    private fun rotationReady(placeInfo: PlaceInfo): Boolean {
        if (!currentRotationsActive) {
            return true
        }

        val raytrace = performBlockRaytrace(currRotation, mc.playerController.blockReachDistance) ?: return false
        return raytrace.typeOfHit.isBlock && raytrace.blockPos == placeInfo.blockPos && raytrace.sideHit == placeInfo.enumFacing
    }

    private fun place(placeInfo: PlaceInfo) {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        if (!delayTimer.hasTimePassed()) {
            return
        }

        if (targetBlockPos != null && !targetBlockPos!!.isReplaceable) {
            clearPlacementTarget()
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

        if (tryToPlaceBlock(stack, placeInfo.blockPos, placeInfo.enumFacing, placeInfo.vec3)) {
            if (autoBlock == "Switch") {
                SilentHotbar.resetSlot(this, true)
            }

            findBlockToSwitchNextTick(stack)
            CPSCounter.registerClick(CPSCounter.MouseButton.RIGHT)
        }
    }

    private fun tryToPlaceBlock(
        stack: ItemStack,
        clickPos: BlockPos,
        side: EnumFacing,
        hitVec: Vec3,
    ): Boolean {
        val player = mc.thePlayer ?: return false

        val previousSize = stack.stackSize
        val suppressAnimation = autoBlock == "FakeSpoof" && SilentHotbar.isSlotModified(this)
        val motionSprintActive = sprint && sprintMode == "MotionSprint" && player.isMoving

        if (motionSprintActive) {
            sendPacket(C0BPacketEntityAction(player, C0BPacketEntityAction.Action.STOP_SPRINTING))
        }

        val clickedSuccessfully = player.onPlayerRightClick(clickPos, side, hitVec, stack)

        if (motionSprintActive) {
            sendPacket(C0BPacketEntityAction(player, C0BPacketEntityAction.Action.START_SPRINTING))
        }

        if (!clickedSuccessfully) {
            if (player.sendUseItem(stack) && !suppressAnimation) {
                mc.entityRenderer.itemRenderer.resetEquippedProgress2()
            }
            return false
        }

        delayTimer.reset()

        if (player.onGround) {
            player.motionX *= speedModifier
            player.motionZ *= speedModifier
        }

        if (swing) {
            player.swingItem()
        } else {
            sendPacket(C0APacketAnimation())
        }

        if (stack.stackSize <= 0) {
            player.inventory.mainInventory[SilentHotbar.currentSlot] = null
            ForgeEventFactory.onPlayerDestroyItem(player, stack)
        } else if (!suppressAnimation && (stack.stackSize != previousSize || mc.playerController.isInCreativeMode)) {
            mc.entityRenderer.itemRenderer.resetEquippedProgress()
        }

        placedThisJump = true
        awaitingPlacement = false
        jumpedByModule = false
        clearPlacementTarget()

        return true
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

    private fun isUsableBlockStack(stack: ItemStack?): Boolean {
        val itemBlock = stack?.item as? ItemBlock ?: return false
        val block = itemBlock.block

        return stack.stackSize > 0
            && block !in InventoryUtils.BLOCK_BLACKLIST
            && block !is BlockBush
    }

    override val tag: String
        get() = scaffoldMode
}
