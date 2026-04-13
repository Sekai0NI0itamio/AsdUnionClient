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
import net.asd.union.utils.client.*
import net.asd.union.utils.extensions.*
import net.asd.union.utils.timing.MSTimer
import net.minecraft.entity.projectile.EntityArrow
import net.minecraft.entity.projectile.EntityPotion
import net.minecraft.entity.projectile.EntitySnowball
import net.minecraft.entity.projectile.EntityEgg
import net.minecraft.entity.projectile.EntityFishHook
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S27PacketExplosion
import kotlin.math.sqrt

object ProjectileVelocity : Module("ProjectileVelocity", Category.COMBAT, hideModule = false) {

    private val mode by choices("Mode", arrayOf("Simple", "Cancel"), "Simple")
    private val horizontal by float("Horizontal", 0F, 0F..1F) { mode == "Simple" }
    private val vertical by float("Vertical", 0F, 0F..1F) { mode == "Simple" }
    private val projectileTimeout by int("ProjectileTimeout", 500, 100..2000)

    private val velocityTimer = MSTimer()
    private var lastProjectileHitTime = 0L
    private var wasHitByProjectile = false

    override val tag
        get() = if (mode == "Simple") {
            val horizontalPercentage = (horizontal * 100).toInt()
            val verticalPercentage = (vertical * 100).toInt()
            "$horizontalPercentage% $verticalPercentage%"
        } else mode

    override fun onDisable() {
        wasHitByProjectile = false
        lastProjectileHitTime = 0L
    }

    val onUpdate = handler<UpdateEvent> {
        val thePlayer = mc.thePlayer ?: return@handler

        if (thePlayer.isInLiquid || thePlayer.isInWeb || thePlayer.isDead)
            return@handler

        if (wasHitByProjectile && velocityTimer.hasTimePassed(projectileTimeout.toLong())) {
            wasHitByProjectile = false
        }
    }

    val onPacket = handler<PacketEvent>(priority = 1) { event ->
        val thePlayer = mc.thePlayer ?: return@handler

        if (!handleEvents())
            return@handler

        if (event.isCancelled)
            return@handler

        val packet = event.packet

        if (packet is S12PacketEntityVelocity && thePlayer.entityId == packet.entityID) {
            if (!wasHitByProjectile) {
                return@handler
            }

            when (mode.lowercase()) {
                "simple" -> {
                    event.cancelEvent()

                    if (horizontal == 0f && vertical == 0f)
                        return@handler

                    if (horizontal != 0f) {
                        var motionX = packet.realMotionX
                        var motionZ = packet.realMotionZ

                        mc.thePlayer.motionX = motionX * horizontal
                        mc.thePlayer.motionZ = motionZ * horizontal
                    }

                    if (vertical != 0f) {
                        var motionY = packet.realMotionY
                        mc.thePlayer.motionY = motionY * vertical
                    }
                }

                "cancel" -> {
                    event.cancelEvent()
                }
            }
        } else if (packet is S27PacketExplosion) {
            if (!wasHitByProjectile) {
                return@handler
            }

            when (mode.lowercase()) {
                "simple" -> {
                    if (horizontal != 0f && vertical != 0f) {
                        packet.field_149152_f = 0f
                        packet.field_149153_g = 0f
                        packet.field_149159_h = 0f
                        return@handler
                    }

                    packet.field_149152_f *= horizontal
                    packet.field_149153_g *= vertical
                    packet.field_149159_h *= horizontal
                }

                "cancel" -> {
                    packet.field_149152_f = 0f
                    packet.field_149153_g = 0f
                    packet.field_149159_h = 0f
                }
            }
        }
    }

    val onWorld = handler<WorldEvent> {
        wasHitByProjectile = false
        lastProjectileHitTime = 0L
    }

    fun onProjectileHit() {
        wasHitByProjectile = true
        lastProjectileHitTime = System.currentTimeMillis()
        velocityTimer.reset()
    }
}
