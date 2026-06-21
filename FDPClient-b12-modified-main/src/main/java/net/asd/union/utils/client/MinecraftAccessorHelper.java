package net.asd.union.utils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;

/**
 * Static helper for accessing the private "myNetworkManager" field on the
 * Minecraft singleton via the MixinMinecraftAccessor mixin.
 *
 * <p>This class is intentionally placed OUTSIDE the mixin package
 * ({@code net.asd.union.injection.forge.mixins.*}). Spongepowered Mixin
 * 0.7.11 considers any class whose fully-qualified name starts with the
 * mixin config's {@code package} value to be a "potential mixin" and will
 * throw {@code NoClassDefFoundError: "X is a mixin class and cannot be
 * referenced directly"} if non-mixin code references it. Keeping this helper
 * in a non-mixin package side-steps that check entirely.
 */
public class MinecraftAccessorHelper {
    public static NetworkManager getMyNetworkManager() {
        return ((net.asd.union.injection.forge.mixins.client.MixinMinecraftAccessor) Minecraft.getMinecraft()).getMyNetworkManager();
    }

    public static void setMyNetworkManager(NetworkManager networkManager) {
        ((net.asd.union.injection.forge.mixins.client.MixinMinecraftAccessor) Minecraft.getMinecraft()).setMyNetworkManager(networkManager);
    }
}
