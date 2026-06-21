package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import org.apache.commons.lang3.mutable.MutableInt;

public class LootPool implements Validatable {
    public static final Codec<LootPool> CODEC = RecordCodecBuilder.create(
        i -> i.group(
                LootPoolEntries.CODEC.listOf().fieldOf("entries").forGetter(p -> p.entries),
                LootItemCondition.DIRECT_CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(p -> p.conditions),
                LootItemFunctions.ROOT_CODEC.listOf().optionalFieldOf("functions", List.of()).forGetter(p -> p.functions),
                NumberProviders.CODEC.fieldOf("rolls").forGetter(p -> p.rolls),
                NumberProviders.CODEC.fieldOf("bonus_rolls").orElse(ConstantValue.exactly(0.0F)).forGetter(p -> p.bonusRolls),
                Codec.STRING.optionalFieldOf("name").forGetter(p -> p.name.filter(n -> !n.startsWith("custom#"))),
                net.minecraftforge.common.crafting.conditions.ICondition.OPTIONAL_FEILD_CODEC.forGetter(p -> p.forge_condition)
            )
            .apply(i, LootPool::new)
    );
    public static final Codec<LootPool> CONDITIONAL_CODEC = net.minecraftforge.common.crafting.conditions.ConditionCodec.checkingDecode(CODEC, () -> lootPool().build());
    private final List<LootPoolEntryContainer> entries;
    private final List<LootItemCondition> conditions;
    private final Predicate<LootContext> compositeCondition;
    private final List<LootItemFunction> functions;
    private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;
    private NumberProvider rolls;
    private NumberProvider bonusRolls;
    private java.util.Optional<String> name;
    private java.util.Optional<net.minecraftforge.common.crafting.conditions.ICondition> forge_condition;

    private LootPool(
        final List<LootPoolEntryContainer> entries,
        final List<LootItemCondition> conditions,
        final List<LootItemFunction> functions,
        final NumberProvider rolls,
        final NumberProvider bonusRolls
    ) {
        this(entries, conditions, functions, rolls, bonusRolls, java.util.Optional.empty(), java.util.Optional.empty());
    }

    private LootPool(
        final List<LootPoolEntryContainer> entries,
        final List<LootItemCondition> conditions,
        final List<LootItemFunction> functions,
        final NumberProvider rolls,
        final NumberProvider bonusRolls,
        final java.util.Optional<String> name,
        final java.util.Optional<net.minecraftforge.common.crafting.conditions.ICondition> forge_condition
    ) {
        this.entries = entries;
        this.conditions = conditions;
        this.compositeCondition = Util.allOf(conditions);
        this.functions = functions;
        this.compositeFunction = LootItemFunctions.compose(functions);
        this.rolls = rolls;
        this.bonusRolls = bonusRolls;
        this.name = name;
        this.forge_condition = forge_condition;
    }

    private void addRandomItem(final Consumer<ItemStack> result, final LootContext context) {
        RandomSource random = context.getRandom();
        List<LootPoolEntry> validEntries = Lists.newArrayList();
        MutableInt totalWeight = new MutableInt();

        for (LootPoolEntryContainer entry : this.entries) {
            entry.expand(context, e -> {
                int weight = e.getWeight(context.getLuck());
                if (weight > 0) {
                    validEntries.add(e);
                    totalWeight.add(weight);
                }
            });
        }

        int entryCount = validEntries.size();
        if (totalWeight.intValue() != 0 && entryCount != 0) {
            if (entryCount == 1) {
                validEntries.get(0).createItemStack(result, context);
            } else {
                int index = random.nextInt(totalWeight.intValue());

                for (LootPoolEntry entry : validEntries) {
                    index -= entry.getWeight(context.getLuck());
                    if (index < 0) {
                        entry.createItemStack(result, context);
                        return;
                    }
                }
            }
        }
    }

    public void addRandomItems(final Consumer<ItemStack> result, final LootContext context) {
        if (this.compositeCondition.test(context)) {
            Consumer<ItemStack> decoratedConsumer = LootItemFunction.decorate(this.compositeFunction, result, context);
            int count = this.rolls.getInt(context) + Mth.floor(this.bonusRolls.getFloat(context) * context.getLuck());

            for (int i = 0; i < count; i++) {
                this.addRandomItem(decoratedConsumer, context);
            }
        }
    }

    @Override
    public void validate(final ValidationContext output) {
        Validatable.validate(output, "conditions", this.conditions);
        Validatable.validate(output, "functions", this.functions);
        Validatable.validate(output, "entries", this.entries);
        Validatable.validate(output, "rolls", this.rolls);
        Validatable.validate(output, "bonus_rolls", this.bonusRolls);
    }

    public static LootPool.Builder lootPool() {
        return new LootPool.Builder();
    }

    private boolean isFrozen = false;
    public void freeze() { this.isFrozen = true; }
    public boolean isFrozen(){ return this.isFrozen; }
    private void checkFrozen() {
       if (this.isFrozen())
          throw new RuntimeException("Attempted to modify LootPool after being frozen!");
    }
    @org.jetbrains.annotations.Nullable
    public String getName() { return this.name.orElse(null); }
    void setName(final String name) {
       if (this.name.isPresent())
          throw new UnsupportedOperationException("Cannot change the name of a pool when it has a name set!");
       this.name = java.util.Optional.of(name);
    }
    public NumberProvider getRolls()      { return this.rolls; }
    public NumberProvider getBonusRolls() { return this.bonusRolls; }
    public void setRolls     (NumberProvider v){ checkFrozen(); this.rolls = v; }
    public void setBonusRolls(NumberProvider v){ checkFrozen(); this.bonusRolls = v; }

    public static class Builder implements FunctionUserBuilder<LootPool.Builder>, ConditionUserBuilder<LootPool.Builder> {
        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemCondition> conditions = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemFunction> functions = ImmutableList.builder();
        private NumberProvider rolls = ConstantValue.exactly(1.0F);
        private NumberProvider bonusRolls = ConstantValue.exactly(0.0F);
        @org.jetbrains.annotations.Nullable
        private String name;
        @org.jetbrains.annotations.Nullable
        private net.minecraftforge.common.crafting.conditions.ICondition forge_condition;

        public LootPool.Builder setRolls(final NumberProvider rolls) {
            this.rolls = rolls;
            return this;
        }

        public LootPool.Builder unwrap() {
            return this;
        }

        public LootPool.Builder setBonusRolls(final NumberProvider bonusRolls) {
            this.bonusRolls = bonusRolls;
            return this;
        }

        public LootPool.Builder add(final LootPoolEntryContainer.Builder<?> entry) {
            this.entries.add(entry.build());
            return this;
        }

        public LootPool.Builder when(final LootItemCondition.Builder condition) {
            this.conditions.add(condition.build());
            return this;
        }

        public LootPool.Builder apply(final LootItemFunction.Builder function) {
            this.functions.add(function.build());
            return this;
        }

        public LootPool.Builder name(String name) {
            this.name = name;
            return this;
        }

        public LootPool.Builder when(net.minecraftforge.common.crafting.conditions.ICondition value) {
            this.forge_condition = value;
            return this;
        }

        public LootPool build() {
            return new LootPool(this.entries.build(), this.conditions.build(), this.functions.build(), this.rolls, this.bonusRolls, java.util.Optional.ofNullable(this.name), java.util.Optional.ofNullable(forge_condition));
        }
    }
}
