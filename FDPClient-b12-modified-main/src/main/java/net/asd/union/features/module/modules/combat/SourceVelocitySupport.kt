package net.asd.union.features.module.modules.combat

import net.asd.union.config.*
import net.asd.union.event.*
import net.asd.union.features.module.Module
import net.asd.union.utils.attack.EntityUtils.isLookingOnEntities
import net.asd.union.utils.attack.EntityUtils.isSelected
import net.asd.union.utils.client.*
import net.asd.union.utils.client.PacketUtils.sendPacket
import net.asd.union.utils.client.PacketUtils.sendPackets
import net.asd.union.utils.extensions.*
import net.asd.union.utils.kotlin.RandomUtils.nextInt
import net.asd.union.utils.movement.MovementUtils.isOnGround
import net.asd.union.utils.movement.MovementUtils.speed
import net.asd.union.utils.rotation.RaycastUtils.runWithModifiedRaycastResult
import net.asd.union.utils.rotation.RotationUtils.currentRotation
import net.asd.union.utils.timing.MSTimer
import net.minecraft.block.BlockAir
import net.minecraft.entity.Entity
import net.minecraft.network.Packet
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.network.play.client.C0BPacketEntityAction.Action.*
import net.minecraft.network.play.client.C0FPacketConfirmTransaction
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S27PacketExplosion
import net.minecraft.network.play.server.S32PacketConfirmTransaction
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing.DOWN
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

internal class SourceVelocitySupport(
    private val owner: Module,
    private val timeoutValueName: String,
    private val sourceDetector: (Entity?) -> Boolean,
) : MinecraftInstance, Listenable {

    override val parent: Listenable?
        get() = owner

    private val mode by choices(
        "Mode",
        arrayOf(
            "Simple", "AAC", "AACPush", "AACZero", "AACv4",
            "Reverse", "SmoothReverse", "Jump", "Glitch", "Legit",
            "GhostBlock", "Vulcan", "S32Packet", "MatrixReduce",
            "IntaveReduce", "Delay", "GrimC03", "Hypixel", "HypixelAir",
            "Click", "BlocksMC"
        ),
        "Simple"
    )

    private val horizontal by float("Horizontal", 0F, 0F..1F) { mode in arrayOf("Simple", "AAC", "Legit") }
    private val vertical by float("Vertical", 0F, 0F..1F) { mode in arrayOf("Simple", "Legit") }

    private val reverseStrength by float("ReverseStrength", 1F, 0.1F..1F) { mode == "Reverse" }
    private val reverse2Strength by float("SmoothReverseStrength", 0.05F, 0.02F..0.1F) { mode == "SmoothReverse" }

    private val onLook by boolean("onLook", false) { mode in arrayOf("Reverse", "SmoothReverse") }
    private val range by float("Range", 3.0F, 1F..5.0F) { onLook && mode in arrayOf("Reverse", "SmoothReverse") }
    private val maxAngleDifference by float("MaxAngleDifference", 45.0f, 5.0f..90f) { onLook && mode in arrayOf("Reverse", "SmoothReverse") }

    private val aacPushXZReducer by float("AACPushXZReducer", 2F, 1F..3F) { mode == "AACPush" }
    private val aacPushYReducer by boolean("AACPushYReducer", true) { mode == "AACPush" }

    private val aacv4MotionReducer by float("AACv4MotionReducer", 0.62F, 0F..1F) { mode == "AACv4" }

    private val legitDisableInAir by boolean("DisableInAir", true) { mode == "Legit" }

    private val chance by int("Chance", 100, 0..100) { mode == "Jump" || mode == "Legit" }

    private val jumpCooldownMode by choices("JumpCooldownMode", arrayOf("Ticks", "ReceivedHits"), "Ticks") { mode == "Jump" }
    private val ticksUntilJump by int("TicksUntilJump", 4, 0..20) { jumpCooldownMode == "Ticks" && mode == "Jump" }
    private val hitsUntilJump by int("ReceivedHitsUntilJump", 2, 0..5) { jumpCooldownMode == "ReceivedHits" && mode == "Jump" }

    private val maxHurtTime: IntegerValue = object : IntegerValue("MaxHurtTime", 9, 1..10) {
        override fun isSupported() = mode == "GhostBlock"
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtLeast(minHurtTime.get())
    }

    private val minHurtTime: IntegerValue = object : IntegerValue("MinHurtTime", 1, 1..10) {
        override fun isSupported() = mode == "GhostBlock" && !maxHurtTime.isMinimal()
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceIn(0, maxHurtTime.get())
    }

    private val spoofDelay by int("SpoofDelay", 500, 0..5000) { mode == "Delay" }
    var delayMode = false

    private val reduceFactor by float("Factor", 0.6f, 0.6f..1f) { mode == "IntaveReduce" }
    private val hurtTime by int("HurtTime", 9, 1..10) { mode == "IntaveReduce" }

    private val pauseOnExplosion by boolean("PauseOnExplosion", true)
    private val ticksToPause by int("TicksToPause", 20, 1..50) { pauseOnExplosion }

    private val timeout by int(timeoutValueName, 500, 100..2000)

    private val limitMaxMotionValue = boolean("LimitMaxMotion", false) { mode == "Simple" }
    private val maxXZMotion by float("MaxXZMotion", 0.4f, 0f..1.9f) { limitMaxMotionValue.isActive() }
    private val maxYMotion by float("MaxYMotion", 0.36f, 0f..0.46f) { limitMaxMotionValue.isActive() }

    private val clicks by intRange("Clicks", 3..5, 1..20) { mode == "Click" }
    private val hurtTimeToClick by int("HurtTimeToClick", 10, 0..10) { mode == "Click" }
    private val whenFacingEnemyOnly by boolean("WhenFacingEnemyOnly", true) { mode == "Click" }
    private val ignoreBlocking by boolean("IgnoreBlocking", false) { mode == "Click" }
    private val clickRange by float("ClickRange", 3f, 1f..6f) { mode == "Click" }
    private val swingMode by choices("SwingMode", arrayOf("Off", "Normal", "Packet"), "Normal") { mode == "Click" }

    private val velocityTimer = MSTimer()
    private var hasReceivedVelocity = false
    private var reverseHurt = false
    private var jump = false
    private var limitUntilJump = 0
    private var intaveTick = 0
    private var lastAttackTime = 0L
    private var intaveDamageTick = 0
    private val packets = LinkedHashMap<Packet<*>, Long>()
    private var timerTicks = 0
    private var transaction = false
    private var absorbedVelocity = false
    private var pauseTicks = 0
    private var blockNextKnockback = false

    val tag
        get() = if (mode == "Simple" || mode == "Legit") {
            val horizontalPercentage = (horizontal * 100).toInt()
            val verticalPercentage = (vertical * 100).toInt()
            "$horizontalPercentage% $verticalPercentage%"
        } else mode

    init {
        owner.addConfigurable(this)
    }

    fun onEnable() {
        reset()
    }

    fun onDisable() {
        pauseTicks = 0
        mc.thePlayer?.speedInAir = 0.02F
        timerTicks = 0
        reset()
    }

    fun onSourceHit() {
        hasReceivedVelocity = true
        velocityTimer.reset()
        if (shouldCancelKnockback()) {
            blockNextKnockback = true
        }
    }

    fun shouldCancelKnockback() = handleEvents() && (mode == "Cancel" || (mode == "Simple" && horizontal == 0f && vertical == 0f))

    fun markKnockbackBlock() {
        if (shouldCancelKnockback()) {
            blockNextKnockback = true
            velocityTimer.reset()
        }
    }

    fun isKnockbackBlockArmed(): Boolean = blockNextKnockback

    fun consumeKnockbackBlock(): Boolean {
        if (!blockNextKnockback) return false
        blockNextKnockback = false
        return true
    }

    fun onMinecraftDamageSource(source: net.minecraft.util.DamageSource, directSource: Entity?, indirectSource: Entity?): Boolean {
        if (!handleEvents()) {
            return false
        }

        if (!sourceDetector(directSource) && !sourceDetector(indirectSource)) {
            return false
        }

        onSourceHit()
        markKnockbackBlock()
        return true
    }

    fun shouldBlockIncomingVelocity(stage: String, motionX: Double, motionY: Double, motionZ: Double): Boolean {
        if (!shouldCancelKnockback()) {
            return false
        }

        return isSourceCurrentlyNear(stage, motionX, motionY, motionZ)
    }

    private fun isSourceCurrentlyNear(stage: String, motionX: Double, motionY: Double, motionZ: Double): Boolean {
        val player = mc.thePlayer ?: return false
        val world = mc.theWorld ?: return false

        for (entity in java.util.ArrayList(world.loadedEntityList)) {
            if (!sourceDetector(entity)) {
                continue
            }

            val distanceSq = entity.getDistanceSqToEntity(player)
            if (distanceSq > 16.0) {
                continue
            }

            val dx = player.posX - entity.posX
            val dy = player.posY + player.eyeHeight / 2.0 - entity.posY
            val dz = player.posZ - entity.posZ
            val dot = entity.motionX * dx + entity.motionY * dy + entity.motionZ * dz

            if (dot > 0 || distanceSq < 4.0) {
                val projectedBox = entity.entityBoundingBox.addCoord(entity.motionX * 5.0, entity.motionY * 5.0, entity.motionZ * 5.0).expand(0.5, 0.5, 0.5)
                if (projectedBox.intersectsWith(player.entityBoundingBox)) {
                    return true
                }
            }
        }

        return false
    }

    val onUpdate = handler<UpdateEvent> {
        val thePlayer = mc.thePlayer ?: return@handler

        if (thePlayer.isInLiquid || thePlayer.isInWeb || thePlayer.isDead)
            return@handler

        if (hasReceivedVelocity && velocityTimer.hasTimePassed(timeout.toLong())) {
            hasReceivedVelocity = false
        }

        if (blockNextKnockback && velocityTimer.hasTimePassed(250L)) {
            blockNextKnockback = false
        }

        when (mode.lowercase()) {
            "glitch" -> {
                thePlayer.noClip = hasReceivedVelocity

                if (thePlayer.hurtTime == 7)
                    thePlayer.motionY = 0.4

                hasReceivedVelocity = false
            }

            "reverse" -> {
                val nearbyEntity = getNearestEntityInRange()

                if (!hasReceivedVelocity)
                    return@handler

                if (nearbyEntity != null) {
                    if (!thePlayer.onGround) {
                        if (onLook && !isLookingOnEntities(nearbyEntity, maxAngleDifference.toDouble())) {
                            return@handler
                        }

                        speed *= reverseStrength
                    } else if (velocityTimer.hasTimePassed(80))
                        hasReceivedVelocity = false
                }
            }

            "smoothreverse" -> {
                val nearbyEntity = getNearestEntityInRange()

                if (hasReceivedVelocity) {
                    if (nearbyEntity == null) {
                        thePlayer.speedInAir = 0.02F
                        reverseHurt = false
                    } else {
                        if (onLook && !isLookingOnEntities(nearbyEntity, maxAngleDifference.toDouble())) {
                            hasReceivedVelocity = false
                            thePlayer.speedInAir = 0.02F
                            reverseHurt = false
                        } else {
                            if (thePlayer.hurtTime > 0) {
                                reverseHurt = true
                            }

                            if (!thePlayer.onGround) {
                                thePlayer.speedInAir = if (reverseHurt) reverse2Strength else 0.02F
                            } else if (velocityTimer.hasTimePassed(80)) {
                                hasReceivedVelocity = false
                                thePlayer.speedInAir = 0.02F
                                reverseHurt = false
                            }
                        }
                    }
                }
            }

            "aac" -> if (hasReceivedVelocity && velocityTimer.hasTimePassed(80)) {
                thePlayer.motionX *= horizontal
                thePlayer.motionZ *= horizontal
                hasReceivedVelocity = false
            }

            "aacv4" ->
                if (thePlayer.hurtTime > 0 && !thePlayer.onGround) {
                    val reduce = aacv4MotionReducer
                    thePlayer.motionX *= reduce
                    thePlayer.motionZ *= reduce
                }

            "aacpush" -> {
                if (jump) {
                    if (thePlayer.onGround)
                        jump = false
                } else {
                    if (thePlayer.hurtTime > 0 && thePlayer.motionX != 0.0 && thePlayer.motionZ != 0.0)
                        thePlayer.onGround = true

                    if (thePlayer.hurtResistantTime > 0 && aacPushYReducer)
                        thePlayer.motionY -= 0.014999993
                }

                if (thePlayer.hurtResistantTime >= 19) {
                    val reduce = aacPushXZReducer

                    thePlayer.motionX /= reduce
                    thePlayer.motionZ /= reduce
                }
            }

            "aaczero" ->
                if (thePlayer.hurtTime > 0) {
                    if (!hasReceivedVelocity || thePlayer.onGround || thePlayer.fallDistance > 2F)
                        return@handler

                    thePlayer.motionY -= 1.0
                    thePlayer.isAirBorne = true
                    thePlayer.onGround = true
                } else
                    hasReceivedVelocity = false

            "legit" -> {
                if (legitDisableInAir && !isOnGround(0.5))
                    return@handler

                if (mc.thePlayer.maxHurtResistantTime != mc.thePlayer.hurtResistantTime || mc.thePlayer.maxHurtResistantTime == 0)
                    return@handler

                if (nextInt(endExclusive = 100) < chance) {
                    val horizontal = horizontal / 100f
                    val vertical = vertical / 100f

                    thePlayer.motionX *= horizontal.toDouble()
                    thePlayer.motionZ *= horizontal.toDouble()
                    thePlayer.motionY *= vertical.toDouble()
                }
            }
        }
    }

    val onGameTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler

        if (mode != "GrimC03")
            return@handler

        if (timerTicks > 0 && mc.timer.timerSpeed <= 1) {
            val timerSpeed = 0.8f + (0.2f * (20 - timerTicks) / 20)
            mc.timer.timerSpeed = timerSpeed.coerceAtMost(1f)
            --timerTicks
        } else if (mc.timer.timerSpeed <= 1) {
            mc.timer.timerSpeed = 1f
        }

        if (hasReceivedVelocity) {
            val pos = BlockPos(player.posX, player.posY, player.posZ)

            if (checkAir(pos))
                hasReceivedVelocity = false
        }
    }

    val onDelayPacket = handler<PacketEvent> { event ->
        val packet = event.packet

        if (event.isCancelled || mode != "Delay")
            return@handler

        if (!hasReceivedVelocity && !blockNextKnockback)
            return@handler

        if (packet is S32PacketConfirmTransaction || packet is S12PacketEntityVelocity) {
            event.cancelEvent()

            synchronized(packets) {
                packets[packet] = System.currentTimeMillis()
            }
        }

        delayMode = true
    }

    val onWorld = handler<WorldEvent> {
        packets.clear()
        reset()
    }

    val onGameLoop = handler<GameLoopEvent> {
        if (mode == "Delay")
            sendPacketsByOrder(false)
    }

    val onJump = handler<JumpEvent> { event ->
        val thePlayer = mc.thePlayer

        if (thePlayer == null || thePlayer.isInLiquid || thePlayer.isInWeb)
            return@handler

        when (mode.lowercase()) {
            "aacpush" -> {
                jump = true

                if (!thePlayer.isCollidedVertically)
                    event.cancelEvent()
            }

            "aaczero" ->
                if (thePlayer.hurtTime > 0)
                    event.cancelEvent()
        }
    }

    val onStrafe = handler<StrafeEvent> {
        val player = mc.thePlayer ?: return@handler

        if (mode == "Jump" && hasReceivedVelocity) {
            if (!player.isJumping && nextInt(endExclusive = 100) < chance && shouldJump() && player.isSprinting && player.onGround && player.hurtTime == 9) {
                player.tryJump()
                limitUntilJump = 0
            }
            hasReceivedVelocity = false
            return@handler
        }

        when (jumpCooldownMode.lowercase()) {
            "ticks" -> limitUntilJump++
            "receivedhits" -> if (player.hurtTime == 9) limitUntilJump++
        }
    }

    val onBlockBB = handler<BlockBBEvent> { event ->
        val player = mc.thePlayer ?: return@handler

        if (mode == "GhostBlock") {
            if (hasReceivedVelocity) {
                if (player.hurtTime in minHurtTime.get()..maxHurtTime.get()) {
                    if (event.block is BlockAir && event.y == mc.thePlayer.posY.toInt() + 1) {
                        event.boundingBox = AxisAlignedBB(
                            event.x.toDouble(),
                            event.y.toDouble(),
                            event.z.toDouble(),
                            event.x + 1.0,
                            event.y + 1.0,
                            event.z + 1.0
                        )
                    }
                } else if (player.hurtTime == 0) {
                    hasReceivedVelocity = false
                }
            }
        }
    }

    val onAttack = handler<AttackEvent> {
        val player = mc.thePlayer ?: return@handler

        if (mode != "IntaveReduce" || !hasReceivedVelocity) return@handler

        if (player.hurtTime == hurtTime && System.currentTimeMillis() - lastAttackTime <= 8000) {
            player.motionX *= reduceFactor
            player.motionZ *= reduceFactor
        }

        lastAttackTime = System.currentTimeMillis()
    }

    private fun checkAir(blockPos: BlockPos): Boolean {
        val world = mc.theWorld ?: return false

        if (!world.isAirBlock(blockPos)) {
            return false
        }

        timerTicks = 20

        sendPackets(
            C03PacketPlayer(true),
            C07PacketPlayerDigging(STOP_DESTROY_BLOCK, blockPos, DOWN)
        )

        world.setBlockToAir(blockPos)

        return true
    }

    private fun getDirection(): Double {
        var moveYaw = mc.thePlayer.rotationYaw
        when {
            mc.thePlayer.moveForward != 0f && mc.thePlayer.moveStrafing == 0f -> {
                moveYaw += if (mc.thePlayer.moveForward > 0) 0 else 180
            }

            mc.thePlayer.moveForward != 0f && mc.thePlayer.moveStrafing != 0f -> {
                if (mc.thePlayer.moveForward > 0) moveYaw += if (mc.thePlayer.moveStrafing > 0) -45 else 45 else moveYaw -= if (mc.thePlayer.moveStrafing > 0) -45 else 45
                moveYaw += if (mc.thePlayer.moveForward > 0) 0 else 180
            }

            mc.thePlayer.moveStrafing != 0f && mc.thePlayer.moveForward == 0f -> {
                moveYaw += if (mc.thePlayer.moveStrafing > 0) -90 else 90
            }
        }
        return Math.floorMod(moveYaw.toInt(), 360).toDouble()
    }

    val onPacket = handler<PacketEvent>(priority = 1) { event ->
        val thePlayer = mc.thePlayer ?: return@handler
        val packet = event.packet

        if (!owner.handleEvents())
            return@handler

        if (pauseTicks > 0) {
            pauseTicks--
            return@handler
        }

        if (!hasReceivedVelocity && !blockNextKnockback)
            return@handler

        if (event.isCancelled)
            return@handler

        if ((packet is S12PacketEntityVelocity && thePlayer.entityId == packet.entityID && packet.motionY > 0 && (packet.motionX != 0 || packet.motionZ != 0))
            || (packet is S27PacketExplosion && (thePlayer.motionY + packet.field_149153_g) > 0.0
                    && ((thePlayer.motionX + packet.field_149152_f) != 0.0 || (thePlayer.motionZ + packet.field_149159_h) != 0.0))
        ) {
            velocityTimer.reset()

            if (pauseOnExplosion && packet is S27PacketExplosion && (thePlayer.motionY + packet.field_149153_g) > 0.0
                && ((thePlayer.motionX + packet.field_149152_f) != 0.0 || (thePlayer.motionZ + packet.field_149159_h) != 0.0)
            ) {
                pauseTicks = ticksToPause
            }

            when (mode.lowercase()) {
                "simple" -> handleVelocity(event)

                "aac", "reverse", "smoothreverse", "aaczero", "ghostblock", "intavereduce" -> hasReceivedVelocity = true

                "jump" -> {
                    var packetDirection = 0.0
                    when (packet) {
                        is S12PacketEntityVelocity -> {
                            if (packet.entityID != thePlayer.entityId) return@handler

                            val motionX = packet.motionX.toDouble()
                            val motionZ = packet.motionZ.toDouble()

                            packetDirection = atan2(motionX, motionZ)
                        }

                        is S27PacketExplosion -> {
                            val motionX = thePlayer.motionX + packet.field_149152_f
                            val motionZ = thePlayer.motionZ + packet.field_149159_h

                            packetDirection = atan2(motionX, motionZ)
                        }
                    }
                    val degreePlayer = getDirection()
                    val degreePacket = Math.floorMod(packetDirection.toDegrees().toInt(), 360).toDouble()
                    var angle = abs(degreePacket + degreePlayer)
                    val threshold = 120.0
                    angle = Math.floorMod(angle.toInt(), 360).toDouble()
                    val inRange = angle in 180 - threshold / 2..180 + threshold / 2
                    if (inRange)
                        hasReceivedVelocity = true
                }

                "glitch" -> {
                    if (!thePlayer.onGround)
                        return@handler

                    hasReceivedVelocity = true
                    event.cancelEvent()
                }

                "matrixreduce" -> {
                    if (packet is S12PacketEntityVelocity && packet.entityID == thePlayer.entityId) {
                        packet.motionX = (packet.getMotionX() * 0.33).toInt()
                        packet.motionZ = (packet.getMotionZ() * 0.33).toInt()

                        if (thePlayer.onGround) {
                            packet.motionX = (packet.getMotionX() * 0.86).toInt()
                            packet.motionZ = (packet.getMotionZ() * 0.86).toInt()
                        }
                    }
                }

                "blocksmc" -> {
                    if (packet is S12PacketEntityVelocity && packet.entityID == thePlayer.entityId) {
                        hasReceivedVelocity = true
                        event.cancelEvent()

                        sendPacket(C0BPacketEntityAction(thePlayer, START_SNEAKING))
                        sendPacket(C0BPacketEntityAction(thePlayer, STOP_SNEAKING))
                    }
                }

                "grimc03" -> {
                    if (thePlayer.isMoving) {
                        hasReceivedVelocity = true
                        event.cancelEvent()
                    }
                }

                "hypixel" -> {
                    hasReceivedVelocity = true
                    if (!thePlayer.onGround) {
                        if (!absorbedVelocity) {
                            event.cancelEvent()
                            absorbedVelocity = true
                            return@handler
                        }
                    }

                    if (packet is S12PacketEntityVelocity && packet.entityID == thePlayer.entityId) {
                        packet.motionX = (thePlayer.motionX * 8000).toInt()
                        packet.motionZ = (thePlayer.motionZ * 8000).toInt()
                    }
                }

                "hypixelair" -> {
                    hasReceivedVelocity = true
                    event.cancelEvent()
                }

                "vulcan" -> {
                    event.cancelEvent()
                }

                "s32packet" -> {
                    hasReceivedVelocity = true
                    event.cancelEvent()
                }
            }
        }

        if (mode == "BlocksMC" && hasReceivedVelocity) {
            if (packet is C0BPacketEntityAction) {
                hasReceivedVelocity = false
                event.cancelEvent()
            }
        }

        if (mode == "Vulcan") {
            if (packet is S32PacketConfirmTransaction) {
                event.cancelEvent()
                sendPacket(
                    C0FPacketConfirmTransaction(
                        if (transaction) 1 else -1,
                        if (transaction) -1 else 1,
                        transaction
                    ), false
                )
                transaction = !transaction
            }
        }

        if (mode == "S32Packet" && packet is S32PacketConfirmTransaction) {
            if (!hasReceivedVelocity)
                return@handler

            event.cancelEvent()
            hasReceivedVelocity = false
        }
    }

    private fun sendPacketsByOrder(velocity: Boolean) {
        synchronized(packets) {
            packets.entries.removeAll { (packet, timestamp) ->
                if (velocity || timestamp <= System.currentTimeMillis() - spoofDelay) {
                    PacketUtils.schedulePacketProcess(packet)
                    true
                } else false
            }
        }
    }

    private fun reset() {
        sendPacketsByOrder(true)

        packets.clear()
        hasReceivedVelocity = false
        reverseHurt = false
        jump = false
        limitUntilJump = 0
        intaveTick = 0
        lastAttackTime = 0L
        intaveDamageTick = 0
        timerTicks = 0
        transaction = false
        absorbedVelocity = false
        pauseTicks = 0
        blockNextKnockback = false
        delayMode = false
    }

    private fun shouldJump() = when (jumpCooldownMode.lowercase()) {
        "ticks" -> limitUntilJump >= ticksUntilJump
        "receivedhits" -> limitUntilJump >= hitsUntilJump
        else -> false
    }

    private fun handleVelocity(event: PacketEvent) {
        val packet = event.packet

        if (packet is S12PacketEntityVelocity) {
            event.cancelEvent()

            if (horizontal == 0f && vertical == 0f)
                return

            if (horizontal != 0f) {
                var motionX = packet.realMotionX
                var motionZ = packet.realMotionZ

                if (limitMaxMotionValue.get()) {
                    val distXZ = sqrt(motionX * motionX + motionZ * motionZ)

                    if (distXZ > maxXZMotion) {
                        val ratioXZ = maxXZMotion / distXZ

                        motionX *= ratioXZ
                        motionZ *= ratioXZ
                    }
                }

                mc.thePlayer.motionX = motionX * horizontal
                mc.thePlayer.motionZ = motionZ * horizontal
            }

            if (vertical != 0f) {
                var motionY = packet.realMotionY

                if (limitMaxMotionValue.get())
                    motionY = motionY.coerceAtMost(maxYMotion + 0.00075)

                mc.thePlayer.motionY = motionY * vertical
            }
        } else if (packet is S27PacketExplosion) {
            if (horizontal != 0f && vertical != 0f) {
                packet.field_149152_f = 0f
                packet.field_149153_g = 0f
                packet.field_149159_h = 0f

                return
            }

            packet.field_149152_f *= horizontal
            packet.field_149153_g *= vertical
            packet.field_149159_h *= horizontal

            if (limitMaxMotionValue.get()) {
                val distXZ = sqrt(packet.field_149152_f * packet.field_149152_f + packet.field_149159_h * packet.field_149159_h)
                val distY = packet.field_149153_g
                val maxYMotion = maxYMotion + 0.00075f

                if (distXZ > maxXZMotion) {
                    val ratioXZ = maxXZMotion / distXZ

                    packet.field_149152_f *= ratioXZ
                    packet.field_149159_h *= ratioXZ
                }

                if (distY > maxYMotion) {
                    packet.field_149153_g *= maxYMotion / distY
                }
            }
        }
    }

    private fun getNearestEntityInRange(range: Float = this.range): Entity? {
        val player = mc.thePlayer ?: return null

        return mc.theWorld.loadedEntityList.filter {
            isSelected(it, true) && player.getDistanceToEntityBox(it) <= range
        }.minByOrNull { player.getDistanceToEntityBox(it) }
    }
}