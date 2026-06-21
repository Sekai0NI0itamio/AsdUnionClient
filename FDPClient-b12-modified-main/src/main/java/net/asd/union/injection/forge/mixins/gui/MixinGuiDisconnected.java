/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.gui;

import me.liuli.elixir.account.MinecraftAccount;
import net.asd.union.event.EventManager;
import net.asd.union.event.SessionUpdateEvent;
import net.asd.union.handler.other.AutoReconnect;
import net.asd.union.handler.payload.ClientFixes;
import net.asd.union.handler.sessiontabs.ClientTabManager;
import net.asd.union.file.FileManager;
import net.asd.union.ui.client.altmanager.GuiAltManager;
import net.asd.union.ui.client.altmanager.menus.GuiLoginProgress;
import net.asd.union.ui.client.gui.GuiInfo;
import net.asd.union.utils.io.APIConnectorUtils;
import net.asd.union.utils.client.ClientUtils;
import net.asd.union.utils.client.ServerUtils;
import net.asd.union.utils.io.MiscUtils;
import net.asd.union.utils.kotlin.RandomUtils;
import net.minecraft.client.gui.*;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.fml.client.config.GuiSlider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.List;
import java.util.Random;

@Mixin(GuiDisconnected.class)
public abstract class MixinGuiDisconnected extends MixinGuiScreen {

    @Shadow
    private int field_175353_i;

    @Shadow
    private String reason;

    @Shadow
    private IChatComponent message;

    private GuiButton reconnectButton;
    private GuiSlider autoReconnectDelaySlider;
    private GuiButton forgeBypassButton;
    private int reconnectTimer;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void initGui(CallbackInfo callbackInfo) {
        final int topInset = ClientTabManager.INSTANCE.contentTop((GuiScreen) (Object) this);
        reconnectTimer = 0;
        buttonList.add(reconnectButton = new GuiButton(1, width / 2 - 100, height / 2 + field_175353_i / 2 + fontRendererObj.FONT_HEIGHT + 22, 98, 20, "Reconnect"));
        drawReconnectDelaySlider();

        buttonList.add(new GuiButton(4, width / 2 + 2, height / 2 + field_175353_i / 2 + fontRendererObj.FONT_HEIGHT + 44, 98, 20, "Random Account"));
        buttonList.add(new GuiButton(5, this.width / 2 - 100, this.height / 2 + field_175353_i / 2 + this.fontRendererObj.FONT_HEIGHT + 66, 200, 20, "Donate Now"));
        buttonList.add(forgeBypassButton = new GuiButton(6, width / 2 - 100, height / 2 + field_175353_i / 2 + fontRendererObj.FONT_HEIGHT + 86, "Bypass AntiForge: " + (ClientFixes.INSTANCE.getFmlFixesEnabled() ? "On" : "Off")));
        // Force Disconnect button — sits next to the Reconnect button. Sends a
        // force-disconnect packet sequence to the server and closes the channel
        // to make sure the server doesn't keep the player flagged as online
        // (the "You are already logged on to this server" issue).
        buttonList.add(new GuiButton(7, width / 2 - 100, height / 2 + field_175353_i / 2 + fontRendererObj.FONT_HEIGHT + 108, 200, 20, "Force Disconnect"));
        buttonList.add(new GuiButton(998, width - 94, topInset + 5, 88, 20, "Alt Manager"));
        buttonList.add(new GuiButton(8, width / 2 - 100, height / 2 + field_175353_i / 2 + fontRendererObj.FONT_HEIGHT + 44, 98, 20, "Settings"));

        updateSliderText();

        // Print the full disconnect reason to chat so the player can see the exact cause
        printDisconnectReason();
    }

    /**
     * Prints the disconnect reason to the in-game chat.
     * Covers all disconnect scenarios: in-game kicks, login-phase rejections
     * (e.g. "Authentication required"), and connection losses.
     */
    private void printDisconnectReason() {
        // Build the full reason string from both the key and the component
        String reasonKey = (reason != null && !reason.isEmpty()) ? reason : null;
        String componentText = (message != null) ? message.getUnformattedText() : null;

        // Avoid printing if there's nothing useful
        if (reasonKey == null && componentText == null) return;

        // Print header
        ClientUtils.INSTANCE.displayChatMessage("§c§lDisconnected §8— §7reason details:");

        // Print the translation key (e.g. "disconnect.lost", "disconnect.loginFailed")
        if (reasonKey != null && !reasonKey.isEmpty()) {
            ClientUtils.INSTANCE.displayChatMessage("§8Key: §f" + reasonKey);
        }

        // Print the full formatted message text (the actual human-readable reason)
        if (componentText != null && !componentText.isEmpty()) {
            // Some reasons are multi-line — split and print each line
            String[] lines = componentText.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    ClientUtils.INSTANCE.displayChatMessage("§c" + trimmed);
                }
            }
        }

        // Also print the formatted (coloured) version if it differs from unformatted
        if (message != null) {
            String formattedText = message.getFormattedText();
            if (formattedText != null && !formattedText.isEmpty() && !formattedText.equals(componentText)) {
                ClientUtils.INSTANCE.displayChatMessage("§8Full: §r" + formattedText);
            }
        }
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"))
    private void actionPerformed(GuiButton button, CallbackInfo callbackInfo) throws IOException {
        switch (button.id) {
            case 1:
                ServerUtils.INSTANCE.connectToLastServer();
                break;
            case 3:
                final List<MinecraftAccount> accounts = FileManager.INSTANCE.getAccountsConfig().getAccounts();
                if (accounts.isEmpty())
                    break;
                final MinecraftAccount minecraftAccount = accounts.get(new Random().nextInt(accounts.size()));

                mc.displayGuiScreen(new GuiLoginProgress(minecraftAccount, () -> {
                    mc.addScheduledTask(() -> {
                        EventManager.INSTANCE.call(SessionUpdateEvent.INSTANCE);
                        ServerUtils.INSTANCE.connectToLastServer();
                    });
                    return null;
                }, e -> {
                    mc.addScheduledTask(() -> {
                        mc.displayGuiScreen(new GuiDisconnected(new GuiMultiplayer(new GuiMainMenu()), e.getMessage(), new ChatComponentText(e.getMessage())));
                    });
                    return null;
                }, () -> null));

                break;
            case 4:
                RandomUtils.INSTANCE.randomAccount();
                ServerUtils.INSTANCE.connectToLastServer();
                break;
            case 5:
                MiscUtils.INSTANCE.showURL(APIConnectorUtils.INSTANCE.getDonate());
                break;
            case 6:
                ClientFixes.INSTANCE.setFmlFixesEnabled(!ClientFixes.INSTANCE.getFmlFixesEnabled());
                forgeBypassButton.displayString = "Bypass AntiForge: " + (ClientFixes.INSTANCE.getFmlFixesEnabled() ? "On" : "Off");
                FileManager.INSTANCE.saveConfig(FileManager.INSTANCE.getValuesConfig(), true);
                break;
            case 7:
                // Force Disconnect — send a force-disconnect packet sequence to
                // the server and close the channel. Useful when the server still
                // thinks the user is online after a normal disconnect (e.g.
                // "You are already logged on to this server" on rejoin).
                handleForceDisconnect();
                break;
            case 998:
                mc.displayGuiScreen(new GuiAltManager((GuiScreen) (Object) this));
                break;
            case 8:
                mc.displayGuiScreen(new GuiInfo((GuiScreen) (Object) this));
        }
    }

    /**
     * Performs a "force disconnect" — sends a sequence of force-disconnect
     * packets to the server and closes the channel. 1.8.9 has no explicit
     * client→server disconnect packet, so we use the next-best thing: an
     * oversized chat message that the server MUST close on, then a clean
     * channel close.
     */
    private void handleForceDisconnect() {
        try {
            net.minecraft.network.NetworkManager nm =
                net.asd.union.utils.client.MinecraftAccessorHelper.getMyNetworkManager();

            if (nm != null && nm.isChannelOpen()) {
                // 1. Send oversized chat packets. The vanilla 1.8.9 server
                //    enforces a 100-char limit on chat messages; oversized
                //    packets cause the server to close the connection from
                //    its side, guaranteeing the player is removed from the
                //    online-players list immediately.
                try {
                    String oversized =
                        "asd_union_force_disconnect_" +
                        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
                        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
                        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
                        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
                    nm.sendPacket(new net.minecraft.network.play.client.C01PacketChatMessage(oversized));
                    nm.sendPacket(new net.minecraft.network.play.client.C01PacketChatMessage(oversized));
                } catch (Throwable ignored) {
                    // Outbound queue may be closed; skip
                }
                // 2. Close the channel with a clear reason. The close sends
                //    a TCP FIN, which combined with the oversized-packet
                //    kick above ensures the server cleans up the player
                //    entry promptly.
                try {
                    nm.closeChannel(new net.minecraft.util.ChatComponentText("Disconnected (force)"));
                } catch (Throwable ignored) {
                }
                ClientUtils.INSTANCE.displayChatMessage("§a§l[ForceDisconnect] §fForce-disconnect packet sent to the server.");
            } else {
                ClientUtils.INSTANCE.displayChatMessage("§e[ForceDisconnect] §fNo active connection — nothing to force close.");
            }
        } catch (Throwable t) {
            ClientUtils.INSTANCE.getLOGGER().warn("[ForceDisconnect] Error during force disconnect", t);
            ClientUtils.INSTANCE.displayChatMessage("§c§l[ForceDisconnect] §fError: " + t.getClass().getSimpleName() + " — " + t.getMessage());
        }
    }

    @Override
    public void updateScreen() {
        if (AutoReconnect.INSTANCE.isEnabled()) {
            reconnectTimer++;
            if (reconnectTimer > AutoReconnect.INSTANCE.getDelay() / 50)
                ServerUtils.INSTANCE.connectToLastServer();
        }
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void drawScreen(CallbackInfo callbackInfo) {
        if (AutoReconnect.INSTANCE.isEnabled()) {
            updateReconnectButton();
        }
    }

    private void drawReconnectDelaySlider() {
        buttonList.add(autoReconnectDelaySlider =
                new GuiSlider(2, width / 2 + 2, height / 2 + field_175353_i / 2
                        + fontRendererObj.FONT_HEIGHT + 22, 98, 20, "AutoReconnect: ",
                        "ms", AutoReconnect.MIN, AutoReconnect.MAX, AutoReconnect.INSTANCE.getDelay(), false, true,
                        guiSlider -> {
                            AutoReconnect.INSTANCE.setDelay(guiSlider.getValueInt());

                            reconnectTimer = 0;
                            updateReconnectButton();
                            updateSliderText();
                        }));
    }

    private void updateSliderText() {
        if (autoReconnectDelaySlider == null)
            return;

        if (!AutoReconnect.INSTANCE.isEnabled()) {
            autoReconnectDelaySlider.displayString = "AutoReconnect: Off";
        } else {
            autoReconnectDelaySlider.displayString = "AutoReconnect: " + Math.floor(AutoReconnect.INSTANCE.getDelay() / 1000.0) + "s";
        }
    }

    private void updateReconnectButton() {
        if (reconnectButton != null)
            reconnectButton.displayString = "Reconnect" + (AutoReconnect.INSTANCE.isEnabled() ? " (" + (AutoReconnect.INSTANCE.getDelay() / 1000 - reconnectTimer / 20) + ")" : "");
    }
}
