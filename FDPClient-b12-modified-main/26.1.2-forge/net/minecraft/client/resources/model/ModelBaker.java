package net.minecraft.client.resources.model;

import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3fc;

@OnlyIn(Dist.CLIENT)
public interface ModelBaker {
    ResolvedModel getModel(Identifier location);

    BlockStateModelPart missingBlockModelPart();

    MaterialBaker materials();

    ModelBaker.Interner interner();

    <T> T compute(ModelBaker.SharedOperationKey<T> key);

    @OnlyIn(Dist.CLIENT)
    public interface Interner {
        Vector3fc vector(Vector3fc vector);

        BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material);
    }

    @FunctionalInterface
    @OnlyIn(Dist.CLIENT)
    public interface SharedOperationKey<T> {
        T compute(ModelBaker modelBakery);
    }

    /** Forge: Return the render type to use when baking this model, its a dirty hack to pass down this value to parents */
    @org.jetbrains.annotations.Nullable
    default net.minecraftforge.client.RenderTypeGroup renderType() {
        return null;
    }

    /** Forge: Return the fast graphics render type to use when baking this model, its a dirty hack to pass down this value to parents */
    @org.jetbrains.annotations.Nullable
    default net.minecraftforge.client.RenderTypeGroup renderTypeFast() {
        return null;
    }
}
