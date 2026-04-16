/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.gui;

import net.asd.union.features.module.modules.client.HUDModule;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Shadow;

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
