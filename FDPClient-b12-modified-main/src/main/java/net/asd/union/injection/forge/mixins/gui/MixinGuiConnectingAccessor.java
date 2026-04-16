package net.asd.union.injection.forge.mixins.gui;

import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiConnecting.class)
public interface MixinGuiConnectingAccessor {

    @Accessor("networkManager")
    NetworkManager getNetworkManager();
}
