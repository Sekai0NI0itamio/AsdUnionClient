/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.render;

import net.asd.union.features.module.modules.visual.FreeCam;
import net.asd.union.handler.sessiontabs.LiveTabRuntimeManager;
import net.asd.union.handler.sessiontabs.SessionRuntimeScope;
import net.asd.union.utils.performance.ChunkOptimizer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {

    @Shadow
    private net.minecraft.client.multiplayer.WorldClient theWorld;

    private boolean fdp$skipDetachedWorldAccess() {
        return SessionRuntimeScope.INSTANCE.isDetachedContextActive() ||
            Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread;
    }

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

    @Inject(method = "setWorldAndLoadRenderers", at = @At("HEAD"), cancellable = true)
    private void fdp$skipDetachedRendererWorldSwap(net.minecraft.client.multiplayer.WorldClient worldClientIn, CallbackInfo ci) {
        if (fdp$skipDetachedWorldAccess()) {
            // Still update theWorld reference so the RenderGlobal knows about
            // the background tab's world, but skip the expensive loadRenderers()
            // call since we're not rendering this world right now.
            theWorld = worldClientIn;
            ci.cancel();
        }
    }

    @Inject(method = {"markBlockRangeForRenderUpdate", "func_147585_a"}, at = @At("HEAD"), cancellable = true)
    private void fdp$skipDetachedBlockRangeUpdates(int x1, int y1, int z1, int x2, int y2, int z2, CallbackInfo ci) {
        if (fdp$skipDetachedWorldAccess()) {
            ci.cancel();
        }
    }

    @Inject(method = {"markBlockForUpdate", "func_174960_a"}, at = @At("HEAD"), cancellable = true)
    private void fdp$skipDetachedBlockUpdates(net.minecraft.util.BlockPos pos, CallbackInfo ci) {
        if (fdp$skipDetachedWorldAccess()) {
            ci.cancel();
        }
    }

    @Inject(method = {"notifyLightSet", "func_174959_b"}, at = @At("HEAD"), cancellable = true)
    private void fdp$skipDetachedLightUpdates(net.minecraft.util.BlockPos pos, CallbackInfo ci) {
        if (fdp$skipDetachedWorldAccess()) {
            ci.cancel();
        }
    }

    @Inject(method = {"onEntityAdded", "func_72703_a"}, at = @At("HEAD"), cancellable = true)
    private void fdp$skipDetachedEntityAdded(net.minecraft.entity.Entity entity, CallbackInfo ci) {
        if (fdp$skipDetachedWorldAccess()) {
            ci.cancel();
        }
    }

    @Inject(method = {"onEntityRemoved", "func_72709_b"}, at = @At("HEAD"), cancellable = true)
    private void fdp$skipDetachedEntityRemoved(net.minecraft.entity.Entity entity, CallbackInfo ci) {
        if (fdp$skipDetachedWorldAccess()) {
            ci.cancel();
        }
    }

    @Inject(method = {"sendBlockBreakProgress", "func_180441_b"}, at = @At("HEAD"), cancellable = true)
    private void fdp$skipDetachedBlockBreakProgress(int breakerId, net.minecraft.util.BlockPos pos, int progress, CallbackInfo ci) {
        if (fdp$skipDetachedWorldAccess()) {
            ci.cancel();
        }
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

    /**
     * Redirect CompiledChunk.isVisible in setupTerrain's BFS traversal.
     *
     * After a tab switch, setWorldAndLoadRenderers creates new RenderChunk
     * instances that are not yet compiled. CompiledChunk.DUMMY.isVisible()
     * returns false for ALL face pairs, which prevents the BFS from expanding
     * beyond the player's own chunk. This means entities in other chunks are
     * never rendered.
     *
     * This redirect makes isVisible return true for uncompiled chunks, allowing
     * the BFS to expand through them and discover all entities. Once chunks are
     * compiled (which happens asynchronously over the next few frames), the
     * real visibility data is used instead.
     */
    @Redirect(
        method = "setupTerrain",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/CompiledChunk;isVisible(Lnet/minecraft/util/EnumFacing;Lnet/minecraft/util/EnumFacing;)Z")
    )
    private boolean fdp$expandThroughUncompiledChunks(CompiledChunk compiledChunk, EnumFacing facing, EnumFacing facing2) {
        // If the chunk is actually compiled (not DUMMY), use its real visibility data
        if (compiledChunk.isEmpty()) {
            // Uncompiled or empty chunks have no occlusion data, so assume
            // all faces are visible. This allows the BFS to expand through
            // uncompiled chunks after a tab switch, ensuring entities in all
            // chunks are rendered immediately.
            return true;
        }
        return compiledChunk.isVisible(facing, facing2);
    }
}
