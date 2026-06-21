package net.minecraft.world.level;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

public class DataPackConfig {
    public static final DataPackConfig DEFAULT = new DataPackConfig(ImmutableList.of("vanilla"), ImmutableList.of());
    public static final Codec<DataPackConfig> CODEC = RecordCodecBuilder.create(
        i -> i.group(Codec.STRING.listOf().fieldOf("Enabled").forGetter(o -> o.enabled), Codec.STRING.listOf().fieldOf("Disabled").forGetter(o -> o.disabled))
            .apply(i, DataPackConfig::new)
    );
    private final List<String> enabled;
    private final List<String> disabled;

    public DataPackConfig(final List<String> enabled, final List<String> disabled) {
        this.enabled = new java.util.ArrayList<>(enabled);
        this.disabled = ImmutableList.copyOf(disabled);
    }

    public List<String> getEnabled() {
        return this.enabled;
    }

    public List<String> getDisabled() {
        return this.disabled;
    }

    public void addModPacks(List<String> modPacks) {
        enabled.addAll(modPacks.stream().filter(p -> !enabled.contains(p)).toList());
    }
}
