/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.render;

import net.asd.union.features.module.modules.visual.FreeCam;
import net.asd.union.utils.performance.ChunkOptimizer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {

    @Shadow
    private boolean field_147595_R;

    @Shadow
    private double field_174997_H;

    @Shadow
    private double field_174998_I;

    @Shadow
    private double field_174999_J;

    @Shadow
    private double field_175000_K;

    @Shadow
    private double field_174994_L;

    @Shadow
    private ChunkRenderDispatcher field_174995_M;

    @Shadow
    private ViewFrustum field_175008_n;

    @Redirect(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;isPlayerSleeping()Z"))
    private boolean injectFreeCam(EntityLivingBase instance) {
        return FreeCam.INSTANCE.renderPlayerFromAllPerspectives(instance);
    }

    @Redirect(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderManager;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;DDD)Z"))
    private boolean injectFreeCamB(RenderManager instance, Entity entity, ICamera camera, double x, double y, double z) {
        return FreeCam.INSTANCE.handleEvents() || instance.shouldRender(entity, camera, x, y, z);
    }

    @Inject(method = "setupTerrain", at = @At("HEAD"))
    private void fdp$optimizeSetupTerrain(Entity viewEntity, double partialTicks, ICamera camera, int frameCount, boolean playerSpectator, CallbackInfo ci) {
        if (!ChunkOptimizer.INSTANCE.getEnabled()) {
            return;
        }

        final boolean posChanged =
            viewEntity.posX != field_174997_H ||
                viewEntity.posY != field_174998_I ||
                viewEntity.posZ != field_174999_J;

        if (!posChanged && !field_147595_R) {
            final boolean rotChanged =
                viewEntity.rotationPitch != field_175000_K ||
                    viewEntity.rotationYaw != field_174994_L;

            if (rotChanged) {
                final boolean passesThreshold =
                    ChunkOptimizer.INSTANCE.shouldRefreshBFS(viewEntity.rotationYaw, viewEntity.rotationPitch);

                if (!passesThreshold) {
                    field_175000_K = viewEntity.rotationPitch;
                    field_174994_L = viewEntity.rotationYaw;
                }
            }
        }
    }

    @Redirect(
        method = "setupTerrain",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;updateChunkNow(Lnet/minecraft/client/renderer/chunk/RenderChunk;)Z"
        )
    )
    private boolean fdp$asyncNearChunks(ChunkRenderDispatcher dispatcher, RenderChunk renderChunk) {
        if (ChunkOptimizer.INSTANCE.shouldCompileAsync(renderChunk)) {
            return dispatcher.updateChunkLater(renderChunk);
        }

        return dispatcher.updateChunkNow(renderChunk);
    }

    @Inject(method = "loadRenderers", at = @At("HEAD"))
    private void fdp$resetChunkOptimizer(CallbackInfo ci) {
        ChunkOptimizer.INSTANCE.reset();
    }

    @Redirect(
        method = "setWorldAndLoadRenderers",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;loadRenderers()V")
    )
    private void fdp$conditionalLoadRenderers(RenderGlobal self) {
        if (ChunkOptimizer.INSTANCE.getSkipNextLoadRenderers()) {
            ChunkOptimizer.INSTANCE.setSkipNextLoadRenderers(false);
            ChunkOptimizer.INSTANCE.reset();
            field_147595_R = true;
            return;
        }

        self.loadRenderers();
    }

    @Inject(method = "setupTerrain", at = @At("RETURN"))
    private void fdp$precompile360(Entity viewEntity, double partialTicks, ICamera camera, int frameCount, boolean playerSpectator, CallbackInfo ci) {
        ChunkOptimizer.INSTANCE.precompileNearbyChunks(field_175008_n, field_174995_M);
    }
}
