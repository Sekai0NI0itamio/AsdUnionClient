package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.HolderSetCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.ItemLike;

public class Ingredient implements Predicate<ItemStack>, StackedContents.IngredientInfo<Holder<Item>> {
    private static final StreamCodec<RegistryFriendlyByteBuf, Ingredient> VANILLA_CONTENTS_STREAM_CODEC = ByteBufCodecs.holderSet(Registries.ITEM)
        .map(Ingredient::new, i -> i.values);
    public static final StreamCodec<RegistryFriendlyByteBuf, Ingredient> CONTENTS_STREAM_CODEC = net.minecraftforge.common.ForgeHooks.ingredientStreamCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> OPTIONAL_CONTENTS_STREAM_CODEC = CONTENTS_STREAM_CODEC
        .map(
            ingredient -> ingredient.items().count() == 0 ? Optional.empty() : Optional.of(ingredient),
            ingredient -> ingredient.orElseGet(() -> Ingredient.of())
        );
    public static final Codec<HolderSet<Item>> NON_AIR_HOLDER_SET_CODEC = HolderSetCodec.create(Registries.ITEM, Item.CODEC, false);
    private static final Codec<Ingredient> VANILLA_CODEC = ExtraCodecs.nonEmptyHolderSet(NON_AIR_HOLDER_SET_CODEC).xmap(Ingredient::new, i -> i.values);
    private static final com.mojang.serialization.MapCodec<Ingredient> VANILLA_MAP_CODEC = VANILLA_CODEC.fieldOf("value");
    public static final Codec<Ingredient> CODEC = net.minecraftforge.common.ForgeHooks.ingredientBaseCodec(VANILLA_CODEC);
    protected final HolderSet<Item> values;

    protected Ingredient(final HolderSet<Item> values) {
        this(values, true);
    }

    protected Ingredient(HolderSet<Item> values, boolean validate) {
        if (validate)
        values.unwrap().ifRight(directValues -> {
            if (directValues.isEmpty()) {
                throw new UnsupportedOperationException("Ingredients can't be empty");
            } else if (directValues.contains(Items.AIR.builtInRegistryHolder())) {
                throw new UnsupportedOperationException("Ingredient can't contain air");
            }
        });
        this.values = values;
    }

    public static boolean testOptionalIngredient(final Optional<Ingredient> ingredient, final ItemStack stack) {
        return ingredient.<Boolean>map(value -> value.test(stack)).orElseGet(stack::isEmpty);
    }

    @Deprecated
    public Stream<Holder<Item>> items() {
        return this.values.stream();
    }

    public boolean isEmpty() {
        return this.values.size() == 0;
    }

    public boolean test(final ItemStack input) {
        return input.is(this.values);
    }

    public boolean acceptsItem(final Holder<Item> item) {
        return this.values.contains(item);
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof Ingredient other ? Objects.equals(this.values, other.values) : false;
    }

    public static Ingredient of(final ItemLike itemLike) {
        return new Ingredient(HolderSet.direct(itemLike.asItem().builtInRegistryHolder()));
    }

    public static Ingredient of(final ItemLike... items) {
        return of(Arrays.stream(items));
    }

    public static Ingredient of(final Stream<? extends ItemLike> stream) {
        return new Ingredient(HolderSet.direct(stream.map(e -> e.asItem().builtInRegistryHolder()).toList()));
    }

    public static Ingredient of(final HolderSet<Item> tag) {
        return new Ingredient(tag);
    }

    public SlotDisplay display() {
        return (SlotDisplay)this.values
            .unwrap()
            .map(SlotDisplay.TagSlotDisplay::new, l -> new SlotDisplay.Composite(l.stream().map(Ingredient::displayForSingleItem).toList()));
    }

    public static SlotDisplay optionalIngredientToDisplay(final Optional<Ingredient> ingredient) {
        return ingredient.map(Ingredient::display).orElse(SlotDisplay.Empty.INSTANCE);
    }

    private static SlotDisplay displayForSingleItem(final Holder<Item> item) {
        SlotDisplay inputDisplay = new SlotDisplay.ItemSlotDisplay(item);
        ItemStackTemplate remainderStack = item.value().getCraftingRemainder();
        if (remainderStack != null) {
            SlotDisplay remainderDisplay = new SlotDisplay.ItemStackSlotDisplay(remainderStack);
            return new SlotDisplay.WithRemainder(inputDisplay, remainderDisplay);
        } else {
            return inputDisplay;
        }
    }

    public boolean isSimple() {
        return true;
    }

    private final boolean isVanilla = this.getClass() == Ingredient.class;
    public final boolean isVanilla() {
        return isVanilla;
    }

    public static final net.minecraftforge.common.crafting.ingredients.IIngredientSerializer<Ingredient> VANILLA_SERIALIZER =
        new net.minecraftforge.common.crafting.ingredients.IIngredientSerializer<Ingredient>() {
            @Override
            public com.mojang.serialization.MapCodec<? extends Ingredient> codec() {
                return VANILLA_MAP_CODEC;
            }

            @Override
            public void write(RegistryFriendlyByteBuf buffer, Ingredient value) {
                VANILLA_CONTENTS_STREAM_CODEC.encode(buffer, value);
            }

            @Override
            public Ingredient read(RegistryFriendlyByteBuf buffer) {
                return VANILLA_CONTENTS_STREAM_CODEC.decode(buffer);
            }
        };

    public net.minecraftforge.common.crafting.ingredients.IIngredientSerializer<? extends Ingredient> serializer() {
        if (!isVanilla()) throw new IllegalStateException("Modders must implement Ingredient.codec in their custom Ingredients: " + getClass());
        return VANILLA_SERIALIZER;
    }

    @Override
    public String toString() {
        var buf = new StringBuilder();
        buf.append("Ingredient[");
        for (int x = 0; x < values.size(); x++) {
            if (x != 0)
                buf.append(", ");
            buf.append(values.get(x));
        }
        buf.append(']');
        return buf.toString();
    }
}
