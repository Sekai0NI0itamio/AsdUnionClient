package net.asd.union.injection.forge.mixins.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for Minecraft.myNetworkManager — needed by the tab system to
 * null the active network manager when activating a tab with no restorable
 * state. Without nulling it, runTick() calls processReceivedPackets() on
 * the dead tab's NM, which NPEs because mc.thePlayer is null.
 */
@Mixin(Minecraft.class)
public interface MixinMinecraftAccessor {

    @Accessor("myNetworkManager")
    NetworkManager getMyNetworkManager();

    @Accessor("myNetworkManager")
    void setMyNetworkManager(NetworkManager networkManager);
}
