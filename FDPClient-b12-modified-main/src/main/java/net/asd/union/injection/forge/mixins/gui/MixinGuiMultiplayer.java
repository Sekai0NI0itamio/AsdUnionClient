/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.gui;

import net.asd.union.event.EventManager;
import net.asd.union.event.SessionUpdateEvent;
import net.asd.union.file.FileManager;
import net.asd.union.file.configs.RecentAccount;
import net.asd.union.file.configs.RecentAccountsConfig;
import net.asd.union.handler.sessiontabs.ClientTabManager;
import net.asd.union.handler.sessiontabs.MultiSelectJoinQueue;
import net.asd.union.handler.network.ConnectToRouter;
import net.asd.union.ui.client.altmanager.GuiAltManager;
import net.asd.union.ui.client.gui.GuiClientFixes;
import net.asd.union.ui.client.gui.GuiConnectToRouter;
import net.asd.union.utils.client.ClientUtils;
import net.asd.union.utils.client.ServerPingController;
import net.asd.union.utils.kotlin.RandomUtils;
import net.asd.union.utils.login.ProperOfflineUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ServerSelectionList;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.Session;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mixin(value = GuiMultiplayer.class, priority = 1001)
public abstract class MixinGuiMultiplayer extends MixinGuiScreen {
    @Shadow
    private ServerSelectionList serverListSelector;

    private GuiTextField accountTextField;
    private GuiButton connectToRouterButton;
    private GuiButton properOfflineButton;
    private boolean joinGuardActive;
    private int accountBarY = 8;

    // Sidebar constants
    private static final int SIDEBAR_WIDTH = 110;
    private static final int SIDEBAR_ENTRY_HEIGHT = 14;
    private static final int SIDEBAR_HEADER_HEIGHT = 14;
    private static final int SIDEBAR_X_PADDING = 4;

    // Sidebar state
    private int sidebarScrollOffset = 0;
    private int hoveredAccountIndex = -1;

    // Multi-select state — the current session's account is always implicitly selected
    private final Set<String> selectedAccounts = new HashSet<>();

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

        properOfflineButton = new GuiButton(1002, fieldX, yPosition + 22, 140, 16, properOfflineButtonText());
        buttonList.add(properOfflineButton);

        if (serverListSelector != null) {
            serverListSelector.top = Math.max(serverListSelector.top, accountBarY + 50);
        }

        if (serverListSelector != null) {
            serverListSelector.width = width - SIDEBAR_WIDTH - 4;
            serverListSelector.setSlotXBoundsFromLeft(SIDEBAR_WIDTH + 4);
        }

        sidebarScrollOffset = 0;
        selectedAccounts.clear();
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
            // Clear multi-select on refresh so the user doesn't think their queued
            // accounts are still pending. They can re-shift-select after refresh.
            selectedAccounts.clear();
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
            case 1002:
                FileManager.INSTANCE.getAccountsConfig().setProperOfflineAccounts(
                        !FileManager.INSTANCE.getAccountsConfig().getProperOfflineAccounts()
                );
                FileManager.INSTANCE.saveConfig(FileManager.INSTANCE.getAccountsConfig(), true);
                if (properOfflineButton != null) {
                    properOfflineButton.displayString = properOfflineButtonText();
                }
                break;
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mouseClicked(int mouseX, int mouseY, int mouseButton, CallbackInfo callbackInfo) {
        if (accountTextField != null) {
            accountTextField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        if (handleSidebarClick(mouseX, mouseY, mouseButton)) {
            callbackInfo.cancel();
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
        if (properOfflineButton != null) {
            properOfflineButton.displayString = properOfflineButtonText();
        }

        drawSidebar(mouseX, mouseY);
    }

    @Inject(method = "func_146791_a", at = @At("HEAD"), cancellable = true)
    private void connectToServer(ServerData serverData, CallbackInfo callbackInfo) {
        joinGuardActive = true;
        ServerPingController.beginNewRefreshCycle();

        // Build the full list of selected accounts (current session + shift-selected)
        String currentUsername = mc.getSession() != null ? mc.getSession().getUsername() : "";

        // Determine the account that is actually playing on the active tab.
        // mc.getSession().getUsername() may not match the active tab's username
        // if the user switched accounts via the tab system. We need to exclude
        // the active tab's account from the background queue to avoid
        // "already logged on" rejections from the server.
        String activeTabUsername = currentUsername;
        if (mc.thePlayer != null) {
            activeTabUsername = mc.thePlayer.getName();
        }

        List<String> allSelected = new ArrayList<>();
        // Current session is always first
        if (!currentUsername.isEmpty()) {
            allSelected.add(currentUsername);
        }
        // Add shift-selected accounts (excluding current session AND active tab's account)
        for (String name : selectedAccounts) {
            if (!name.equals(currentUsername) && !name.equals(activeTabUsername)) {
                allSelected.add(name);
            }
        }

        if (allSelected.size() > 1) {
            // Multi-select join: first account joins naturally, rest are queued
            selectedAccounts.clear();

            // Record the first account's join
            RecentAccountsConfig config = FileManager.INSTANCE.getRecentAccountsConfig();
            config.recordJoin(currentUsername, serverData.serverIP);
            FileManager.INSTANCE.saveConfig(config, true);

            // Queue the remaining accounts (skip index 0 = current session)
            List<String> queuedAccounts = allSelected.subList(1, allSelected.size());
            MultiSelectJoinQueue.INSTANCE.enqueue(queuedAccounts, serverData);

            // Don't cancel — let the first account join naturally through vanilla flow
            return;
        }

        // Single account join — normal flow
        selectedAccounts.clear();
        String username = mc.getSession() != null ? mc.getSession().getUsername() : null;
        String serverIp = serverData != null ? serverData.serverIP : "";
        if (username != null && !username.isEmpty()) {
            RecentAccountsConfig config = FileManager.INSTANCE.getRecentAccountsConfig();
            config.recordJoin(username, serverIp);
            FileManager.INSTANCE.saveConfig(config, true);
        }
    }

    @Inject(method = "handleMouseInput", at = @At("HEAD"))
    private void handleSidebarMouseInput(CallbackInfo callbackInfo) throws IOException {
        ScaledResolution sr = new ScaledResolution(mc);
        int mouseX = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
        int mouseY = sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;

        int sidebarTop = getSidebarTop();
        int sidebarBottom = getSidebarBottom();

        if (mouseX >= 0 && mouseX < SIDEBAR_WIDTH && mouseY >= sidebarTop && mouseY <= sidebarBottom) {
            int scroll = Mouse.getEventDWheel();
            if (scroll != 0) {
                sidebarScrollOffset -= scroll > 0 ? SIDEBAR_ENTRY_HEIGHT * 2 : -SIDEBAR_ENTRY_HEIGHT * 2;
                clampSidebarScroll();
            }
        }
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

    // ==================== Sidebar ====================

    private int getSidebarTop() {
        return accountBarY + 50;
    }

    private int getSidebarBottom() {
        return height - 64;
    }

    private void drawSidebar(int mouseX, int mouseY) {
        List<RecentAccount> accounts = FileManager.INSTANCE.getRecentAccountsConfig().getAccounts();
        int sidebarTop = getSidebarTop();
        int sidebarBottom = getSidebarBottom();
        int visibleHeight = sidebarBottom - sidebarTop;

        if (visibleHeight <= 0) return;

        // Background
        Gui.drawRect(0, sidebarTop, SIDEBAR_WIDTH, sidebarBottom, 0xC0101010);
        Gui.drawRect(SIDEBAR_WIDTH, sidebarTop, SIDEBAR_WIDTH + 1, sidebarBottom, 0xFF303030);

        // Header
        String currentUsername = mc.getSession() != null ? mc.getSession().getUsername() : "";
        int totalSelected = selectedAccounts.size() + (currentUsername.isEmpty() ? 0 : 1);
        String headerText = totalSelected > 1 ? "Accounts (" + totalSelected + " sel)" : "Accounts";
        fontRendererObj.drawStringWithShadow(headerText, SIDEBAR_X_PADDING, sidebarTop + 1, 0xFFAAAAAA);

        int contentTop = sidebarTop + SIDEBAR_HEADER_HEIGHT;
        int contentBottom = sidebarBottom;
        int contentHeight = contentBottom - contentTop;
        if (contentHeight <= 0) return;

        // Determine hovered account
        hoveredAccountIndex = -1;
        if (mouseX >= 0 && mouseX < SIDEBAR_WIDTH && mouseY >= contentTop && mouseY < contentBottom) {
            int relativeY = mouseY - contentTop + sidebarScrollOffset;
            int index = relativeY / SIDEBAR_ENTRY_HEIGHT;
            if (index >= 0 && index < accounts.size()) {
                hoveredAccountIndex = index;
            }
        }

        // Draw entries
        int entryStartY = contentTop - sidebarScrollOffset;
        for (int i = 0; i < accounts.size(); i++) {
            int entryY = entryStartY + i * SIDEBAR_ENTRY_HEIGHT;
            if (entryY + SIDEBAR_ENTRY_HEIGHT < contentTop || entryY > contentBottom) continue;

            RecentAccount account = accounts.get(i);
            boolean hovered = (i == hoveredAccountIndex);
            boolean isCurrentSession = account.getUsername().equals(currentUsername);
            boolean isShiftSelected = selectedAccounts.contains(account.getUsername());
            boolean isSelected = isCurrentSession || isShiftSelected;

            // Hover highlight only (no background for selected)
            if (hovered && !isSelected) {
                Gui.drawRect(0, entryY, SIDEBAR_WIDTH, entryY + SIDEBAR_ENTRY_HEIGHT, 0x40FFFFFF);
            }

            // Username text (truncated)
            int maxTextWidth = SIDEBAR_WIDTH - SIDEBAR_X_PADDING * 2 - 12;
            String displayName = account.getUsername();
            while (fontRendererObj.getStringWidth(displayName) > maxTextWidth && displayName.length() > 1) {
                displayName = displayName.substring(0, displayName.length() - 1);
            }
            if (!displayName.equals(account.getUsername())) {
                displayName = displayName + "..";
            }

            // Text color: green if selected, white otherwise
            int textColor;
            if (isSelected) {
                textColor = 0xFF55FF55; // Green for selected
            } else if (hovered) {
                textColor = 0xFFFF5555;
            } else {
                textColor = 0xFFFFFFFF;
            }

            fontRendererObj.drawStringWithShadow(displayName, SIDEBAR_X_PADDING, entryY + 3, textColor);

            // X button
            int xBtnX = SIDEBAR_WIDTH - 12;
            boolean xHovered = hovered && mouseX >= xBtnX && mouseX < SIDEBAR_WIDTH - 2
                    && mouseY >= entryY && mouseY < entryY + SIDEBAR_ENTRY_HEIGHT;
            String xText = xHovered ? "\u00a7c\u00a7lx" : "\u00a77x";
            fontRendererObj.drawStringWithShadow(xText, xBtnX, entryY + 3, 0xFFFFFFFF);
        }

        // Scroll indicator
        int totalContentHeight = accounts.size() * SIDEBAR_ENTRY_HEIGHT;
        if (totalContentHeight > contentHeight) {
            int maxScroll = totalContentHeight - contentHeight;
            int indicatorHeight = Math.max(12, contentHeight * contentHeight / totalContentHeight);
            int indicatorY = contentTop + (int) ((float) sidebarScrollOffset / maxScroll * (contentHeight - indicatorHeight));
            Gui.drawRect(SIDEBAR_WIDTH - 3, indicatorY, SIDEBAR_WIDTH - 1, indicatorY + indicatorHeight, 0xFF808080);
        }

        // Queue status
        if (MultiSelectJoinQueue.INSTANCE.isRunning()) {
            int queueSize = MultiSelectJoinQueue.INSTANCE.getQueueSize();
            String statusText = queueSize > 0
                    ? "\u00a7eQueue: \u00a77" + queueSize + " account(s) remaining"
                    : "\u00a7eQueue: \u00a7fProcessing...";
            fontRendererObj.drawStringWithShadow(statusText, SIDEBAR_X_PADDING, sidebarBottom - 12, 0xFFFFFFFF);
        }

        // Tooltip
        if (hoveredAccountIndex >= 0 && hoveredAccountIndex < accounts.size()) {
            int entryY = entryStartY + hoveredAccountIndex * SIDEBAR_ENTRY_HEIGHT;
            int xBtnX = SIDEBAR_WIDTH - 12;
            boolean overX = mouseX >= xBtnX && mouseX < SIDEBAR_WIDTH - 2
                    && mouseY >= entryY && mouseY < entryY + SIDEBAR_ENTRY_HEIGHT;

            if (!overX) {
                RecentAccount account = accounts.get(hoveredAccountIndex);
                boolean isCurrentSession = account.getUsername().equals(currentUsername);
                boolean isShiftSelected = selectedAccounts.contains(account.getUsername());
                boolean isSelected = isCurrentSession || isShiftSelected;

                List<String> tooltipLines = new ArrayList<>();
                tooltipLines.add("\u00a7e" + account.getUsername());
                tooltipLines.add("\u00a77Last joined: \u00a7f" + formatRelativeTime(account.getLastJoinTimestamp()));
                if (account.getLastServerIp() != null && !account.getLastServerIp().isEmpty()) {
                    tooltipLines.add("\u00a77Server: \u00a7f" + account.getLastServerIp());
                }
                if (isCurrentSession) {
                    tooltipLines.add("\u00a7aCurrent account (auto-selected)");
                    if (selectedAccounts.size() > 0) {
                        tooltipLines.add("\u00a7bJoin a server to connect all selected");
                    }
                } else if (isShiftSelected) {
                    tooltipLines.add("\u00a7bShift-click to deselect");
                    tooltipLines.add("\u00a7bJoin a server to connect all selected");
                } else {
                    tooltipLines.add("\u00a7bShift-click to multi-select");
                }
                drawHoveringText(tooltipLines, mouseX, mouseY, fontRendererObj);
            }
        }
    }

    private boolean handleSidebarClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) return false;

        int sidebarTop = getSidebarTop();
        int sidebarBottom = getSidebarBottom();
        int contentTop = sidebarTop + SIDEBAR_HEADER_HEIGHT;

        if (mouseX < 0 || mouseX >= SIDEBAR_WIDTH || mouseY < contentTop || mouseY >= sidebarBottom) {
            return false;
        }

        List<RecentAccount> accounts = FileManager.INSTANCE.getRecentAccountsConfig().getAccounts();
        int relativeY = mouseY - contentTop + sidebarScrollOffset;
        int index = relativeY / SIDEBAR_ENTRY_HEIGHT;

        if (index < 0 || index >= accounts.size()) return false;

        RecentAccount account = accounts.get(index);
        int entryY = contentTop - sidebarScrollOffset + index * SIDEBAR_ENTRY_HEIGHT;
        int xBtnX = SIDEBAR_WIDTH - 12;

        // X button
        if (mouseX >= xBtnX && mouseX < SIDEBAR_WIDTH - 2
                && mouseY >= entryY && mouseY < entryY + SIDEBAR_ENTRY_HEIGHT) {
            selectedAccounts.remove(account.getUsername());
            FileManager.INSTANCE.getRecentAccountsConfig().removeAccount(account.getUsername());
            FileManager.INSTANCE.saveConfig(FileManager.INSTANCE.getRecentAccountsConfig(), true);
            clampSidebarScroll();
            return true;
        }

        String currentUsername = mc.getSession() != null ? mc.getSession().getUsername() : "";

        // Shift-click toggles multi-select (but can't deselect the current session account)
        if (isShiftKeyDown()) {
            if (account.getUsername().equals(currentUsername)) {
                // Can't deselect current session — it's always selected
                return true;
            }
            if (selectedAccounts.contains(account.getUsername())) {
                selectedAccounts.remove(account.getUsername());
            } else {
                selectedAccounts.add(account.getUsername());
            }
            return true;
        }

        // Normal click — switch to this account but preserve multi-select.
        // Multi-select is only cleared on: screen init, server join, or refresh.
        // Shift-click an already-selected account to deselect individually.
        applyStandardOfflineSession(account.getUsername());
        return true;
    }

    private boolean isShiftKeyDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private void clampSidebarScroll() {
        List<RecentAccount> accounts = FileManager.INSTANCE.getRecentAccountsConfig().getAccounts();
        int contentHeight = getSidebarBottom() - getSidebarTop() - SIDEBAR_HEADER_HEIGHT;
        int totalContentHeight = accounts.size() * SIDEBAR_ENTRY_HEIGHT;
        int maxScroll = Math.max(0, totalContentHeight - contentHeight);
        if (sidebarScrollOffset < 0) sidebarScrollOffset = 0;
        if (sidebarScrollOffset > maxScroll) sidebarScrollOffset = maxScroll;
    }

    private static String formatRelativeTime(long timestamp) {
        if (timestamp <= 0) return "Never";
        long diffMs = System.currentTimeMillis() - timestamp;
        long diffSeconds = diffMs / 1000;
        if (diffSeconds < 60) return "Just now";
        long diffMinutes = diffSeconds / 60;
        if (diffMinutes < 60) return diffMinutes + "m ago";
        long diffHours = diffMinutes / 60;
        if (diffHours < 24) return diffHours + "h ago";
        long diffDays = diffHours / 24;
        if (diffDays < 30) return diffDays + "d ago";
        return new java.text.SimpleDateFormat("MMM dd").format(new Date(timestamp));
    }

    // ==================== Helpers ====================

    private boolean isRefreshButton(GuiButton button) {
        if (button == null) return false;
        if (button.id == 8) return true;
        return I18n.format("selectServer.refresh").equals(button.displayString);
    }

    private boolean shouldSuppressPlayMultiplayer(String text) {
        if (text == null) return false;
        return I18n.format("multiplayer.title").equals(text) || "Play Multiplayer".equals(text);
    }

    private void removePlayMultiplayerButton() {
        String titleText = I18n.format("multiplayer.title");
        buttonList.removeIf(button ->
                button != null && button.displayString != null
                        && (titleText.equals(button.displayString) || "Play Multiplayer".equals(button.displayString))
        );
    }

    private void setOfflineAccount(String username) {
        if (username == null || username.isEmpty()) return;

        if (FileManager.INSTANCE.getAccountsConfig().getProperOfflineAccounts()) {
            new Thread(() -> {
                ProperOfflineUtils.ApplyResult result = ProperOfflineUtils.INSTANCE.applyProperOfflineSession(username);
                if (result == ProperOfflineUtils.ApplyResult.NO_MOJANG_ACCOUNT
                        || result == ProperOfflineUtils.ApplyResult.LOOKUP_FAILED) {
                    mc.addScheduledTask(() -> applyStandardOfflineSession(username));
                }
                if (accountTextField != null) {
                    mc.addScheduledTask(() -> accountTextField.setText(username));
                }
            }, "ProperOfflineLookup").start();
        } else {
            applyStandardOfflineSession(username);
            if (accountTextField != null) {
                accountTextField.setText(username);
            }
        }
    }

    private void applyStandardOfflineSession(String username) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        mc.session = new Session(username, uuid.toString(), "-", "legacy");
        EventManager.INSTANCE.call(SessionUpdateEvent.INSTANCE);
        if (accountTextField != null) {
            accountTextField.setText(username);
        }
    }

    private String properOfflineButtonText() {
        boolean enabled = FileManager.INSTANCE.getAccountsConfig().getProperOfflineAccounts();
        return "Proper Offline: " + (enabled ? "\u00a7aON" : "\u00a7cOFF");
    }
}
