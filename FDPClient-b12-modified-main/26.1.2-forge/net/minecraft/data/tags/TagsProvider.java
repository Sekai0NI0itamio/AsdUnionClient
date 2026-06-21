package net.minecraft.data.tags;

import com.google.common.collect.Maps;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;

public abstract class TagsProvider<T> implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;
    private final CompletableFuture<HolderLookup.Provider> lookupProvider;
    private final CompletableFuture<Void> contentsDone = new CompletableFuture<>();
    private final CompletableFuture<TagsProvider.TagLookup<T>> parentProvider;
    protected final ResourceKey<? extends Registry<T>> registryKey;
    protected final Map<Identifier, TagBuilder> builders = Maps.newLinkedHashMap();
    protected final String modId;
    @org.jetbrains.annotations.Nullable
    protected final net.minecraftforge.common.data.ExistingFileHelper existingFileHelper;
    private final net.minecraftforge.common.data.ExistingFileHelper.IResourceType resourceType;
    private final net.minecraftforge.common.data.ExistingFileHelper.IResourceType elementResourceType; // FORGE: Resource type for validating required references to datapack registry elements.

    protected TagsProvider(
        final PackOutput output, final ResourceKey<? extends Registry<T>> registryKey, final CompletableFuture<HolderLookup.Provider> lookupProvider
    ) {
        this(output, registryKey, lookupProvider, "vanilla", null);
    }

    protected TagsProvider(final PackOutput output, final ResourceKey<? extends Registry<T>> registryKey, final CompletableFuture<HolderLookup.Provider> lookupProvider, String modId, @org.jetbrains.annotations.Nullable net.minecraftforge.common.data.ExistingFileHelper existingFileHelper) {
       this(output, registryKey, lookupProvider, CompletableFuture.completedFuture(TagsProvider.TagLookup.empty()), modId, existingFileHelper);
    }

    protected TagsProvider(PackOutput output, ResourceKey<? extends Registry<T>> registryKey, CompletableFuture<HolderLookup.Provider> lookupProvider, CompletableFuture<TagsProvider.TagLookup<T>> parentProvider) {
        this(output, registryKey, lookupProvider, parentProvider, "vanilla", null);
    }

    protected TagsProvider(
        final PackOutput output,
        final ResourceKey<? extends Registry<T>> registryKey,
        final CompletableFuture<HolderLookup.Provider> lookupProvider,
        final CompletableFuture<TagsProvider.TagLookup<T>> parentProvider,
        final String modId,
        final @org.jetbrains.annotations.Nullable net.minecraftforge.common.data.ExistingFileHelper existingFileHelper
    ) {
        this.pathProvider = output.createRegistryTagsPathProvider(registryKey);
        this.registryKey = registryKey;
        this.parentProvider = parentProvider;
        this.lookupProvider = lookupProvider;
        this.modId = modId;
        this.existingFileHelper = existingFileHelper;
        this.resourceType = new net.minecraftforge.common.data.ExistingFileHelper.ResourceType(net.minecraft.server.packs.PackType.SERVER_DATA, ".json", net.minecraft.core.registries.Registries.tagsDirPath(registryKey));
        this.elementResourceType = new net.minecraftforge.common.data.ExistingFileHelper.ResourceType(net.minecraft.server.packs.PackType.SERVER_DATA, ".json", net.minecraft.core.registries.Registries.elementsDirPath(registryKey));
    }

    // Forge: Allow customizing the path for a given tag or returning null
    @org.jetbrains.annotations.Nullable
    protected Path getPath(Identifier id) {
        return this.pathProvider.json(id);
    }

    @Override
    public String getName() {
        return "Tags for " + this.registryKey.identifier() + " mod id " + this.modId;
    }

    protected abstract void addTags(HolderLookup.Provider registries);

    @Override
    public CompletableFuture<?> run(final CachedOutput cache) {
        record CombinedData<T>(HolderLookup.Provider contents, TagsProvider.TagLookup<T> parent) {
        }

        return this.createContentsProvider()
            .thenApply(provider -> {
                this.contentsDone.complete(null);
                return (HolderLookup.Provider)provider;
            })
            .thenCombineAsync(this.parentProvider, (x$0, x$1) -> new CombinedData<>(x$0, (TagsProvider.TagLookup<T>)x$1), Util.backgroundExecutor())
            .thenCompose(
                c -> {
                    HolderLookup.RegistryLookup<T> lookup = c.contents.lookup(this.registryKey).orElseThrow(() -> {
                       // FORGE: Throw a more descriptive error message if this is a Forge registry without tags enabled
                       if (net.minecraftforge.registries.RegistryManager.ACTIVE.getRegistry(this.registryKey) != null) {
                          return new IllegalStateException("Forge registry " + this.registryKey.identifier() + " does not have support for tags");
                       }
                       return new IllegalStateException("Registry " + this.registryKey.identifier() + " not found");
                    });
                    Predicate<Identifier> elementCheck = id -> lookup.get(ResourceKey.create(this.registryKey, id)).isPresent();
                    Predicate<Identifier> tagCheck = id -> this.builders.containsKey(id) || c.parent.contains(TagKey.create(this.registryKey, id));
                    return CompletableFuture.allOf(
                        this.builders
                            .entrySet()
                            .stream()
                            .<CompletableFuture<?>>map(
                                entry -> {
                                    Identifier id = entry.getKey();
                                    TagBuilder builder = entry.getValue();
                                    List<TagEntry> entries = builder.build();
                                    List<TagEntry> unresolvedEntries = java.util.stream.Stream.concat(entries.stream(), builder.getRemoveEntries()).filter(e -> !e.verifyIfPresent(elementCheck, tagCheck)).filter(this::missing).toList();
                                    if (!unresolvedEntries.isEmpty()) {
                                        throw new IllegalArgumentException(
                                            String.format(
                                                Locale.ROOT,
                                                "Couldn't define tag %s as it is missing following references: %s",
                                                id,
                                                unresolvedEntries.stream().map(Objects::toString).collect(Collectors.joining(","))
                                            )
                                        );
                                    } else {
                                        Path path = this.getPath(id);
                                        if (path == null) {
                                            return CompletableFuture.completedFuture(null); // Forge: Allow running this data provider without writing it. Recipe provider needs valid tags.
                                        }
                                        return DataProvider.saveStable(cache, c.contents, TagFile.CODEC, new TagFile(entries, builder.shouldReplace(), builder.getRemoveEntries().toList()), path);
                                    }
                                }
                            )
                            .toArray(CompletableFuture[]::new)
                    );
                }
            );
    }

    protected TagBuilder getOrCreateRawBuilder(final TagKey<T> tag) {
        return this.builders.computeIfAbsent(tag.location(), k -> {
            if (existingFileHelper != null) {
                existingFileHelper.trackGenerated(k, resourceType);
            }
            return TagBuilder.create();
        });
    }

    public CompletableFuture<TagsProvider.TagLookup<T>> contentsGetter() {
        return this.contentsDone.thenApply(ignore -> id -> Optional.ofNullable(this.builders.get(id.location())));
    }

    protected CompletableFuture<HolderLookup.Provider> createContentsProvider() {
        return this.lookupProvider.thenApply(registries -> {
            this.builders.clear();
            this.addTags(registries);
            return (HolderLookup.Provider)registries;
        });
    }


    private boolean missing(TagEntry reference) {
        // Optional tags should not be validated
        if (reference.isRequired()) {
           return existingFileHelper == null || !existingFileHelper.exists(reference.getId(), reference.isTag() ? resourceType : elementResourceType);
        }
        return false;
    }

    @FunctionalInterface
    public interface TagLookup<T> extends Function<TagKey<T>, Optional<TagBuilder>> {
        static <T> TagsProvider.TagLookup<T> empty() {
            return id -> Optional.empty();
        }

        default boolean contains(final TagKey<T> key) {
            return this.apply(key).isPresent();
        }
    }
}
