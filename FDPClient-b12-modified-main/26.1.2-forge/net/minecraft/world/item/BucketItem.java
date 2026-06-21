package net.minecraft.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

public class BucketItem extends Item implements DispensibleContainerItem {
    private final Fluid content;

    // Forge: Use the other constructor that takes a Supplier
    @Deprecated
    public BucketItem(final Fluid content, final Item.Properties properties) {
        super(properties);
        this.content = content;
        this.fluidSupplier = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getDelegateOrThrow(content);
    }

    /**
     * @param supplier A fluid supplier such as {@link net.minecraftforge.registries.RegistryObject<Fluid>}
     */
    public BucketItem(java.util.function.Supplier<? extends Fluid> supplier, Item.Properties builder) {
       super(builder);
       this.content = null;
       this.fluidSupplier = supplier;
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, this.content == Fluids.EMPTY ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);
        var ret = net.minecraftforge.event.ForgeEventFactory.onBucketUse(player, level, itemStack, hitResult);
        if (ret != null) return ret;
        if (hitResult.getType() == HitResult.Type.MISS) {
            return InteractionResult.PASS;
        } else if (hitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        } else {
            BlockPos pos = hitResult.getBlockPos();
            Direction direction = hitResult.getDirection();
            BlockPos directionOffsetPos = pos.relative(direction);
            if (!level.mayInteract(player, pos) || !player.mayUseItemAt(directionOffsetPos, direction, itemStack)) {
                return InteractionResult.FAIL;
            } else if (this.content == Fluids.EMPTY) {
                BlockState blockState = level.getBlockState(pos);
                if (blockState.getBlock() instanceof BucketPickup bucketPickupBlock) {
                    ItemStack taken = bucketPickupBlock.pickupBlock(player, level, pos, blockState);
                    if (!taken.isEmpty()) {
                        player.awardStat(Stats.ITEM_USED.get(this));
                        bucketPickupBlock.getPickupSound(blockState).ifPresent(soundEvent -> player.playSound(soundEvent, 1.0F, 1.0F));
                        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
                        ItemStack result = ItemUtils.createFilledResult(itemStack, player, taken);
                        if (!level.isClientSide()) {
                            CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer)player, taken);
                        }

                        return InteractionResult.SUCCESS.heldItemTransformedTo(result);
                    }
                }

                return InteractionResult.FAIL;
            } else {
                BlockState clicked = level.getBlockState(pos);
                BlockPos placePos = canBlockContainFluid(level, pos, clicked) ? pos : directionOffsetPos;
                if (this.emptyContents(player, level, placePos, hitResult, itemStack)) {
                    this.checkExtraContent(player, level, itemStack, placePos);
                    if (player instanceof ServerPlayer) {
                        CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer)player, placePos, itemStack);
                    }

                    player.awardStat(Stats.ITEM_USED.get(this));
                    ItemStack emptyResult = ItemUtils.createFilledResult(itemStack, player, getEmptySuccessItem(itemStack, player));
                    return InteractionResult.SUCCESS.heldItemTransformedTo(emptyResult);
                } else {
                    return InteractionResult.FAIL;
                }
            }
        }
    }

    public static ItemStack getEmptySuccessItem(final ItemStack itemStack, final Player player) {
        return !player.hasInfiniteMaterials() ? new ItemStack(Items.BUCKET) : itemStack;
    }

    @Override
    public void checkExtraContent(final @Nullable LivingEntity user, final Level level, final ItemStack itemStack, final BlockPos pos) {
    }

    @Deprecated //Forge: use the ItemStack sensitive version
    @Override
    public boolean emptyContents(final @Nullable LivingEntity user, final Level level, final BlockPos pos, final @Nullable BlockHitResult hitResult) {
        return this.emptyContents(user, level, pos, hitResult, null);
    }

    public boolean emptyContents(final @Nullable LivingEntity user, final Level level, final BlockPos pos, final @Nullable BlockHitResult hitResult, final @Nullable ItemStack containerItem) {
        if (!(this.content instanceof FlowingFluid flowingFluid)) {
            return false;
        } else {
            BlockState blockState = level.getBlockState(pos);
            Block block = blockState.getBlock();
            boolean mayReplace = blockState.canBeReplaced(this.content);
            boolean shiftKeyDown = user != null && user.isShiftKeyDown();
            boolean placeLiquid = mayReplace
                || block instanceof LiquidBlockContainer container && container.canPlaceLiquid(user, level, pos, blockState, this.content);
            boolean canPlaceFluidInsideBlock = blockState.isAir() || placeLiquid && (!shiftKeyDown || hitResult == null);
            var containedFluidStack = java.util.Optional.ofNullable(containerItem).flatMap(net.minecraftforge.fluids.FluidUtil::getFluidContained);
            if (!canPlaceFluidInsideBlock) {
                return hitResult != null && this.emptyContents(user, level, hitResult.getBlockPos().relative(hitResult.getDirection()), null, containerItem);
            } else if (containedFluidStack.isPresent() && this.content.getFluidType().isVaporizedOnPlacement(level, pos, containedFluidStack.get())) {
                this.content.getFluidType().onVaporize(user, level, pos, containedFluidStack.get());
                return true;
            } else if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos) && this.content.is(FluidTags.WATER)) {
                int x = pos.getX();
                int y = pos.getY();
                int z = pos.getZ();
                RandomSource random = level.getRandom();
                level.playSound(user, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.6F + (random.nextFloat() - random.nextFloat()) * 0.8F);

                for (int i = 0; i < 8; i++) {
                    level.addParticle(ParticleTypes.LARGE_SMOKE, x + random.nextFloat(), y + random.nextFloat(), z + random.nextFloat(), 0.0, 0.0, 0.0);
                }

                return true;
            } else if (block instanceof LiquidBlockContainer containerx && containerx.canPlaceLiquid(user, level, pos, blockState, content) && this.content == Fluids.WATER) {
                containerx.placeLiquid(level, pos, blockState, flowingFluid.getSource(false));
                this.playEmptySound(user, level, pos);
                return true;
            } else {
                if (!level.isClientSide() && mayReplace && !blockState.liquid()) {
                    level.destroyBlock(pos, true);
                }

                if (!level.setBlock(pos, this.content.defaultFluidState().createLegacyBlock(), 11) && !blockState.getFluidState().isSource()) {
                    return false;
                } else {
                    this.playEmptySound(user, level, pos);
                    return true;
                }
            }
        }
    }

    protected void playEmptySound(final @Nullable LivingEntity user, final LevelAccessor level, final BlockPos pos) {
        SoundEvent soundEvent = this.content.is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
        var custom = this.content.getFluidType().getSound(user, level, pos, net.minecraftforge.common.SoundActions.BUCKET_EMPTY);
        if (custom != null) {
            soundEvent = custom;
        }
        level.playSound(user, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(user, GameEvent.FLUID_PLACE, pos);
    }

    /** Forge: TODO: Forge ItemStack capabilities - Lex 042724
    @Override
    public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(ItemStack stack, @Nullable net.minecraft.nbt.CompoundTag nbt) {
        if (this.getClass() == BucketItem.class) {
            return new net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper(stack);
        } else {
            return super.initCapabilities(stack, nbt);
        }
    }
    */

    private final java.util.function.Supplier<? extends Fluid> fluidSupplier;

    public Fluid getFluid() {
        return fluidSupplier.get();
    }

    protected boolean canBlockContainFluid(Level worldIn, BlockPos posIn, BlockState blockstate) {
        return blockstate.getBlock() instanceof LiquidBlockContainer liquid && liquid.canPlaceLiquid(null, worldIn, posIn, blockstate, this.content);
    }

    public Fluid getContent() {
        return this.content;
    }
}
