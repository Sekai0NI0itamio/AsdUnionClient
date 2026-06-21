package net.minecraft.data.recipes;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import org.jspecify.annotations.Nullable;

public interface RecipeOutput {
    default void accept(ResourceKey<Recipe<?>> id, Recipe<?> recipe, @Nullable AdvancementHolder advancement) {
        if (advancement == null) {
            accept(id, recipe, null, null);
        } else {
            var ops = registry().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
            var json = Advancement.CODEC.encodeStart(ops, advancement.value()).getOrThrow(IllegalStateException::new);
            accept(id, recipe, advancement.id(), json);
        }
    }

    void accept(ResourceKey<Recipe<?>> id, Recipe<?> recipe, net.minecraft.resources.@Nullable Identifier advancementId, com.google.gson.@Nullable JsonElement advancement);

    net.minecraft.core.HolderLookup.Provider registry();

    Advancement.Builder advancement();

    void includeRootAdvancement();
}
