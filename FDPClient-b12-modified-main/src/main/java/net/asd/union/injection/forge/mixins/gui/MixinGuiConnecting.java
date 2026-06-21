/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.gui;

import net.asd.union.features.module.modules.client.HUDModule;
import net.asd.union.handler.network.ConnectToRouter;
import net.asd.union.handler.sessiontabs.ClientTabManager;
import net.asd.union.ui.font.Fonts;
import net.asd.union.utils.client.ServerUtils;
import net.asd.union.utils.render.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Shadow;

import java.net.InetAddress;

@Mixin(GuiConnecting.class)
@SideOnly(Side.CLIENT)
public abstract class MixinGuiConnecting extends GuiScreen {

    @Shadow
    private NetworkManager networkManager;

    @Shadow
    private boolean cancel;

    @Inject(method = "connect", at = @At("HEAD"))
    private void headConnect(final String ip, final int port, CallbackInfo callbackInfo) {
        ServerUtils.INSTANCE.setServerData(new ServerData("", ip + ":" + port, false));
    }

    /**
     * When the tunnel is active, skip DNS resolution and use 127.0.0.1 directly.
     * The tunnel handles routing to the real server, so DNS is unnecessary and
     * may fail if the hostname is only resolvable through the tunnel's network.
     * The background tunnel health check (ConnectToRouter) keeps
     * {@code tunnelAvailable} up-to-date, so we trust that flag rather than
     * doing another round-trip here.
     */
    @Redirect(
        method = "connect",
        at = @At(
            value = "INVOKE",
            target = "Ljava/net/InetAddress;getByName(Ljava/lang/String;)Ljava/net/InetAddress;",
            remap = false
        )
    )
    private InetAddress redirectDnsResolution(String host) throws Exception {
        boolean shouldUseTunnel = ConnectToRouter.INSTANCE.getEnabled()
                && (ConnectToRouter.INSTANCE.isTunnelMode() || ConnectToRouter.INSTANCE.getTunnelAvailable());

        if (shouldUseTunnel) {
            // Trust the background health-check flag. If the tunnel dies
            // mid-connect, the Netty handshake will simply fail and the user
            // sees a normal "Connection refused" — no need to do another
            // round-trip in the redirector.
            try {
                return java.net.InetAddress.getByName("127.0.0.1");
            } catch (Exception e) {
                net.asd.union.utils.client.ClientUtils.INSTANCE.getLOGGER().warn("[GuiConnecting] Tunnel local resolve failed, falling back to direct: " + e.getMessage());
                return java.net.InetAddress.getByName(host);
            }
        }

        // No tunnel — resolve normally
        return java.net.InetAddress.getByName(host);
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void forceImmediateCancel(GuiButton button, CallbackInfo callbackInfo) {
        if (button == null || button.id != 0) {
            return;
        }

        cancel = true;

        if (networkManager != null) {
            networkManager.closeChannel(new ChatComponentText("Aborted"));
            networkManager = null;
        }

        mc.displayGuiScreen(new GuiMainMenu());
        callbackInfo.cancel();
    }

    /**
     * @author CCBlueX
     */
    @Overwrite
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        int topInset = ClientTabManager.INSTANCE.contentTop((GuiScreen) (Object) this);
        float loadingY = scaledResolution.getScaledHeight() / 4f + 70 + topInset;

        drawDefaultBackground();

        RenderUtils.INSTANCE.drawLoadingCircle(scaledResolution.getScaledWidth() / 2f, loadingY);

        String ip = "Unknown";

        final ServerData serverData = mc.getCurrentServerData();
        if (serverData != null) {
            ip = ServerUtils.INSTANCE.hideSensitiveInformation(serverData.serverIP);
        }

        Fonts.font35.drawCenteredString("Connecting to", scaledResolution.getScaledWidth() / 2f, loadingY + 40, 0xFFFFFF, true);
        Fonts.font35.drawCenteredString(ip, scaledResolution.getScaledWidth() / 2f, loadingY + 50, HUDModule.INSTANCE.getGuiColor(), true);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
