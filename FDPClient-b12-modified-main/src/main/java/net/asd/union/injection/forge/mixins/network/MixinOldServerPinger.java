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

import java.util.ArrayList;
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
        List<NetworkManager> toRemove = new ArrayList<>();
        
        synchronized (this.pingDestinations) {
            for (NetworkManager networkManager : this.pingDestinations) {
                try {
                    if (networkManager.isChannelOpen()) {
                        networkManager.processReceivedPackets();
                    } else {
                        toRemove.add(networkManager);
                    }
                } catch (Throwable throwable) {
                    toRemove.add(networkManager);

                    Channel channel = networkManager.channel();
                    if (channel != null && channel.isOpen()) {
                        channel.close();
                    }

                    PING_LOG.warn("Dropped a broken server ping request instead of crashing the multiplayer screen.", throwable);
                }
            }
            
            // Remove collected failed networks
            this.pingDestinations.removeAll(toRemove);
            for (NetworkManager nm : toRemove) {
                try {
                    nm.checkDisconnected();
                } catch (Exception ignored) {
                }
            }
        }

        ci.cancel();
    }

    @Inject(method = "clearPendingNetworks", at = @At("HEAD"), cancellable = true)
    private void fdp$clearPendingNetworksAsync(CallbackInfo ci) {
        List<Channel> channelsToClose = new ArrayList<>();
        
        synchronized (this.pingDestinations) {
            for (NetworkManager networkManager : this.pingDestinations) {
                Channel channel = networkManager.channel();
                if (channel != null && channel.isOpen()) {
                    channelsToClose.add(channel);
                }
            }
            this.pingDestinations.clear();
        }
        
        // Close channels outside synchronized block to avoid deadlock
        for (Channel channel : channelsToClose) {
            try {
                channel.close();
            } catch (Exception ignored) {
            }
        }

        ci.cancel();
    }
}
