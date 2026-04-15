/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.entity;

import net.asd.union.features.module.modules.combat.ProjectileVelocity;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.asd.union.utils.client.MinecraftInstance.mc;

@Mixin(EntityArrow.class)
@SideOnly(Side.CLIENT)
public abstract class MixinEntityArrow {

    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void fdp$directArrowCollisionCheck(CallbackInfo ci) {
        if (!ProjectileVelocity.INSTANCE.handleEvents()) {
            return;
        }

        EntityPlayerSP player = mc.thePlayer;
        EntityArrow arrow = (EntityArrow) (Object) this;

        if (player == null || arrow.isDead) {
            return;
        }

        AxisAlignedBB playerBox = player.getEntityBoundingBox().expand(0.45D, 0.45D, 0.45D);
        Vec3 start = new Vec3(arrow.posX, arrow.posY, arrow.posZ);
        Vec3 end = new Vec3(arrow.posX + arrow.motionX, arrow.posY + arrow.motionY, arrow.posZ + arrow.motionZ);
        MovingObjectPosition intercept = playerBox.calculateIntercept(start, end);
        double distanceSq = arrow.getDistanceSqToEntity(player);
        boolean directHit = intercept != null || distanceSq <= 6.25D;

        if (directHit) {
            ProjectileVelocity.INSTANCE.onProjectileHit();
            ProjectileVelocity.INSTANCE.markKnockbackBlock();
        }
    }
}