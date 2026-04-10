package net.asd.union.injection.forge.mixins.render;

import net.asd.union.utils.performance.ChunkOptimizer;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Mixin(ChunkRenderDispatcher.class)
public class MixinChunkRenderDispatcher {

    @Shadow
    @Mutable
    @Final
    private BlockingQueue<RegionRenderCacheBuilder> field_178520_e;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fdp$expandBuilderPool(CallbackInfo ci) {
        if (!ChunkOptimizer.INSTANCE.getEnabled()) {
            return;
        }

        try {
            LinkedBlockingQueue<RegionRenderCacheBuilder> expandedQueue = new LinkedBlockingQueue<>();
            field_178520_e.drainTo(expandedQueue);

            int extraBuilders = 5;
            for (int i = 0; i < extraBuilders; i++) {
                expandedQueue.add(new RegionRenderCacheBuilder());
            }

            field_178520_e = expandedQueue;
        } catch (Exception ignored) {
        }
    }
}
