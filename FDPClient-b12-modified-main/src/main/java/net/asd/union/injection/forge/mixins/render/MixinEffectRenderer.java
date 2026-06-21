/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.render;

import net.asd.union.handler.sessiontabs.TabSimulationThread;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityParticleEmitter;
import net.minecraft.client.particle.EntityFX;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

@Mixin(EffectRenderer.class)
@SideOnly(Side.CLIENT)
public abstract class MixinEffectRenderer {

    @Shadow
    protected abstract void updateEffectLayer(int layer);

    @Shadow
    private List<EntityParticleEmitter> particleEmitters;

    /**
     * Block particle additions from simulation threads.
     * The EffectRenderer is a singleton shared across all tabs.
     */
    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void fdp$skipParticlesFromSimThread(EntityFX entityFX, CallbackInfo ci) {
        if (Thread.currentThread() instanceof TabSimulationThread) {
            ci.cancel();
        }
    }

    /**
     * @author Mojang
     * @author Marco
     */
    @Overwrite
    public void updateEffects() {
        try {
            for (int i = 0; i < 4; ++i)
                updateEffectLayer(i);

            for (final Iterator<EntityParticleEmitter> it = particleEmitters.iterator(); it.hasNext(); ) {
                final EntityParticleEmitter entityParticleEmitter = it.next();

                entityParticleEmitter.onUpdate();

                if (entityParticleEmitter.isDead)
                    it.remove();
            }
        } catch(final ConcurrentModificationException ignored) {
        }
    }
}