/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import java.net.InetAddress;
import net.asd.union.event.EventManager;
import net.asd.union.event.EventState;
import net.asd.union.event.PacketEvent;
import net.asd.union.handler.network.ConnectToRouter;
import net.asd.union.utils.client.ServerPingController;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkManager.class)
public class MixinNetworkManager {
    private static final Logger ROUTER_LOG = LogManager.getLogger("ConnectToRouter");

    private static boolean isServerPingerThread() {
        return ServerPingController.isServerPingerThread();
    }

    @Redirect(
            method = "createNetworkManagerAndConnect",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;",
                    remap = false
            )
    )
    private static ChannelFuture redirectConnect(Bootstrap bootstrap, InetAddress address, int port) {
        if (isServerPingerThread()) {
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, ServerPingController.getConnectTimeoutMillis());
        }

        boolean shouldUseTunnel = ConnectToRouter.INSTANCE.getEnabled()
                && (ConnectToRouter.INSTANCE.isTunnelMode() || ConnectToRouter.INSTANCE.getTunnelAvailable());

        if (shouldUseTunnel) {
            try {
                InetAddress loopback = InetAddress.getByName("127.0.0.1");
                int tunnelPort = 25560;
                String realTarget = address != null ? address.getHostAddress() : "unresolved";
                ROUTER_LOG.info("Routing via tunnel 127.0.0.1:" + tunnelPort + "  (real target " + realTarget + ":" + port + ")");
                return trackConnectFuture(bootstrap.connect(loopback, tunnelPort));
            } catch (Exception e) {
                ROUTER_LOG.warn("Tunnel connect failed, falling back: " + e.getMessage());
            }
        }

        if (address != null && port > 0 && port <= 65535) {
            InetAddress localAddress = ConnectToRouter.INSTANCE.getPreferredLocalAddressFor(address);
            if (localAddress != null) {
                ROUTER_LOG.info("Binding to " + localAddress.getHostAddress());
                return trackConnectFuture(bootstrap.localAddress(localAddress, 0).connect(address, port));
            }
            return trackConnectFuture(bootstrap.connect(address, port));
        }

        return failedConnectFuture(bootstrap, address, port);
    }

    @Redirect(
            method = "createNetworkManagerAndConnect",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/netty/channel/ChannelFuture;syncUninterruptibly()Lio/netty/channel/ChannelFuture;",
                    remap = false
            )
    )
    private static ChannelFuture redirectSyncUninterruptibly(ChannelFuture future) {
        if (isServerPingerThread()) {
            return future;
        }

        return future.syncUninterruptibly();
    }

    private static ChannelFuture failedConnectFuture(Bootstrap bootstrap, InetAddress address, int port) {
        String message = "Invalid remote endpoint for connect: " + address + ":" + port;
        ROUTER_LOG.warn(message);

        try {
            return trackConnectFuture(bootstrap.connect(InetAddress.getByName("127.0.0.1"), 9));
        } catch (Exception e) {
            return trackConnectFuture(bootstrap.connect(InetAddress.getLoopbackAddress(), 9));
        }
    }

    private static ChannelFuture trackConnectFuture(ChannelFuture future) {
        if (isServerPingerThread()) {
            ServerPingController.registerConnectFuture(future);

            if (ServerPingController.shouldCancelCurrentPing()) {
                future.cancel(true);
            }
        }

        return future;
    }

    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
    private void read(ChannelHandlerContext context, Packet<?> packet, CallbackInfo callback) {
        if (!isServerPingerThread()) {
            PacketEvent event = new PacketEvent(packet, EventState.RECEIVE);
            EventManager.INSTANCE.call(event);
            if (event.isCancelled()) {
                callback.cancel();
            }
        }
    }

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void send(Packet<?> packet, CallbackInfo callback) {
        if (!isServerPingerThread()) {
            PacketEvent event = new PacketEvent(packet, EventState.SEND);
            EventManager.INSTANCE.call(event);
            if (event.isCancelled()) {
                callback.cancel();
            }
        }
    }
}
