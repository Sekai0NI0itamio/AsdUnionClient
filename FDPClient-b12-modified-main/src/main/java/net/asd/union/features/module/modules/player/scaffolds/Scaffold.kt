package net.asd.union.features.module.modules.player.scaffolds

import net.asd.union.config.*
import net.asd.union.event.*
import net.asd.union.features.module.Category
import net.asd.union.features.module.Module
import net.asd.union.utils.block.*
import net.asd.union.utils.extensions.*
import net.asd.union.utils.inventory.InventoryUtils
import net.asd.union.utils.inventory.SilentHotbar
import net.asd.union.utils.inventory.hotBarSlot
import net.asd.union.utils.movement.MovementUtils
import net.asd.union.utils.rotation.PlaceRotation
import net.asd.union.utils.rotation.Rotation
import net.asd.union.utils.rotation.RotationSettings
import net.asd.union.utils.rotation.RotationUtils
import net.asd.union.utils.rotation.RotationUtils.canUpdateRotation
import net.asd.union.utils.rotation.RotationUtils.getVectorForRotation
import net.asd.union.utils.rotation.RotationUtils.rotationDifference
import net.asd.union.utils.rotation.RotationUtils.performRaytrace
import net.asd.union.utils.rotation.RotationUtils.setTargetRotation
import net.asd.union.utils.rotation.RotationUtils.toRotation
import net.asd.union.utils.timing.MSTimer
import net.minecraft.init.Blocks.air
import net.minecraft.block.BlockBush
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.gui.Gui
import net.minecraft.client.settings.GameSettings
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.MovementInput
import net.minecraft.util.Vec3
import org.lwjgl.input.Keyboard
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sin

object Scaffold : Module("Scaffold", Category.PLAYER, Keyboard.KEY_V, hideModule = false) {

    private const val SCAN_RADIUS = 4
    private const val VERTICAL_SCAN_RADIUS = 1
    private const val ROTATION_HOLD_TICKS = 2

    private val sneakDelay by int("SneakDelay", 0, 0..100)
    private val onlyWhenLookingDown by boolean("OnlyWhenLookingDown", false)
    private val lookDownThreshold by float("LookDownThreshold", 45f, 0f..90f) { onlyWhenLookingDown }
    private val placeCps by int("CPS", 6, 1..20)

    var placeRotation: PlaceRotation? = null

    private val rotationSettings = RotationSettings(this).withoutKeepRotation().apply {
        prioritizeRequest = true
        immediate = true

        rotationsValue.setAndUpdateDefault(true)
        applyServerSideValue.setAndUpdateDefault(true)
        resetTicksValue.setAndUpdateDefault(2)
        legitimizeValue.setAndUpdateDefault(true)
        maxHorizontalAngleChangeValue.setAndUpdateDefault(12f)
        minHorizontalAngleChangeValue.setAndUpdateDefault(6f)
        maxVerticalAngleChangeValue.setAndUpdateDefault(10f)
        minVerticalAngleChangeValue.setAndUpdateDefault(4f)
    }

    private val placeTimer = MSTimer()
    private val sneakTimer = MSTimer()
    private var sneakOn = false

    private val currRotation: Rotation
        get() = RotationUtils.currentRotation ?: mc.thePlayer?.rotation ?: Rotation.ZERO

    override val tag: String
        get() = "Legit"

    override fun onEnable() {
        placeRotation = null
        sneakOn = false
        placeTimer.zero()
        sneakTimer.zero()
    }

    override fun onDisable() {
        placeRotation = null
        sneakOn = false
        placeTimer.zero()
        sneakTimer.zero()
        SilentHotbar.resetSlot(this, true)
        releaseSneakKey()
        clearOwnedRotation()
    }

    val onUpdate = handler<UpdateEvent> {
        val player = mc.thePlayer ?: return@handler

        val origin = placementOrigin(player)

        if (player.onGround && origin != null) {
            val shouldSneak = !onlyWhenLookingDown || player.rotationPitch >= lookDownThreshold

            if (shouldSneak && !GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)) {
                if (sneakTimer.hasTimePassed(sneakDelay)) {
                    mc.gameSettings.keyBindSneak.pressed = true
                    sneakTimer.reset()
                    sneakOn = false
                }
            } else {
                mc.gameSettings.keyBindSneak.pressed = false
            }

            sneakOn = true
        } else {
            if (sneakOn) {
                mc.gameSettings.keyBindSneak.pressed = false
            }

            sneakOn = false
        }

        if (!sneakOn && mc.currentScreen !is Gui) {
            mc.gameSettings.keyBindSneak.pressed = GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)
        }
    }

    val onRotationUpdate = handler<RotationUpdateEvent> {
        val player = mc.thePlayer ?: return@handler

        if (!refreshTarget(player)) {
            return@handler
        }

        placeRotation?.rotation?.let { setTargetRotation(it, rotationSettings, ROTATION_HOLD_TICKS) }
    }

    val onTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler

        if (placeRotation == null && !refreshTarget(player)) {
            return@handler
        }

        val target = placeRotation ?: return@handler

        setTargetRotation(target.rotation, rotationSettings, ROTATION_HOLD_TICKS)

        if (shouldPlaceNow(target) && placeTimer.hasTimePassed(placeDelayMs()) && attemptPlacement(target)) {
            placeTimer.reset()
        }
    }

    fun handleMovementOptions(input: MovementInput) {
        if (!state) {
            return
        }

        val player = mc.thePlayer ?: return

        if (player.onGround && placementOrigin(player) != null) {
            if (input.moveForward >= 0f) {
                input.moveForward = -1f
            }
            input.moveStrafe = 0f
            input.jump = false
            input.sneak = !onlyWhenLookingDown || player.rotationPitch >= lookDownThreshold
        }
    }

    fun search(blockPosition: BlockPos, raycast: Boolean, area: Boolean, horizontalOnly: Boolean = false): Boolean {
        val player = mc.thePlayer ?: return false
        val world = mc.theWorld ?: return false

        if (!blockPosition.isReplaceable) {
            return false
        }

        val eyes = player.eyes
        val currentRotation = currRotation
        val maxReach = mc.playerController.blockReachDistance
        val yOffsets = if (horizontalOnly) intArrayOf(0) else intArrayOf(-VERTICAL_SCAN_RADIUS, 0, VERTICAL_SCAN_RADIUS)
        val playerFeet = Vec3(player.posX, player.posY, player.posZ)
        val yawRadians = currentRotation.yaw.toRadiansD()
        val lookX = -sin(yawRadians)
        val lookZ = cos(yawRadians)

        val candidatePositions = if (area) {
            val positions = mutableListOf<BlockPos>()

            for (dx in -SCAN_RADIUS..SCAN_RADIUS) {
                for (dz in -SCAN_RADIUS..SCAN_RADIUS) {
                    if (dx * dx + dz * dz > SCAN_RADIUS * SCAN_RADIUS) {
                        continue
                    }

                    for (dy in yOffsets) {
                        positions += blockPosition.add(dx, dy, dz)
                    }
                }
            }

            positions
        } else {
            yOffsets.mapTo(mutableListOf()) { blockPosition.add(0, it, 0) }
        }

        var bestPlaceRotation: PlaceRotation? = null
        var bestScore = Double.MAX_VALUE

        for (candidateAir in candidatePositions) {
            if (!candidateAir.isReplaceable) {
                continue
            }

            for (side in EnumFacing.values()) {
                if (horizontalOnly && side.axis == EnumFacing.Axis.Y) {
                    continue
                }

                val supportPos = candidateAir.offset(side)
                if (!supportPos.canBeClicked()) {
                    continue
                }

                val face = side.opposite
                val hitVec = faceCenter(supportPos, face)
                val distance = eyes.distanceTo(hitVec)

                if (distance > maxReach) {
                    continue
                }

                if (raycast) {
                    val visibilityTrace = world.rayTraceBlocks(eyes, hitVec, false, false, true)
                    if (visibilityTrace != null && (
                            !visibilityTrace.typeOfHit.isBlock
                                || visibilityTrace.blockPos != supportPos
                                || visibilityTrace.sideHit != face
                        )
                    ) {
                        continue
                    }
                }

                val rotation = toRotation(hitVec, false).fixedSensitivity()
                val supportDx = supportPos.x + 0.5 - playerFeet.xCoord
                val supportDz = supportPos.z + 0.5 - playerFeet.zCoord
                val horizontalDistanceScore = supportDx * supportDx + supportDz * supportDz
                val angleDifference = rotationDifference(rotation, currentRotation).toDouble()
                val deltaX = supportPos.x + 0.5 - playerFeet.xCoord
                val deltaZ = supportPos.z + 0.5 - playerFeet.zCoord
                val forwardBias = deltaX * lookX + deltaZ * lookZ
                val score = horizontalDistanceScore + distance + angleDifference * 0.01 - forwardBias * 0.1

                if (score < bestScore) {
                    bestScore = score
                    bestPlaceRotation = PlaceRotation(PlaceInfo(supportPos, face, hitVec), rotation)
                }
            }
        }

        val result = bestPlaceRotation ?: return false

        placeRotation = result
        return true
    }

    private fun refreshTarget(player: EntityPlayerSP): Boolean {
        val origin = placementOrigin(player)

        if (origin == null) {
            if (placeRotation == null) {
                clearTarget()
                return false
            }

            return true
        }

        PlaceInfo.get(origin)?.let { placeInfo ->
            placeRotation = PlaceRotation(
                placeInfo,
                toRotation(faceCenter(placeInfo.blockPos, placeInfo.enumFacing), false).fixedSensitivity()
            )
        }

        if (placeRotation == null) {
            search(origin, raycast = false, area = false, horizontalOnly = true)
        }

        if (placeRotation == null) {
            clearTarget()
            return false
        }

        placeRotation?.rotation?.let { setTargetRotation(it, rotationSettings, ROTATION_HOLD_TICKS) }
        return placeRotation != null
    }

    private fun placementOrigin(player: EntityPlayerSP): BlockPos? {
        val directBelow = BlockPos(player).down()
        val forwardProbe = forwardProbe(player)

        return when {
            directBelow.isReplaceable -> directBelow
            forwardProbe.isReplaceable -> forwardProbe
            else -> null
        }
    }

    private fun forwardProbe(player: EntityPlayerSP): BlockPos {
        val yawDegrees = if (player.isMoving) {
            MovementUtils.direction.toDegreesF()
        } else {
            currRotation.yaw
        }

        val yawRadians = yawDegrees.toRadiansD()
        val forwardX = -sin(yawRadians)
        val forwardZ = cos(yawRadians)

        return BlockPos(player.posX + forwardX, player.posY - 1.0, player.posZ + forwardZ)
    }

    private fun shouldSneakNow(player: EntityPlayerSP): Boolean {
        return player.onGround && placementOrigin(player) != null && (!onlyWhenLookingDown || player.rotationPitch >= lookDownThreshold)
    }

    private fun shouldPlaceNow(target: PlaceRotation): Boolean {
        if (!target.placeInfo.blockPos.canBeClicked()) {
            return false
        }

        return performRaytrace(target.placeInfo.blockPos, target.rotation) != null
    }

    private fun attemptPlacement(target: PlaceRotation): Boolean {
        val world = mc.theWorld ?: return false

        if (!shouldPlaceNow(target)) {
            return false
        }

        if (SilentHotbar.modifiedThisTick && !SilentHotbar.isSlotModified(this)) {
            return false
        }

        val stack = resolveBlockStack(target, world) ?: return false
        val player = mc.thePlayer ?: return false

        val clicked = player.onPlayerRightClick(
            target.placeInfo.blockPos,
            target.placeInfo.enumFacing,
            target.placeInfo.vec3,
            stack
        )

        if (clicked) {
            player.swingItem()
        }

        return clicked
    }

    private fun resolveBlockStack(target: PlaceRotation, world: net.minecraft.world.World): ItemStack? {
        val player = mc.thePlayer ?: return null

        val currentSlot = SilentHotbar.currentSlot
        val currentStack = player.hotBarSlot(currentSlot).stack

        if (isUsableBlockStack(currentStack, world, target)) {
            return currentStack
        }

        val blockSlot = InventoryUtils.findBlockInHotbar() ?: return null
        val stack = player.hotBarSlot(blockSlot).stack ?: return null

        if (!isUsableBlockStack(stack, world, target)) {
            return null
        }

        if (blockSlot != currentSlot) {
            SilentHotbar.selectSlotSilently(this, blockSlot, immediate = true, render = false, resetManually = true)
        }

        return player.hotBarSlot(SilentHotbar.currentSlot).stack ?: stack
    }

    private fun isUsableBlockStack(stack: ItemStack?, world: net.minecraft.world.World, target: PlaceRotation): Boolean {
        val player = mc.thePlayer ?: return false
        val itemBlock = stack?.item as? ItemBlock ?: return false
        val block = itemBlock.block

        return stack.stackSize > 0
            && block !in InventoryUtils.BLOCK_BLACKLIST
            && block !is BlockBush
            && itemBlock.canPlaceBlockOnSide(world, target.placeInfo.blockPos, target.placeInfo.enumFacing, player, stack)
    }

    private fun clearTarget() {
        placeRotation = null
        placeTimer.zero()
        clearOwnedRotation()
    }

    private fun clearOwnedRotation() {
        if (RotationUtils.activeSettings === rotationSettings) {
            RotationUtils.targetRotation = null
            RotationUtils.currentRotation = null
            RotationUtils.activeSettings = null
            RotationUtils.resetTicks = 0
        }
    }

    private fun releaseSneakKey() {
        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)) {
            mc.gameSettings.keyBindSneak.pressed = false
        }
    }

    private fun performBlockRaytrace(rotation: Rotation, maxReach: Float): MovingObjectPosition? {
        val player = mc.thePlayer ?: return null
        val world = mc.theWorld ?: return null

        val eyes = player.eyes
        val rotationVec = getVectorForRotation(rotation)
        val reach = eyes + (rotationVec * maxReach.toDouble())

        return world.rayTraceBlocks(eyes, reach, false, false, true)
    }

    private fun faceCenter(pos: BlockPos, facing: EnumFacing): Vec3 {
        return Vec3(
            pos.x + 0.5 + facing.directionVec.x * 0.5,
            pos.y + 0.5 + facing.directionVec.y * 0.5,
            pos.z + 0.5 + facing.directionVec.z * 0.5
        )
    }

    private fun placeDelayMs(): Long = max(1L, (1000.0 / placeCps.coerceAtLeast(1)).roundToLong())
}