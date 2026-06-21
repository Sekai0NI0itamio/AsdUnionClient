package net.minecraft.data.registries;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;

public class RegistriesDatapackGenerator implements DataProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;
    private final java.util.function.Predicate<String> namespacePredicate;

    public RegistriesDatapackGenerator(final PackOutput output, final CompletableFuture<HolderLookup.Provider> registries) {
        this(output, registries, null);
    }

    public RegistriesDatapackGenerator(final PackOutput output, final CompletableFuture<HolderLookup.Provider> registries, @org.jetbrains.annotations.Nullable java.util.Set<String> modIds) {
        this.registries = registries;
        this.output = output;
        this.namespacePredicate = modIds == null ? namespace -> true : modIds::contains;
    }

    /** Forge: Call this to get the registry holder lookup provider that includes elements added via {@link net.minecraftforge.common.data.DatapackBuiltinEntriesProvider} */
    public CompletableFuture<HolderLookup.Provider> getRegistryProvider() {
       return registries;
    }

    @Override
    public CompletableFuture<?> run(final CachedOutput cache) {
        return this.registries
            .thenCompose(
                access -> {
                    DynamicOps<JsonElement> registryOps = access.createSerializationContext(JsonOps.INSTANCE);
                    return CompletableFuture.allOf(
                        RegistryDataLoader.getWorldGenAndDimensionStream()
                            .flatMap(v -> this.dumpRegistryCap(cache, access, registryOps, (RegistryDataLoader.RegistryData<?>)v).stream())
                            .toArray(CompletableFuture[]::new)
                    );
                }
            );
    }

    private <T> Optional<CompletableFuture<?>> dumpRegistryCap(
        final CachedOutput cache, final HolderLookup.Provider registries, final DynamicOps<JsonElement> writeOps, final RegistryDataLoader.RegistryData<T> v
    ) {
        ResourceKey<? extends Registry<T>> registryKey = v.key();
        return registries.lookup(registryKey)
            .map(
                registry -> {
                    PackOutput.PathProvider pathProvider = this.output.createRegistryElementsPathProvider(registryKey);
                    return CompletableFuture.allOf(
                        registry.listElements()
                            .filter(holder -> shouldDump(holder.key()))
                            .<CompletableFuture<?>>map(e -> dumpValue(pathProvider.json(e.key().identifier()), cache, writeOps, v.elementCodec(), e.value()))
                            .toArray(CompletableFuture[]::new)
                    );
                }
            );
    }

    protected boolean shouldDump(ResourceKey<?> key) {
        return this.namespacePredicate.test(key.identifier().getNamespace());
    }

    private static <E> CompletableFuture<?> dumpValue(
        final Path path, final CachedOutput cache, final DynamicOps<JsonElement> ops, final Encoder<E> codec, final E value
    ) {
        return codec.encodeStart(ops, value)
            .mapOrElse(
                result -> DataProvider.saveStable(cache, result, path),
                error -> CompletableFuture.failedFuture(new IllegalStateException("Couldn't generate file '" + path + "': " + error.message()))
            );
    }

    @Override
    public String getName() {
        return "Registries";
    }
}
