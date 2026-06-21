package net.minecraft.client.data.models;

import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.data.models.blockstates.BlockModelDefinitionGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelInstance;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ModelProvider implements DataProvider {
    private final PackOutput.PathProvider blockStatePathProvider;
    private final PackOutput.PathProvider itemInfoPathProvider;
    private final PackOutput.PathProvider modelPathProvider;

    public ModelProvider(final PackOutput output) {
        this.blockStatePathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates");
        this.itemInfoPathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
        this.modelPathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
    }

    @Override
    public CompletableFuture<?> run(final CachedOutput cache) {
        ModelProvider.ItemInfoCollector itemModels = new ModelProvider.ItemInfoCollector(this::getKnownItems);
        ModelProvider.BlockStateGeneratorCollector blockStateGenerators = new ModelProvider.BlockStateGeneratorCollector(this::getKnownBlocks);
        ModelProvider.SimpleModelCollector simpleModels = new ModelProvider.SimpleModelCollector();
         getBlockModelGenerators(blockStateGenerators, itemModels, simpleModels).run();
         getItemModelGenerators(itemModels, simpleModels).run();
        blockStateGenerators.validate();
        itemModels.finalizeAndValidate();
        return CompletableFuture.allOf(
            blockStateGenerators.save(cache, this.blockStatePathProvider),
            simpleModels.save(cache, this.modelPathProvider),
            itemModels.save(cache, this.itemInfoPathProvider)
        );
    }

    protected java.util.stream.Stream<Block> getKnownBlocks() {
         return BuiltInRegistries.BLOCK.stream().filter(block -> "minecraft".equals(block.builtInRegistryHolder().key().identifier().getNamespace()));
    }

    protected java.util.stream.Stream<Item> getKnownItems() {
        return BuiltInRegistries.ITEM.stream().filter(item -> "minecraft".equals(item.builtInRegistryHolder().key().identifier().getNamespace()));
    }

    protected BlockModelGenerators getBlockModelGenerators(BlockStateGeneratorCollector blocks, ItemInfoCollector items, SimpleModelCollector models) {
        return new BlockModelGenerators(blocks, items, models);
    }

    protected ItemModelGenerators getItemModelGenerators(ItemInfoCollector items, SimpleModelCollector models) {
        return new ItemModelGenerators(items, models);
    }

    @Override
    public final String getName() {
        return "Model Definitions";
    }

    @OnlyIn(Dist.CLIENT)
    public static class BlockStateGeneratorCollector implements Consumer<BlockModelDefinitionGenerator> {
        private final Map<Block, BlockModelDefinitionGenerator> generators = new HashMap<>();
        private final Supplier<java.util.stream.Stream<Block>> known;

        public BlockStateGeneratorCollector() {
            this(() -> BuiltInRegistries.BLOCK.stream().filter(block -> "minecraft".equals(block.builtInRegistryHolder().key().identifier().getNamespace())));
        }

        public BlockStateGeneratorCollector(Supplier<java.util.stream.Stream<Block>> known) {
            this.known = known;
        }

        public void accept(final BlockModelDefinitionGenerator generator) {
            Block block = generator.block();
            BlockModelDefinitionGenerator prev = this.generators.put(block, generator);
            if (prev != null) {
                throw new IllegalStateException("Duplicate blockstate definition for " + block);
            }
        }

        public void validate() {
            List<Identifier> missingDefinitions = known.get().map(Block::builtInRegistryHolder)
                .filter(e -> !this.generators.containsKey(e.value()))
                .map(e -> e.key().identifier())
                .toList();
            if (!missingDefinitions.isEmpty()) {
                throw new IllegalStateException("Missing blockstate definitions for: " + missingDefinitions);
            }
        }

        public CompletableFuture<?> save(final CachedOutput cache, final PackOutput.PathProvider pathProvider) {
            Map<Block, BlockStateModelDispatcher> definitions = Maps.transformValues(this.generators, BlockModelDefinitionGenerator::create);
            Function<Block, Path> pathGetter = block -> pathProvider.json(block.builtInRegistryHolder().key().identifier());
            return DataProvider.saveAll(cache, BlockStateModelDispatcher.CODEC, pathGetter, definitions);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class ItemInfoCollector implements ItemModelOutput {
        private final Map<Item, ClientItem> itemInfos = new HashMap<>();
        private final Map<Item, Item> copies = new HashMap<>();
        private final Supplier<java.util.stream.Stream<Item>> known;

        public ItemInfoCollector() {
            this(() -> BuiltInRegistries.ITEM.stream().filter(item -> "minecraft".equals(item.builtInRegistryHolder().key().identifier().getNamespace())));
        }

        public ItemInfoCollector(Supplier<java.util.stream.Stream<Item>> known) {
            this.known = known;
        }

        @Override
        public void accept(final Item item, final ItemModel.Unbaked model, final ClientItem.Properties properties) {
            this.register(item, new ClientItem(model, properties));
        }

        private void register(final Item item, final ClientItem itemInfo) {
            ClientItem prev = this.itemInfos.put(item, itemInfo);
            if (prev != null) {
                throw new IllegalStateException("Duplicate item model definition for " + item);
            }
        }

        @Override
        public void copy(final Item donor, final Item acceptor) {
            this.copies.put(acceptor, donor);
        }

        public void generateDefaultBlockModels() {
            BuiltInRegistries.ITEM.forEach(item -> {
                if (!this.copies.containsKey(item)) {
                    if (item instanceof BlockItem blockItem && !this.itemInfos.containsKey(blockItem)) {
                        Identifier targetModel = ModelLocationUtils.getModelLocation(blockItem.getBlock());
                        this.accept(blockItem, ItemModelUtils.plainModel(targetModel));
                    }
                }
            });
        }
        public void finalizeAndValidate() {
            this.copies.forEach((acceptor, donor) -> {
                ClientItem donorInfo = this.itemInfos.get(donor);
                if (donorInfo == null) {
                    throw new IllegalStateException("Missing donor: " + donor + " -> " + acceptor);
                } else {
                    this.register(acceptor, donorInfo);
                }
            });
            List<Identifier> missingDefinitions = known.get()
                .map(item -> item.builtInRegistryHolder())
                .filter(e -> !this.itemInfos.containsKey(e.value()))
                .map(e -> e.key().identifier())
                .toList();
            if (!missingDefinitions.isEmpty()) {
                throw new IllegalStateException("Missing item model definitions for: " + missingDefinitions);
            }
        }

        public CompletableFuture<?> save(final CachedOutput cache, final PackOutput.PathProvider pathProvider) {
            return DataProvider.saveAll(cache, ClientItem.CODEC, item -> pathProvider.json(item.builtInRegistryHolder().key().identifier()), this.itemInfos);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class SimpleModelCollector implements BiConsumer<Identifier, ModelInstance> {
        private final Map<Identifier, ModelInstance> models = new HashMap<>();

        public void accept(final Identifier id, final ModelInstance contents) {
            Supplier<JsonElement> prev = this.models.put(id, contents);
            if (prev != null) {
                throw new IllegalStateException("Duplicate model definition for " + id);
            }
        }

        public CompletableFuture<?> save(final CachedOutput cache, final PackOutput.PathProvider pathProvider) {
            return DataProvider.saveAll(cache, Supplier::get, pathProvider::json, this.models);
        }
    }
}
