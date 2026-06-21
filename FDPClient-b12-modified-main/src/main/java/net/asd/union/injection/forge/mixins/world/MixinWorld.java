/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.world;

import net.asd.union.handler.sessiontabs.TabSimulationThread;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class MixinWorld {
    @Shadow
    public abstract IBlockState getBlockState(BlockPos pos);

    /**
     * Block render update notifications from simulation threads.
     * World.markBlockRangeForRenderUpdate iterates over worldAccesses
     * (which includes RenderGlobal) and would trigger render updates
     * on the foreground tab's renderer.
     */
    @Inject(method = "markBlockRangeForRenderUpdate(Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/BlockPos;)V", at = @At("HEAD"), cancellable = true)
    private void fdp$skipRenderUpdatesFromSimBlockPos(BlockPos rangeMin, BlockPos rangeMax, CallbackInfo ci) {
        if (Thread.currentThread() instanceof TabSimulationThread) {
            ci.cancel();
        }
    }

    @Inject(method = "markBlockRangeForRenderUpdate(IIIIII)V", at = @At("HEAD"), cancellable = true)
    private void fdp$skipRenderUpdatesFromSimInt(int x1, int y1, int z1, int x2, int y2, int z2, CallbackInfo ci) {
        if (Thread.currentThread() instanceof TabSimulationThread) {
            ci.cancel();
        }
    }
}
