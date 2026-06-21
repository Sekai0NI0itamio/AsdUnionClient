/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import net.asd.union.event.EventManager;
import net.asd.union.event.EventState;
import net.asd.union.event.PacketEvent;
import net.asd.union.handler.network.ConnectToRouter;
import net.asd.union.handler.network.optimized.OptimizedCompressionDecoder;
import net.asd.union.handler.network.optimized.OptimizedCompressionEncoder;
import net.asd.union.handler.network.optimized.OptimizedEncryptionDecoder;
import net.asd.union.handler.network.optimized.OptimizedEncryptionEncoder;
import net.asd.union.handler.network.optimized.OptimizedFlowControlHandler;
import net.asd.union.handler.network.optimized.OptimizedFrameDecoder;
import net.asd.union.handler.network.optimized.OptimizedFrameEncoder;
import net.asd.union.handler.sessiontabs.LiveTabRuntime;
import net.asd.union.handler.sessiontabs.LiveTabRuntimeManager;
import net.asd.union.handler.sessiontabs.SessionRuntimeScope;
import net.asd.union.utils.client.ServerPingController;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.util.CryptManager;
import net.minecraft.util.IChatComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.crypto.SecretKey;
import java.net.InetAddress;

@Mixin(NetworkManager.class)
public class MixinNetworkManager {
    private static final Logger ROUTER_LOG = LogManager.getLogger("ConnectToRouter");

    @Shadow
    private Channel channel;

    @Shadow
    private INetHandler packetListener;

    @Shadow
    private IChatComponent terminationReason;

    @Shadow
    private boolean disconnected;

    @Shadow
    private boolean isEncrypted;

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

    @Inject(method = "channelActive", at = @At("RETURN"))
    private void optimizePipeline(ChannelHandlerContext context, CallbackInfo callbackInfo) {
        if (this.channel == null) return;

        ChannelPipeline pipeline = this.channel.pipeline();

        if (pipeline.get("splitter") != null && !(pipeline.get("splitter") instanceof OptimizedFrameDecoder)) {
            pipeline.replace("splitter", "splitter", new OptimizedFrameDecoder());
        }

        if (pipeline.get("prepender") != null && !(pipeline.get("prepender") instanceof OptimizedFrameEncoder)) {
            pipeline.replace("prepender", "prepender", new OptimizedFrameEncoder());
        }

        if (pipeline.get("flow_control") == null && pipeline.get("decoder") != null) {
            pipeline.addBefore("decoder", "flow_control", new OptimizedFlowControlHandler());
        }

        // Enable auto-read for maximum throughput — ensures the channel
        // continuously reads inbound data without waiting for read() calls.
        channel.config().setAutoRead(true);
    }

    @Inject(method = "channelActive", at = @At("HEAD"), cancellable = true)
    private void cancelLateChannelActivation(ChannelHandlerContext context, CallbackInfo callbackInfo) {
        if (disconnected) {
            context.close();
            callbackInfo.cancel();
        }
    }

    @Inject(method = "checkDisconnected", at = @At("HEAD"), cancellable = true)
    private void fdp$preventCheckDisconnectedOnSimThread(CallbackInfo ci) {
        if (Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            // On simulation threads, we must NOT let checkDisconnected() call
            // onDisconnect() on the handler, because that calls mc.loadWorld(null)
            // which corrupts the mc singleton state.
            //
            // IMPORTANT: We also must NOT call runtime.markDisconnectedScreen()
            // here because that destroys the world/player state (sets them to null),
            // which causes the sim thread to lose all entity data and stop
            // processing packets. Instead, we just mark connected=false and let
            // the sim thread's own checkDisconnection() handle it gracefully
            // (it only logs a warning and doesn't destroy state).
            if (!this.disconnected && this.packetListener != null) {
                net.asd.union.handler.sessiontabs.LiveTabRuntime runtime =
                    LiveTabRuntimeManager.INSTANCE.findRuntimeForHandler(this.packetListener);
                if (runtime != null && runtime.getConnected()) {
                    runtime.setConnected(false);
                    // Don't destroy world/player state — the sim thread needs it
                    // to continue processing packets. The sim thread's own
                    // checkDisconnection() will detect the closed channel and
                    // handle it without destroying state.
                    net.asd.union.utils.client.ClientUtils.INSTANCE.getLOGGER().warn(
                        "[TabSim][{}] checkDisconnected detected closed channel on sim thread, marking disconnected (preserving world state)",
                        runtime.debugLabel());
                }
            }
            // Always set the disconnected flag to prevent repeated calls
            this.disconnected = true;
            ci.cancel();
        }
    }

    @Inject(method = "closeChannel", at = @At("HEAD"), cancellable = true)
    private void cancelCloseWithoutChannel(IChatComponent reason, CallbackInfo callbackInfo) {
        if (channel == null) {
            disconnected = true;
            terminationReason = reason;
            packetListener = null;
            callbackInfo.cancel();
            return;
        }

        // If on a simulation thread, let the simulation thread's disconnect handling take over
        if (Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            if (packetListener != null) {
                net.asd.union.handler.sessiontabs.LiveTabRuntime runtime =
                    LiveTabRuntimeManager.INSTANCE.findRuntimeForHandler(packetListener);
                if (runtime != null && runtime.getDisconnectedReason() == null) {
                    runtime.markDisconnectedScreen(reason);
                    // Notify the multi-select queue that this tab has disconnected.
                    // Without this, the queue never advances when a background tab
                    // is disconnected during the login phase.
                    net.asd.union.handler.sessiontabs.MultiSelectJoinQueue.INSTANCE.onTabDisconnected(runtime.getTabId());
                    callbackInfo.cancel();
                }
            }
            return;
        }

        if (!SessionRuntimeScope.INSTANCE.isDetachedContextActive() && packetListener != null) {
            LiveTabRuntime runtime = LiveTabRuntimeManager.INSTANCE.findRuntimeForHandler(packetListener);
            if (runtime != null && runtime.getDisconnectedReason() == null) {
                LiveTabRuntimeManager.INSTANCE.scheduleRuntimeDisconnect(runtime, reason);
                callbackInfo.cancel();
            }
        }
    }

    @Inject(method = "enableEncryption", at = @At("HEAD"), cancellable = true)
    private void useOptimizedEncryption(SecretKey key, CallbackInfo ci) {
        if (this.channel == null) return;

        this.isEncrypted = true;
        this.channel.pipeline().addBefore("splitter", "decrypt",
                new OptimizedEncryptionDecoder(CryptManager.createNetCipherInstance(2, key)));
        this.channel.pipeline().addBefore("prepender", "encrypt",
                new OptimizedEncryptionEncoder(CryptManager.createNetCipherInstance(1, key)));

        ci.cancel();
    }

    @Inject(method = "setCompressionTreshold", at = @At("HEAD"), cancellable = true)
    private void useOptimizedCompression(int threshold, CallbackInfo ci) {
        if (this.channel == null) return;

        if (threshold >= 0) {
            Object decompressHandler = this.channel.pipeline().get("decompress");
            if (decompressHandler instanceof OptimizedCompressionDecoder) {
                ((OptimizedCompressionDecoder) decompressHandler).setCompressionThreshold(threshold);
            } else {
                this.channel.pipeline().addBefore("decoder", "decompress",
                        new OptimizedCompressionDecoder(threshold));
            }

            Object compressHandler = this.channel.pipeline().get("compress");
            if (compressHandler instanceof OptimizedCompressionEncoder) {
                ((OptimizedCompressionEncoder) compressHandler).setCompressionThreshold(threshold);
            } else {
                this.channel.pipeline().addBefore("encoder", "compress",
                        new OptimizedCompressionEncoder(threshold));
            }
        } else {
            if (this.channel.pipeline().get("decompress") != null) {
                this.channel.pipeline().remove("decompress");
            }
            if (this.channel.pipeline().get("compress") != null) {
                this.channel.pipeline().remove("compress");
            }
        }

        ci.cancel();
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
            // LOGIN-state packets must NOT be routed to the simulation thread.
            // The Netty pipeline's PacketDecoder needs handleLoginSuccess to call
            // networkManager.setConnectionState(EnumConnectionState.PLAY) synchronously
            // on the Netty I/O thread. If login packets are processed asynchronously
            // on the sim thread, there's a race condition — the Netty pipeline receives
            // PLAY-state packets before the state transition happens, causing
            // "Bad packet id 99".
            //
            // Instead, let LOGIN-state packets go through the vanilla flow. The
            // MixinNetHandlerLoginClient guards prevent mc.displayGuiScreen() for
            // background tabs, and handleLoginSuccess creates a NetHandlerPlayClient
            // that will be picked up by subsequent PLAY-state packet routing.
            boolean isLoginPacket = packet instanceof net.minecraft.network.login.server.S02PacketLoginSuccess
                    || packet instanceof net.minecraft.network.login.server.S01PacketEncryptionRequest
                    || packet instanceof net.minecraft.network.login.server.S00PacketDisconnect
                    || packet instanceof net.minecraft.network.login.server.S03PacketEnableCompression;

            if (!isLoginPacket) {
                if (LiveTabRuntimeManager.INSTANCE.enqueueIncomingPacket(packetListener, packet, (NetworkManager)(Object)this)) {
                    callback.cancel();
                    return;
                }

                // If enqueueIncomingPacket failed, try to force-route using the
                // NetworkManager as the lookup key. This handles the case where the
                // handler reference doesn't match any runtime's currentHandler but
                // the NetworkManager does match a background tab.
                if (packetListener != null && LiveTabRuntimeManager.INSTANCE.forceRouteToBackgroundTab(packetListener, packet, (NetworkManager)(Object)this)) {
                    callback.cancel();
                    return;
                }

                // Safety net: if both routing methods failed but this packet belongs
                // to a background tab, drop it. Processing it on the main thread would
                // access a null clientWorldController and corrupt the active tab's state.
                if (packetListener != null && LiveTabRuntimeManager.INSTANCE.isHandlerForBackgroundTab(packetListener)) {
                    callback.cancel();
                    return;
                }

                // BULLETPROOF SAFETY NET: Compare the NetworkManager that received
                // this packet with the active tab's NetworkManager. If they don't
                // match, this packet belongs to a different tab and MUST be dropped
                // or routed to the sim thread. Processing it on the main thread would
                // corrupt the active tab's state (e.g., respawn from a background tab
                // would overwrite mc.theWorld/mc.thePlayer).
                //
                // This catches cases where nmToTabId is stale or the handler reference
                // doesn't match any runtime's currentHandler.
                if (packetListener != null && LiveTabRuntimeManager.INSTANCE.isHandlerForDifferentTab(packetListener)) {
                    // This packet belongs to a different tab — try to route it
                    // to the sim thread, or drop it if that fails.
                    net.asd.union.handler.sessiontabs.LiveTabRuntime runtime =
                        LiveTabRuntimeManager.INSTANCE.findRuntimeByNetworkManagerPublic((NetworkManager)(Object)this);
                    if (runtime != null && runtime.getSimulationThread() != null && runtime.getSimulationThread().isAlive()) {
                        @SuppressWarnings("unchecked")
                        net.minecraft.network.Packet<net.minecraft.network.INetHandler> typedPacket =
                            (net.minecraft.network.Packet<net.minecraft.network.INetHandler>) packet;
                        runtime.getSimulationThread().enqueuePacket(typedPacket);
                    }
                    // Always cancel — this packet must NOT be processed on the main thread
                    callback.cancel();
                    return;
                }

                // Additional safety net: if the handler belongs to a tab whose world
                // is not yet ready (clientWorldController is null because handleJoinGame
                // hasn't been processed yet), drop the packet. This handles the case
                // where the tab IS the active tab but its handler was just created by
                // handleLoginSuccess — isHandlerForBackgroundTab returns false, but
                // processing the packet would still NPE on the main thread.
                if (packetListener != null && LiveTabRuntimeManager.INSTANCE.isHandlerWorldNotReady(packetListener)) {
                    callback.cancel();
                    return;
                }

                // NOTE: Do NOT add a blanket "drop if mc.thePlayer is null" check here.
                // During normal connection setup, mc.thePlayer and mc.theWorld are null
                // until S01PacketJoinGame is processed (which creates them). Dropping
                // packets in that state breaks ALL server connections.
            }

            PacketEvent event = new PacketEvent(packet, EventState.RECEIVE);
            EventManager.INSTANCE.call(event);
            if (event.isCancelled()) {
                callback.cancel();
            }
        }
    }

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void send(Packet<?> packet, CallbackInfo callback) {
        // Skip packet event processing for simulation threads — background tabs
        // should not trigger module event handlers (KillAura, NoRotateSet, etc.)
        // as they reference mc.thePlayer and other active-tab state.
        if (Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            return;
        }

        boolean isServerThread = isServerPingerThread();
        if (!isServerThread) {
            PacketEvent event = new PacketEvent(packet, EventState.SEND);
            EventManager.INSTANCE.call(event);
            if (event.isCancelled()) {
                callback.cancel();
            }
        }
    }
}
