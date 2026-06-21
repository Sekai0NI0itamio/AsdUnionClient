package net.minecraft.world.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public interface Shearable extends net.minecraftforge.common.IForgeShearable {
    /** @deprecated Use {@link net.minecraftforge.common.IForgeShearable#onSheared} */
    void shear(ServerLevel level, SoundSource soundSource, ItemStack tool);

    /** @deprecated Use {@link net.minecraftforge.common.IForgeShearable#isShearable} */
    boolean readyForShearing();

    default boolean isShearable(net.minecraft.world.item.ItemStack item, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return readyForShearing();
    }
}
