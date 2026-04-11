package net.asd.union.injection.forge.mixins.network;

import io.netty.channel.Channel;
import net.minecraft.client.network.OldServerPinger;
import net.minecraft.network.NetworkManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    private static final Logger PING_LOG = LogManager.getLogger("ServerPingController");

    @Shadow
    @Final
    private List<NetworkManager> pingDestinations;

    @Inject(method = "pingPendingNetworks", at = @At("HEAD"), cancellable = true)
    private void fdp$pingPendingNetworksSafely(CallbackInfo ci) {
        synchronized (this.pingDestinations) {
            Iterator<NetworkManager> iterator = this.pingDestinations.iterator();

            while (iterator.hasNext()) {
                NetworkManager networkManager = iterator.next();

                try {
                    if (networkManager.isChannelOpen()) {
                        networkManager.processReceivedPackets();
                    } else {
                        iterator.remove();
                        networkManager.checkDisconnected();
                    }
                } catch (Throwable throwable) {
                    iterator.remove();

                    Channel channel = networkManager.channel();
                    if (channel != null && channel.isOpen()) {
                        channel.close();
                    }

                    PING_LOG.warn("Dropped a broken server ping request instead of crashing the multiplayer screen.", throwable);
                }
            }
        }

        ci.cancel();
    }

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
