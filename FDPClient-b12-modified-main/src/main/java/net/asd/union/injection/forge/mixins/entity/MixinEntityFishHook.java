/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.entity;

import net.asd.union.features.module.modules.combat.RodVelocity;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.projectile.EntityFishHook;
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

@Mixin(EntityFishHook.class)
@SideOnly(Side.CLIENT)
public abstract class MixinEntityFishHook {

    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void fdp$directHookCollisionCheck(CallbackInfo ci) {
        if (!RodVelocity.INSTANCE.handleEvents()) {
            return;
        }

        EntityPlayerSP player = mc.thePlayer;
        EntityFishHook hook = (EntityFishHook) (Object) this;

        if (player == null || hook.isDead) {
            return;
        }

        AxisAlignedBB playerBox = player.getEntityBoundingBox().expand(0.45D, 0.45D, 0.45D);
        Vec3 start = new Vec3(hook.posX, hook.posY, hook.posZ);
        Vec3 end = new Vec3(hook.posX + hook.motionX, hook.posY + hook.motionY, hook.posZ + hook.motionZ);
        MovingObjectPosition intercept = playerBox.calculateIntercept(start, end);
        double distanceSq = hook.getDistanceSqToEntity(player);
        boolean caughtPlayer = hook.caughtEntity == player;
        boolean directHit = caughtPlayer || intercept != null || distanceSq <= 6.25D;

        if (directHit) {
            RodVelocity.INSTANCE.onRodHit();
            RodVelocity.INSTANCE.markKnockbackBlock();
        }
    }
}