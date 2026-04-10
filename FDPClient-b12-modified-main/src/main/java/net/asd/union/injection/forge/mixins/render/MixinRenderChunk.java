package net.asd.union.injection.forge.mixins.render;

import net.asd.union.handler.render.AntiTranslucent;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderChunk.class)
public class MixinRenderChunk {

    @Redirect(
            method = "rebuildChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;canRenderInLayer(Lnet/minecraft/util/EnumWorldBlockLayer;)Z"
            )
    )
    private boolean fdp$canRenderInLayerRedirect(Block block, EnumWorldBlockLayer layer) {
        try {
            if (AntiTranslucent.INSTANCE.getEnabled()) {
                EnumWorldBlockLayer blockLayer = block.getBlockLayer();
                if (blockLayer == EnumWorldBlockLayer.TRANSLUCENT) {
                    return layer == EnumWorldBlockLayer.SOLID;
                }
            }
        } catch (Throwable ignored) {
        }

        return block.canRenderInLayer(layer);
    }
}
