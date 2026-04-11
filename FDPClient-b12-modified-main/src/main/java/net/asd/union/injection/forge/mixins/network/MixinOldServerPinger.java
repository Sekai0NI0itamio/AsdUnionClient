package net.asd.union.injection.forge.mixins.network;

import io.netty.channel.Channel;
import net.minecraft.client.network.OldServerPinger;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.List;

@Mixin(OldServerPinger.class)
public abstract class MixinOldServerPinger {

    @Shadow
    @Final
    private List<NetworkManager> pingDestinations;

    @Inject(method = "clearPendingNetworks", at = @At("HEAD"), cancellable = true)
    private void fdp$clearPendingNetworksAsync(CallbackInfo ci) {
        synchronized (this.pingDestinations) {
            Iterator<NetworkManager> iterator = this.pingDestinations.iterator();

            while (iterator.hasNext()) {
                NetworkManager networkManager = iterator.next();
                iterator.remove();

                Channel channel = networkManager.channel();
                if (channel != null && channel.isOpen()) {
                    channel.close();
                }
            }
        }

        ci.cancel();
    }
}
