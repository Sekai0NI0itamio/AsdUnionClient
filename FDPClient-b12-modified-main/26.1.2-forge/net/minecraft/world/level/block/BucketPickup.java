package net.minecraft.world.level.block;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public interface BucketPickup extends net.minecraftforge.common.extensions.IForgeBucketPickup {
    ItemStack pickupBlock(@Nullable LivingEntity user, LevelAccessor level, BlockPos pos, BlockState state);

    /** @deprecated Forge: Use state-sensitive variant instead */
    @Deprecated
    Optional<SoundEvent> getPickupSound();
}
