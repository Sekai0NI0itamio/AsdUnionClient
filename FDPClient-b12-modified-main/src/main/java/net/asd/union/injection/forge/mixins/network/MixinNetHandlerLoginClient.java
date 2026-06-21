package net.asd.union.injection.forge.mixins.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes session authentication and disconnect handling for background tab connections.
 *
 * When a background tab connects to a server, the login handshake is
 * asynchronous. By the time the server sends an encryption request,
 * the SessionRuntimeScope.runDetached block has already completed and
 * mc.session has been restored to the active tab's session. This mixin
 * intercepts mc.getSession() calls in NetHandlerLoginClient and returns
 * the correct session for the connecting tab based on its NetworkManager.
 *
 * Additionally, this mixin prevents onDisconnect from calling
 * mc.displayGuiScreen() for background tabs, which would corrupt the
 * active tab's screen state.
 */
@Mixin(NetHandlerLoginClient.class)
public abstract class MixinNetHandlerLoginClient {

    @Shadow
    private Minecraft mc;

    @Shadow
    private NetworkManager networkManager;

    /**
     * Intercepts mc.getSession() calls in handleEncryptionRequest.
     * When a background tab is connecting, its NetworkManager is stored
     * in the LiveTabRuntime. We look up the runtime by NetworkManager
     * and use its session instead of the mc singleton's session.
     */
    @Redirect(
            method = "handleEncryptionRequest",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getSession()Lnet/minecraft/util/Session;"),
            expect = 2
    )
    private Session fdp$useTabSession(Minecraft mcInstance) {
        // Try to find the runtime for this connection's NetworkManager
        net.asd.union.handler.sessiontabs.LiveTabRuntime runtime =
                net.asd.union.handler.sessiontabs.LiveTabRuntimeManager.INSTANCE
                        .findRuntimeByNetworkManagerPublic(this.networkManager);
        if (runtime != null && runtime.getSession() != null) {
            return runtime.getSession();
        }

        // Fallback: check if we're in a SessionRuntimeScope.runDetached block
        net.asd.union.handler.sessiontabs.LiveTabRuntime detachedRuntime =
                net.asd.union.handler.sessiontabs.SessionRuntimeScope.INSTANCE.currentRuntime();
        if (detachedRuntime != null && detachedRuntime.getSession() != null) {
            return detachedRuntime.getSession();
        }

        // Fallback to mc.session for the active tab
        return mcInstance.getSession();
    }

    /**
     * Prevents onDisconnect from calling mc.displayGuiScreen() for background tabs.
     *
     * When a background tab's login connection is disconnected (e.g., "already
     * connected to this proxy"), the vanilla onDisconnect calls
     * mc.displayGuiScreen(new GuiDisconnected(...)), which corrupts the
     * active tab's screen. For background tabs, we mark the runtime as
     * disconnected instead.
     */
    @Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
    private void fdp$preventDisconnectScreenForBackgroundTab(IChatComponent reason, CallbackInfo ci) {
        net.asd.union.handler.sessiontabs.LiveTabRuntime runtime =
                net.asd.union.handler.sessiontabs.LiveTabRuntimeManager.INSTANCE
                        .findRuntimeByNetworkManagerPublic(this.networkManager);

        if (runtime != null && runtime.getTabId() != null
                && runtime.getTabId() != net.asd.union.handler.sessiontabs.LiveTabRuntimeManager.INSTANCE.getActiveRuntimeTabId()) {
            // This is a background tab — don't call mc.displayGuiScreen()
            runtime.setDisconnectedReason(reason != null ? reason.getUnformattedText() : null);
            runtime.setConnected(false);
            // Notify the multi-select queue that this tab has disconnected
            net.asd.union.handler.sessiontabs.MultiSelectJoinQueue.INSTANCE.onTabDisconnected(runtime.getTabId());
            ci.cancel();
        }
    }
}
