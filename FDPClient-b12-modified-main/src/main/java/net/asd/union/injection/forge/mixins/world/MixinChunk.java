/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.world;

import net.asd.union.utils.render.MiniMapRegister;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Chunk.class)
public class MixinChunk {
    @Shadow
    @Final
    public int xPosition;

    @Shadow
    @Final
    public int zPosition;

    @Inject(method = {"setBlockState", "func_177439_a"}, at = @At("HEAD"), cancellable = true)
    private void guardInvalidBlockState(BlockPos pos, IBlockState state, CallbackInfoReturnable<IBlockState> ci) {
        int y = pos.getY();
        int localX = pos.getX() - (xPosition << 4);
        int localZ = pos.getZ() - (zPosition << 4);

        if (y < 0 || y >= 256 || localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
            ci.setReturnValue(null);
            return;
        }
    }

    @Inject(method = {"setBlockState", "func_177439_a"}, at = @At("HEAD"))
    private void setProphuntBlock(BlockPos pos, IBlockState state, final CallbackInfoReturnable callbackInfo) {
        //noinspection ConstantConditions
        MiniMapRegister.INSTANCE.updateChunk((Chunk) ((Object) this));
    }

    @Inject(method = "onChunkUnload", at = @At("HEAD"))
    private void injectFillChunk(CallbackInfo ci) {
        MiniMapRegister.INSTANCE.unloadChunk(xPosition, zPosition);
    }

    @Inject(method = "fillChunk", at = @At("RETURN"))
    private void injectFillChunk(byte[] p_177439_1_, int p_177439_2_, boolean p_177439_3_, CallbackInfo ci) {
        //noinspection ConstantConditions
        MiniMapRegister.INSTANCE.updateChunk((Chunk) ((Object) this));
    }

    @Inject(method = "fillChunk", at = @At("HEAD"), cancellable = true)
    private void onFillChunk(byte[] data, int p_177439_2_, boolean p_177439_3_, CallbackInfo ci) {
    }

    @Inject(method = "getBiomeArray", at = @At("HEAD"))
    private void fixBiomeArray(CallbackInfoReturnable<byte[]> ci) {
        Chunk chunk = (Chunk) (Object) this;
        try {
            if (chunk.getBiomeArray() != null && chunk.getBiomeArray().length == 257) {
                byte[] fixed = new byte[256];
                System.arraycopy(chunk.getBiomeArray(), 0, fixed, 0, 256);
                ci.setReturnValue(fixed);
            }
        } catch (Exception ignored) {
        }
    }
}