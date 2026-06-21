package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class ConcretePowderBlock extends FallingBlock {
    public static final MapCodec<ConcretePowderBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("concrete").forGetter(b -> b.concrete), propertiesCodec())
            .apply(i, ConcretePowderBlock::new)
    );
    private final Block concrete;

    @Override
    public MapCodec<ConcretePowderBlock> codec() {
        return CODEC;
    }

    public ConcretePowderBlock(final Block concrete, final BlockBehaviour.Properties properties) {
        super(properties);
        this.concrete = concrete;
    }

    @Override
    public void onLand(final Level level, final BlockPos pos, final BlockState state, final BlockState replacedBlock, final FallingBlockEntity entity) {
        if (shouldSolidify(level, pos, state, replacedBlock.getFluidState())) { // Forge: Use block of falling entity instead of block at replaced position, and check if shouldSolidify with the FluidState of the replaced block
            level.setBlock(pos, this.concrete.defaultBlockState(), 3);
        }
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState replacedBlock = level.getBlockState(pos);
        return shouldSolidify(level, pos, replacedBlock) ? this.concrete.defaultBlockState() : super.getStateForPlacement(context);
    }

    private static boolean shouldSolidify(final BlockGetter level, final BlockPos pos, final BlockState state, final net.minecraft.world.level.material.FluidState fluidState) {
        return state.canBeHydrated(level, pos, fluidState, pos) || touchesLiquid(level, pos, state);
    }

    private static boolean shouldSolidify(final BlockGetter level, final BlockPos pos, final BlockState replacedBlock) {
        return shouldSolidify(level, pos, replacedBlock, level.getFluidState(pos));
    }

    private static boolean touchesLiquid(final BlockGetter level, BlockPos pos, BlockState state) {
        boolean touchesLiquid = false;
        BlockPos.MutableBlockPos testPos = pos.mutable();

        for (Direction direction : Direction.values()) {
            BlockState blockState = level.getBlockState(testPos);
            if (direction != Direction.DOWN || state.canBeHydrated(level, pos, blockState.getFluidState(), testPos)) {
                testPos.setWithOffset(pos, direction);
                blockState = level.getBlockState(testPos);
                if (state.canBeHydrated(level, pos, blockState.getFluidState(), testPos) && !blockState.isFaceSturdy(level, pos, direction.getOpposite())) {
                    touchesLiquid = true;
                    break;
                }
            }
        }

        return touchesLiquid;
    }

    private static boolean canSolidify(final BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
    }

    @Override
    protected BlockState updateShape(
        final BlockState state,
        final LevelReader level,
        final ScheduledTickAccess ticks,
        final BlockPos pos,
        final Direction directionToNeighbour,
        final BlockPos neighbourPos,
        final BlockState neighbourState,
        final RandomSource random
    ) {
        return touchesLiquid(level, pos, state)
            ? this.concrete.defaultBlockState()
            : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public int getDustColor(final BlockState blockState, final BlockGetter level, final BlockPos pos) {
        return blockState.getMapColor(level, pos).col;
    }
}
