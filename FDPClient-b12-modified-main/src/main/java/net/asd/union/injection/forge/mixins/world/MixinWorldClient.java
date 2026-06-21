/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.world;

import net.asd.union.features.module.modules.visual.TrueSight;
import net.asd.union.handler.sessiontabs.TabSimulationThread;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldClient.class)
public class MixinWorldClient {

    /**
     * Block void fog particles from simulation threads — they would access
     * mc.thePlayer and add particles to the foreground EffectRenderer.
     */
    @Inject(method = "doVoidFogParticles", at = @At("HEAD"), cancellable = true)
    private void fdp$skipVoidFogParticlesFromSimThread(int x, int y, int z, CallbackInfo ci) {
        if (Thread.currentThread() instanceof TabSimulationThread) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "doVoidFogParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;randomDisplayTick(Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V", shift = At.Shift.AFTER), ordinal = 0)
    private boolean handleBarriers(final boolean flag) {
        final TrueSight trueSight = TrueSight.INSTANCE;
        return flag || trueSight.handleEvents() && trueSight.getBarriers();
    }

    /**
     * Block sounds from simulation threads — they would play through the
     * foreground tab's SoundHandler.
     */
    @Inject(method = "playSound(DDDLjava/lang/String;FFZ)V", at = @At("HEAD"), cancellable = true)
    private void fdp$skipPlaySoundFromSimThread(double x, double y, double z, String soundName, float volume, float pitch, boolean distanceDelay, CallbackInfo ci) {
        if (Thread.currentThread() instanceof TabSimulationThread) {
            ci.cancel();
        }
    }

    /**
     * Block playSoundAtPos from simulation threads.
     */
    @Inject(method = "playSoundAtPos", at = @At("HEAD"), cancellable = true)
    private void fdp$skipPlaySoundAtPosFromSimThread(net.minecraft.util.BlockPos pos, String soundName, float volume, float pitch, boolean distanceDelay, CallbackInfo ci) {
        if (Thread.currentThread() instanceof TabSimulationThread) {
            ci.cancel();
        }
    }

    /**
     * Block the SoundHandler.playSound call for minecart sounds from sim threads.
     * This also catches the MovingSoundMinecart created in spawnEntityInWorld.
     */
    @Redirect(method = "spawnEntityInWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/audio/SoundHandler;playSound(Lnet/minecraft/client/audio/ISound;)V"))
    private void fdp$skipSoundHandlerPlayFromSimThread(net.minecraft.client.audio.SoundHandler handler, net.minecraft.client.audio.ISound sound) {
        if (Thread.currentThread() instanceof TabSimulationThread) {
            return;
        }
        handler.playSound(sound);
    }

    /**
     * Block firework particles from simulation threads — they would add
     * effects to the foreground tab's EffectRenderer.
     */
    @Inject(method = "makeFireworks", at = @At("HEAD"), cancellable = true)
    private void fdp$skipFireworksFromSimThread(double x, double y, double z, double motionX, double motionY, double motionZ, net.minecraft.nbt.NBTTagCompound compound, CallbackInfo ci) {
        if (Thread.currentThread() instanceof TabSimulationThread) {
            ci.cancel();
        }
    }

    /**
     * Fix getEntityByID: on a simulation thread, don't return mc.thePlayer
     * (which belongs to the foreground tab). The vanilla code does:
     *   id == this.mc.thePlayer.getEntityId() ? this.mc.thePlayer : super.getEntityByID(id)
     *
     * The field redirect below returns null for mc.thePlayer on sim threads,
     * but that causes NPE when calling .getEntityId() on null.
     *
     * This HEAD inject handles the sim thread case by directly looking up
     * the entity from the world's entity list, bypassing the mc.thePlayer check.
     */
    @Inject(method = "getEntityByID", at = @At("HEAD"), cancellable = true)
    private void fdp$safeGetEntityByIDForSimThread(int id, CallbackInfoReturnable<net.minecraft.entity.Entity> cir) {
        if (Thread.currentThread() instanceof TabSimulationThread) {
            // On sim threads, look up the entity directly from the loaded entity list.
            // This bypasses the mc.thePlayer comparison that causes NPE.
            for (net.minecraft.entity.Entity entity : ((WorldClient)(Object)this).loadedEntityList) {
                if (entity != null && entity.getEntityId() == id) {
                    cir.setReturnValue(entity);
                    return;
                }
            }
            cir.setReturnValue(null);
            return;
        }
    }

    // Safety net: on sim threads, return null for mc.thePlayer access in getEntityByID.
    // The HEAD inject above should handle sim threads before this is reached.
    @Redirect(method = "getEntityByID", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;field_71439_g:Lnet/minecraft/client/entity/EntityPlayerSP;"))
    private net.minecraft.client.entity.EntityPlayerSP fdp$dontReturnForegroundPlayer(Minecraft mcInstance) {
        if (Thread.currentThread() instanceof TabSimulationThread) {
            return null;
        }
        return mcInstance.thePlayer;
    }
}
