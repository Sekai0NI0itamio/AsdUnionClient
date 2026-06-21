package net.minecraft.client.renderer.block.dispatch;

import java.util.List;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WeightedVariants implements BlockStateModel {
    private final WeightedList<BlockStateModel> list;
    private final Material.Baked particleMaterial;
    @BakedQuad.MaterialFlags
    private final int materialFlags;
    private final BlockStateModel first;

    public WeightedVariants(final WeightedList<BlockStateModel> list) {
        this.list = list;
        BlockStateModel firstModel = list.unwrap().getFirst().value();
        this.particleMaterial = firstModel.particleMaterial();
        this.first = firstModel;
        this.materialFlags = computeMaterialFlags(list);
    }

    @BakedQuad.MaterialFlags
    private static int computeMaterialFlags(final WeightedList<BlockStateModel> list) {
        int flags = 0;

        for (Weighted<BlockStateModel> entry : list.unwrap()) {
            flags |= entry.value().materialFlags();
        }

        return flags;
    }

    @Override
    public Material.Baked particleMaterial() {
        return this.particleMaterial;
    }

    @Override
    public Material.Baked particleMaterial(net.minecraftforge.client.model.data.ModelData data) {
        return this.first.particleMaterial(data);
    }

    @BakedQuad.MaterialFlags
    @Override
    public int materialFlags() {
        return this.materialFlags;
    }

    @Override
    public void collectParts(final RandomSource random, final List<BlockStateModelPart> output) {
        this.list.getRandomOrThrow(random).collectParts(random, output);
    }

    @Override
    public void collectParts(final RandomSource random, final List<BlockStateModelPart> output, net.minecraftforge.client.model.data.ModelData data) {
        this.list.getRandomOrThrow(random).collectParts(random, output, data);
    }

    @OnlyIn(Dist.CLIENT)
    public record Unbaked(WeightedList<BlockStateModel.Unbaked> entries) implements BlockStateModel.Unbaked {
        @Override
        public BlockStateModel bake(final ModelBaker modelBakery) {
            return new WeightedVariants(this.entries.map(m -> m.bake(modelBakery)));
        }

        @Override
        public void resolveDependencies(final ResolvableModel.Resolver resolver) {
            this.entries.unwrap().forEach(v -> v.value().resolveDependencies(resolver));
        }
    }
}
