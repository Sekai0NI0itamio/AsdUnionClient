/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.gui;

import net.asd.union.event.EventManager;
import net.asd.union.event.SessionUpdateEvent;
import net.asd.union.handler.sessiontabs.ClientTabManager;
import net.asd.union.handler.network.ConnectToRouter;
import net.asd.union.ui.client.altmanager.GuiAltManager;
import net.asd.union.ui.client.gui.GuiClientFixes;
import net.asd.union.ui.client.gui.GuiConnectToRouter;
import net.asd.union.utils.client.ClientUtils;
import net.asd.union.utils.client.ServerPingController;
import net.asd.union.utils.kotlin.RandomUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.Session;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Mixin(value = GuiMultiplayer.class, priority = 1001)
public abstract class MixinGuiMultiplayer extends MixinGuiScreen {
    @Shadow
    private ServerSelectionList serverListSelector;

    private GuiTextField accountTextField;
    private GuiButton connectToRouterButton;
    private boolean joinGuardActive;
    private int accountBarY = 8;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void initGui(CallbackInfo callbackInfo) {
        int topInset = ClientTabManager.INSTANCE.contentTop((GuiScreen) (Object) this);
        GuiButton button = buttonList.stream().filter(b -> "ViaForge".equals(b.displayString)).findFirst().orElse(null);

        int yPosition = topInset + 8;
        int fixesX = 5;

        if (button != null) {
            button.yPosition = yPosition;
            fixesX = button.xPosition + button.width + 5;
        }

        accountBarY = yPosition;

        buttonList.add(new GuiButton(997, fixesX, yPosition, 45, 20, "Fixes"));
        buttonList.add(new GuiButton(999, width - 104, yPosition, 98, 20, "Alt Manager"));

        int randX = fixesX + 50;
        buttonList.add(new GuiButton(1000, randX, yPosition, 45, 20, "Rand"));

        int fieldX = randX + 50;
        accountTextField = new GuiTextField(2000, fontRendererObj, fieldX, yPosition, 140, 20);
        accountTextField.setMaxStringLength(32);

        Session session = mc.getSession();
        accountTextField.setText(session == null ? "" : session.getUsername());

        int rightLimit = width - 104 - 5;
        int routerButtonX = accountTextField.xPosition + accountTextField.width + 6;
        int routerButtonWidth = Math.min(90, rightLimit - routerButtonX);

        if (routerButtonWidth < 60) {
            routerButtonWidth = 60;
            routerButtonX = Math.max(5, rightLimit - routerButtonWidth);
        }

        connectToRouterButton = new GuiButton(1001, routerButtonX, yPosition, routerButtonWidth, 20, "Router");
        buttonList.add(connectToRouterButton);

        if (serverListSelector != null) {
            serverListSelector.top = Math.max(serverListSelector.top, accountBarY + 28);
        }

        removePlayMultiplayerButton();
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"))
    private void actionPerformed(GuiButton button, CallbackInfo callbackInfo) throws IOException {
        if (isRefreshButton(button)) {
            ConnectToRouter.UltraFastRefreshResult result = ConnectToRouter.INSTANCE.ultraFastRefreshServerPing();

            if (result.getChanged()) {
                String oldIp = result.getPreviousIp().isEmpty() ? "unknown" : result.getPreviousIp();
                String newIp = result.getCurrentIp().isEmpty() ? "unknown" : result.getCurrentIp();
                ClientUtils.INSTANCE.displayAlert("Ultra Fast Refresh: ping IP changed " + oldIp + " -> " + newIp);
            }
        }

        switch (button.id) {
            case 997:
                mc.displayGuiScreen(new GuiClientFixes((GuiScreen) (Object) this));
                break;
            case 999:
                mc.displayGuiScreen(new GuiAltManager((GuiScreen) (Object) this));
                break;
            case 1000:
                if (accountTextField != null) {
                    String randomName = RandomUtils.INSTANCE.randomAccount().getName();
                    accountTextField.setText(randomName);
                }
                break;
            case 1001:
                mc.displayGuiScreen(new GuiConnectToRouter((GuiScreen) (Object) this));
                break;
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void mouseClicked(int mouseX, int mouseY, int mouseButton, CallbackInfo callbackInfo) {
        if (accountTextField != null) {
            accountTextField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Inject(method = "keyTyped", at = @At("HEAD"))
    private void keyTyped(char typedChar, int keyCode, CallbackInfo callbackInfo) {
        if (accountTextField == null) {
            return;
        }

        accountTextField.textboxKeyTyped(typedChar, keyCode);

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            setOfflineAccount(accountTextField.getText().trim());
        }
    }

    @Inject(method = "updateScreen", at = @At("HEAD"))
    private void updateScreen(CallbackInfo callbackInfo) {
        if (accountTextField != null) {
            accountTextField.updateCursorCounter();
        }
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void drawAccountSwitcher(int mouseX, int mouseY, float partialTicks, CallbackInfo callbackInfo) {
        if (accountTextField == null) {
            return;
        }

        int padding = 2;
        Gui.drawRect(
                accountTextField.xPosition - padding,
                accountBarY - padding,
                accountTextField.xPosition + accountTextField.width + padding,
                accountBarY + accountTextField.height + padding,
                Integer.MIN_VALUE
        );
        accountTextField.drawTextBox();
        if (connectToRouterButton != null) {
            connectToRouterButton.displayString = ConnectToRouter.INSTANCE.getEnabled() ? "Router: On" : "Router: Off";
        }
    }

    @Inject(method = "func_146791_a", at = @At("HEAD"))
    private void connectToServer(ServerData serverData, CallbackInfo callbackInfo) {
        joinGuardActive = true;
        ServerPingController.beginNewRefreshCycle();
    }

    @Redirect(
            method = "drawScreen",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiScreen;drawCenteredString(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V"
            ),
            require = 0
    )
    private void suppressMultiplayerTitle(GuiScreen instance, net.minecraft.client.gui.FontRenderer fontRenderer, String text, int x, int y, int color) {
        if (shouldSuppressPlayMultiplayer(text)) {
            return;
        }

        instance.drawCenteredString(fontRenderer, text, x, y, color);
    }

    @Redirect(
            method = "drawScreen",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I"
            ),
            require = 0
    )
    private int suppressPlayMultiplayerText(net.minecraft.client.gui.FontRenderer fontRenderer, String text, int x, int y, int color) {
        if (shouldSuppressPlayMultiplayer(text)) {
            return 0;
        }

        return fontRenderer.drawString(text, x, y, color);
    }

    @Inject(method = "onGuiClosed", at = @At("HEAD"))
    private void onGuiClosed(CallbackInfo callbackInfo) {
        if (joinGuardActive) {
            joinGuardActive = false;
            return;
        }

        ServerPingController.beginNewRefreshCycle();
    }

    private boolean isRefreshButton(GuiButton button) {
        if (button == null) {
            return false;
        }

        if (button.id == 8) {
            return true;
        }

        String refreshText = I18n.format("selectServer.refresh");
        return refreshText.equals(button.displayString);
    }

    private boolean shouldSuppressPlayMultiplayer(String text) {
        if (text == null) {
            return false;
        }

        return I18n.format("multiplayer.title").equals(text) || "Play Multiplayer".equals(text);
    }

    private void removePlayMultiplayerButton() {
        String titleText = I18n.format("multiplayer.title");
        buttonList.removeIf(button ->
                button != null
                        && button.displayString != null
                        && (titleText.equals(button.displayString) || "Play Multiplayer".equals(button.displayString))
        );
    }

    private void setOfflineAccount(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }

        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        mc.session = new Session(username, uuid.toString(), "-", "legacy");
        EventManager.INSTANCE.call(SessionUpdateEvent.INSTANCE);

        if (accountTextField != null) {
            accountTextField.setText(username);
        }
    }
}
