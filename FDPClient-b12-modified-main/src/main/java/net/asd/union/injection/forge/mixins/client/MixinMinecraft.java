/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.client;

import net.asd.union.FDPClient;
import net.asd.union.features.module.modules.combat.TickBase;
import net.asd.union.features.module.modules.other.FastPlace;
import net.asd.union.handler.api.ClientUpdate;
import net.asd.union.event.*;
import net.asd.union.event.ClientTickEvent;
import net.asd.union.features.module.modules.combat.AutoClicker;
import net.asd.union.injection.forge.SplashProgressLock;
import net.asd.union.ui.client.gui.GuiClientConfiguration;
import net.asd.union.ui.client.gui.GuiMainMenu;
import net.asd.union.ui.client.gui.GuiUpdate;
import net.asd.union.utils.attack.CPSCounter;
import net.asd.union.utils.client.ClientUtils;
import net.asd.union.utils.inventory.SilentHotbar;
import net.asd.union.utils.io.MiscUtils;
import net.asd.union.utils.performance.StartupProgress;
import net.asd.union.utils.performance.StartupProgressRenderer;
import net.asd.union.utils.client.SoundDisabler;
import net.asd.union.utils.render.IconUtils;
import net.asd.union.utils.render.MiniMapRegister;
import net.asd.union.utils.render.RenderUtils;
import net.asd.union.file.FileManager;
import net.asd.union.handler.other.SessionStorage;
import net.asd.union.handler.network.ConnectToRouter;
import net.asd.union.handler.sessiontabs.ClientTabManager;
import net.asd.union.handler.sessiontabs.SessionRuntimeScope;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Util;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static net.asd.union.utils.client.MinecraftInstance.mc;

@Mixin(Minecraft.class)
@SideOnly(Side.CLIENT)
public abstract class MixinMinecraft {

    private static final Logger LOGGER = LogManager.getLogger("EarlyConfig");

    @Shadow
    public GuiScreen currentScreen;

    @Shadow
    public boolean skipRenderWorld;

    @Shadow
    private int leftClickCounter;

    @Shadow
    public MovingObjectPosition objectMouseOver;

    @Shadow
    public WorldClient theWorld;

    @Shadow
    public EntityPlayerSP thePlayer;

    @Shadow
    public PlayerControllerMP playerController;

    @Shadow
    public int displayWidth;

    @Shadow
    public int displayHeight;

    @Shadow
    public int rightClickDelayTimer;

    @Shadow
    public GameSettings gameSettings;

    @Shadow
    public abstract void displayGuiScreen(GuiScreen guiScreenIn);

    @Unique
    private Future<?> liquidBounce$preloadFuture;

    @Inject(method = "run", at = @At("HEAD"))
    private void init(CallbackInfo callbackInfo) {
        if (displayWidth < 1067) displayWidth = 1067;

        if (displayHeight < 622) displayHeight = 622;

        liquidBounce$preloadFuture = FDPClient.INSTANCE.preload();
    }

    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Timer;updateTimer()V", shift = At.Shift.BEFORE))
    private void preTimerUpdate(CallbackInfo ci) {
        EventManager.INSTANCE.call(PreTickEvent.INSTANCE);
    }

    @Inject(method = "startGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;checkGLError(Ljava/lang/String;)V", ordinal = 2, shift = At.Shift.AFTER))
    private void startGame(CallbackInfo callbackInfo) throws ExecutionException, InterruptedException {
        StartupProgressRenderer.render();
        liquidBounce$preloadFuture.get();

        // Load configs EARLIER to ensure saved settings are applied before Minecraft sets defaults
        FDPClient.INSTANCE.startClient();
    }

    @Inject(method = "startGame", at = @At(value = "HEAD"))
    private void loadConfigsEarly(CallbackInfo callbackInfo) {
        // Load configs as early as possible to prevent default values from overwriting saved settings
        // This ensures saved account and router settings are applied BEFORE Minecraft sets defaults
        try {
            LOGGER.info("[EarlyConfig] Loading configs before Minecraft initialization...");
            
            // Disable sound for Apple Silicon to prevent OpenAL crashes
            SoundDisabler.INSTANCE.disableSoundForAppleSilicon();
            
            // Only load essential configs that don't depend on fonts being initialized:
            // - accountsConfig: needed for saved username/router settings
            // - valuesConfig: needed for ConnectToRouter.enabled state
            // Skip hudConfig, clickgui.json, etc. as they require fonts to be loaded first
            FileManager.INSTANCE.loadConfigs(FileManager.INSTANCE.getAccountsConfig(), FileManager.INSTANCE.getValuesConfig());
            
            // Apply saved username BEFORE Minecraft can set default "itamio"
            SessionStorage.INSTANCE.applySavedUsername();
            
            // Refresh router status with the loaded settings (ConnectToRouter.enabled should already be set)
            ConnectToRouter.INSTANCE.refreshStatus(false);
            
            LOGGER.info("[EarlyConfig] Successfully loaded configs before Minecraft initialization");
        } catch (Exception e) {
            LOGGER.error("[EarlyConfig] Failed to load configs early", e);
        }
    }

    @Inject(method = "startGame", at = @At(value = "NEW", target = "net/minecraft/client/renderer/texture/TextureManager"))
    private void waitForLock(CallbackInfo ci) {
        long end = System.currentTimeMillis() + 20000;

        while (end < System.currentTimeMillis() && SplashProgressLock.INSTANCE.isAnimationRunning()) {
            synchronized (SplashProgressLock.INSTANCE) {
                try {
                    SplashProgressLock.INSTANCE.wait(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Inject(method = "startGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V", shift = At.Shift.AFTER))
    private void afterMainScreen(CallbackInfo callbackInfo) {
        // if (ClientUpdate.INSTANCE.hasUpdate()) {
        //     displayGuiScreen(new GuiUpdate());
        // }
    }

    @Inject(method = "createDisplay", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setTitle(Ljava/lang/String;)V", shift = At.Shift.AFTER))
    private void createDisplay(CallbackInfo callbackInfo) {
        if (GuiClientConfiguration.Companion.getEnabledClientTitle()) {
            Display.setTitle(FDPClient.INSTANCE.getClientTitle());
        }

        StartupProgressRenderer.setDisplayReady();
        StartupProgressRenderer.render();
    }

    @Inject(method = "drawSplashScreen", at = @At("HEAD"), cancellable = true)
    private void drawStartupSplash(net.minecraft.client.renderer.texture.TextureManager textureManager, CallbackInfo callbackInfo) {
        if (!StartupProgress.INSTANCE.isActive()) {
            StartupProgress.INSTANCE.start();
        }

        StartupProgressRenderer.render();
        callbackInfo.cancel();
    }

    @Inject(method = "displayGuiScreen", at = @At("HEAD"), cancellable = true)
    private void fdp$isolateDetachedGuiScreens(GuiScreen guiScreenIn, CallbackInfo callbackInfo) {
        // Guard: when thePlayer is null, opening in-game screens (GuiChat, GuiInventory)
        // or passing a null guiScreenIn while theWorld is non-null will NPE inside
        // vanilla displayGuiScreen or its callers (e.g. runTick line 2084). Force
        // GuiMainMenu instead. This is the ultimate safety net for dead-tab switches.
        boolean needsGuard = thePlayer == null && (
            (guiScreenIn == null && theWorld != null) ||
            guiScreenIn instanceof net.minecraft.client.gui.GuiChat ||
            guiScreenIn instanceof net.minecraft.client.gui.inventory.GuiInventory ||
            guiScreenIn instanceof net.minecraft.client.gui.GuiIngameMenu
        );
        if (needsGuard) {
            String screenName = guiScreenIn != null ? guiScreenIn.getClass().getSimpleName() : "null";
            LOGGER.warn("[TabRuntime] displayGuiScreen guard: thePlayer null and opening {} — forcing GuiMainMenu", screenName);
            GuiScreen screen = new GuiMainMenu();
            currentScreen = screen;
            ScaledResolution sr = new ScaledResolution(mc);
            screen.setWorldAndResolution(mc, sr.getScaledWidth(), sr.getScaledHeight());
            skipRenderWorld = false;
            callbackInfo.cancel();
            return;
        }

        // Simulation threads should not change the mc singleton's currentScreen
        if (Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            net.asd.union.handler.sessiontabs.LiveTabRuntime runtime =
                ((net.asd.union.handler.sessiontabs.TabSimulationThread) Thread.currentThread()).getRuntime();
            if (runtime != null) {
                // Sanitize: don't allow screens that pause the game
                GuiScreen sanitized = (guiScreenIn != null && guiScreenIn.doesGuiPauseGame()) ? null : guiScreenIn;
                runtime.setCurrentScreen(sanitized);
            }
            callbackInfo.cancel();
            return;
        }

        if (!SessionRuntimeScope.INSTANCE.isDetachedContextActive()) {
            return;
        }

        SessionRuntimeScope.INSTANCE.updateDetachedScreen(guiScreenIn);
        callbackInfo.cancel();
    }

    /**
     * CRITICAL: When thePlayer and theWorld are both null (dead-tab state),
     * cancel runTick entirely to prevent NPEs in the keybind processing block
     * and other code that accesses thePlayer without null checks. Manually
     * process GUI input so the screen (e.g. GuiDisconnected) stays interactive.
     */
    @Inject(method = "runTick", at = @At("HEAD"), cancellable = true)
    private void fdp$guardRunTickWhenNoPlayer(CallbackInfo ci) {
        // CRITICAL: Execute deferred state clearing from tab switches.
        // switchToTab() is called from within runTick (via scheduled tasks or
        // GUI button handlers). If we null thePlayer/theWorld synchronously
        // inside switchToTab, the rest of the current runTick NPEs on thePlayer
        // access (line 2084 = keybind processing block). So activateRuntime()
        // defers the clearing to this Runnable, which we execute at the HEAD of
        // the NEXT tick — after the current tick has finished safely with the
        // old (valid) player state.
        if (net.asd.union.handler.sessiontabs.TabSwitchState.pendingClear != null) {
            Runnable r = net.asd.union.handler.sessiontabs.TabSwitchState.pendingClear;
            net.asd.union.handler.sessiontabs.TabSwitchState.pendingClear = null;
            try {
                System.out.println("[TabDebug][runTick HEAD] Executing deferred state clear from tab switch");
                System.out.flush();
                r.run();
                System.out.println("[TabDebug][runTick HEAD] Deferred clear finished. thePlayer=" + (thePlayer != null) + ", theWorld=" + (theWorld != null));
                System.out.flush();
            } catch (Exception e) {
                LOGGER.warn("[TabRuntime] Error during deferred state clear", e);
            }
        }

        if (thePlayer == null && theWorld == null) {
            if (currentScreen == null) {
                LOGGER.warn("[TabRuntime] runTick guard: no player/world/screen — setting GuiMainMenu");
                GuiScreen screen = new GuiMainMenu();
                currentScreen = screen;
                ScaledResolution sr = new ScaledResolution(mc);
                screen.setWorldAndResolution(mc, sr.getScaledWidth(), sr.getScaledHeight());
                skipRenderWorld = false;
            }
            try {
                if (currentScreen != null) {
                    currentScreen.handleInput();
                    currentScreen.updateScreen();
                }
            } catch (Exception e) {
                LOGGER.warn("[TabRuntime] Error during dead-tab runTick skip", e);
            }
            ci.cancel();
        }
    }

    /**
     * CRITICAL: After currentScreen.handleInput() returns in runTick, check if
     * thePlayer became null (e.g. due to a tab switch triggered from a GUI button).
     * If so, cancel the rest of runTick — the keybind processing block and other
     * code will NPE on thePlayer access.
     */
    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleInput()V", shift = At.Shift.AFTER), cancellable = true)
    private void fdp$guardAfterHandleInput(CallbackInfo ci) {
        if (thePlayer == null) {
            System.out.println("[TabDebug][afterHandleInput] thePlayer became null during GUI handling — cancelling rest of runTick");
            System.out.flush();
            ci.cancel();
        }
    }

    @Inject(method = "displayGuiScreen", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;currentScreen:Lnet/minecraft/client/gui/GuiScreen;", shift = At.Shift.AFTER))
    private void handleDisplayGuiScreen(CallbackInfo callbackInfo) {
        if (currentScreen instanceof net.minecraft.client.gui.GuiMainMenu || (currentScreen != null && currentScreen.getClass().getName().startsWith("net.labymod") && currentScreen.getClass().getSimpleName().equals("ModGuiMainMenu"))) {
            currentScreen = new GuiMainMenu();

            ScaledResolution scaledResolution = new ScaledResolution(mc);
            currentScreen.setWorldAndResolution(mc, scaledResolution.getScaledWidth(), scaledResolution.getScaledHeight());
            skipRenderWorld = false;
        }

        EventManager.INSTANCE.call(new ScreenEvent(currentScreen));
    }

    @Unique
    private long lastFrame = getTime();

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void runGameLoop(final CallbackInfo callbackInfo) {
        final long currentTime = getTime();
        final int deltaTime = (int) (currentTime - lastFrame);
        lastFrame = currentTime;

        RenderUtils.INSTANCE.setDeltaTime(deltaTime);
        EventManager.INSTANCE.call(ClientTickEvent.INSTANCE);
    }

    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/Profiler;startSection(Ljava/lang/String;)V", ordinal = 1))
    private void hook(final CallbackInfo ci) {
        EventManager.INSTANCE.call(GameLoopEvent.INSTANCE);
    }

    @Unique
    public long getTime() {
        return (Sys.getTime() * 1000) / Sys.getTimerResolution();
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void injectGameRuntimeTicks(CallbackInfo ci) {
        ClientUtils.INSTANCE.setRunTimeTicks(ClientUtils.INSTANCE.getRunTimeTicks() + 1);
        SilentHotbar.INSTANCE.updateSilentSlot();
    }

    /**
     * Guard entityRenderer.updateRenderer() in runTick (line 2157).
     * updateRenderer accesses mc.theWorld.getLightBrightness() and
     * mc.getRenderViewEntity() unconditionally, which NPEs when theWorld
     * or thePlayer is null after activating a disconnected tab.
     * This is the ACTUAL crash location (func_78464_a = updateRenderer).
     */
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;updateRenderer()V"))
    private void fdp$guardUpdateRenderer(net.minecraft.client.renderer.EntityRenderer instance) {
        if (theWorld != null && thePlayer != null) {
            instance.updateRenderer();
        }
    }

    /**
     * Guard entityRenderer.getMouseOver() in runTick (line 1721).
     * When switching to a waiting tab with no restorable state, thePlayer
     * and playerController are null but getMouseOver is called unconditionally.
     */
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V"))
    private void fdp$guardGetMouseOver(net.minecraft.client.renderer.EntityRenderer instance, float partialTicks) {
        if (thePlayer != null && theWorld != null && playerController != null) {
            instance.getMouseOver(partialTicks);
        }
    }

    /**
     * Guard the keybind processing section of runTick (around line 2062)
     * where playerController.isRidingHorse() is called. When a background
     * tab disconnects and the tab system clears mc state, playerController
     * can be null while thePlayer still exists briefly. This guard prevents
     * the NPE by returning false when playerController is null.
     */
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;isRidingHorse()Z"))
    private boolean fdp$guardIsRidingHorse(PlayerControllerMP instance) {
        if (instance == null) {
            return false;
        }
        return instance.isRidingHorse();
    }

    /**
     * Guard playerController.onStoppedUsingItem() in runTick (around line 2096).
     * When a background tab disconnects, playerController can be null while
     * thePlayer still exists briefly.
     */
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;onStoppedUsingItem(Lnet/minecraft/client/entity/EntityPlayerSP;)V"))
    private void fdp$guardOnStoppedUsingItem(PlayerControllerMP instance, EntityPlayerSP player) {
        if (instance != null) {
            instance.onStoppedUsingItem(player);
        }
    }

    /**
     * CRITICAL: Guard KeyBinding.isPressed() in runTick's keybind processing block
     * (lines 2044-2130). This block executes REGARDLESS of currentScreen value,
     * and accesses this.thePlayer.isSpectator(), this.thePlayer.dropOneItem(),
     * this.displayGuiScreen(new GuiChat()), etc. without null checks.
     *
     * When a dead tab is activated, thePlayer is null but currentScreen is set
     * (e.g., GuiDisconnected), so the fdp$ensureScreenWhenNoPlayer guard doesn't
     * fire. Returning false here prevents all keybind processing when thePlayer
     * is null, avoiding NPEs at lines 2048, 2065, 2076, 2078, 2084, etc.
     */
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/KeyBinding;isPressed()Z"))
    private boolean fdp$guardKeyBindPressed(KeyBinding instance) {
        // Always call the original to consume the key-press counter, but return false
        // when there is no player so that the keybind action is not executed.
        boolean pressed = instance.isPressed();
        return pressed && thePlayer != null;
    }

    /**
     * Guard KeyBinding.isKeyDown() in runTick. Line 2132 checks
     * keyBindUseItem.isKeyDown() && ... && !this.thePlayer.isUsingItem().
     * When thePlayer is null, returning false prevents rightClickMouse()
     * from being called (which would NPE on thePlayer access).
     */
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/KeyBinding;isKeyDown()Z"))
    private boolean fdp$guardKeyBindKeyDown(KeyBinding instance) {
        if (thePlayer == null) {
            return false;
        }
        return instance.isKeyDown();
    }

    /**
     * Guard EntityPlayerSP.isUsingItem() in runTick (lines 2092 and 2132).
     * Line 2092: if (this.thePlayer.isUsingItem()) — NPEs when thePlayer is null.
     * Line 2132: !this.thePlayer.isUsingItem() — NPEs when thePlayer is null.
     * Returning false when the instance is null prevents both NPEs.
     */
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z"))
    private boolean fdp$guardIsUsingItem(EntityPlayerSP instance) {
        return instance != null && instance.isUsingItem();
    }

    /**
     * Guard EntityPlayerSP.isSpectator() in runTick (lines 1812, 1830, 2048, 2076).
     * These calls are made without null-checking thePlayer. Returning false when
     * the instance is null prevents NPEs in both the keyboard handling block and
     * the keybind processing block.
     */
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;isSpectator()Z"))
    private boolean fdp$guardIsSpectator(EntityPlayerSP instance) {
        return instance != null && instance.isSpectator();
    }

    /**
     * FINAL safety net for the persistent line 2084 crash: redirect every
     * displayGuiScreen(...) call inside runTick. If thePlayer is missing, log the
     * attempted screen and force GuiMainMenu. This fires at the call site, so it
     * catches crashes that happen before displayGuiScreen's HEAD inject is reached.
     */
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V"))
    private void fdp$guardDisplayGuiScreenInRunTick(Minecraft instance, GuiScreen guiScreenIn) {
        if (instance.thePlayer == null) {
            String name = guiScreenIn != null ? guiScreenIn.getClass().getSimpleName() : "null";
            LOGGER.warn("[TabRuntime] runTick displayGuiScreen redirect: thePlayer null, skipping {} and forcing GuiMainMenu", name);
            instance.displayGuiScreen(new GuiMainMenu());
            return;
        }
        instance.displayGuiScreen(guiScreenIn);
    }

    /**
     * Guard EntityPlayerSP.dropOneItem(boolean) in runTick (line 2078).
     * When thePlayer is null, dropping an item is impossible; skip the call.
     */
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;dropOneItem(Z)V"))
    private void fdp$guardDropOneItem(EntityPlayerSP instance, boolean dropAll) {
        if (instance != null) {
            instance.dropOneItem(dropAll);
        }
    }

    /**
     * Guard EntityPlayerSP.sendHorseInventory() in runTick (line 2065).
     * When thePlayer is null, sending horse inventory is impossible; skip the call.
     */
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;sendHorseInventory()V"))
    private void fdp$guardSendHorseInventory(EntityPlayerSP instance) {
        if (instance != null) {
            instance.sendHorseInventory();
        }
    }

    // NOTE: Do NOT guard processReceivedPackets() with a theWorld==null && thePlayer==null
    // check. During normal connection setup, both are null until S01PacketJoinGame is
    // processed (which creates them). Skipping processReceivedPackets() in that state
    // breaks ALL server connections. The dead-tab case is handled by nulling
    // myNetworkManager in activateRuntime, which prevents the runTick "pendingConnection"
    // branch from executing at all.

    @Inject(method = "runTick", at = @At("TAIL"))
    private void injectEndTickEvent(CallbackInfo ci) {
        EventManager.INSTANCE.call(TickEndEvent.INSTANCE);
    }

    @Inject(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;joinPlayerCounter:I", ordinal = 0))
    private void onTick(final CallbackInfo callbackInfo) {
        EventManager.INSTANCE.call(GameTickEvent.INSTANCE);
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;dispatchKeypresses()V", shift = At.Shift.AFTER))
    private void onKey(CallbackInfo callbackInfo) {
        if (Keyboard.getEventKeyState() && currentScreen == null)
            EventManager.INSTANCE.call(new KeyEvent(Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey()));
    }

    @Inject(method = "sendClickBlockToController", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MovingObjectPosition;getBlockPos()Lnet/minecraft/util/BlockPos;"))
    private void onClickBlock(CallbackInfo callbackInfo) {
        final BlockPos blockPos = objectMouseOver.getBlockPos();
        if (leftClickCounter == 0 && theWorld.getBlockState(blockPos).getBlock().getMaterial() != Material.air) {
            EventManager.INSTANCE.call(new ClickBlockEvent(blockPos, objectMouseOver.sideHit));
        }
    }

    @Inject(method = "sendClickBlockToController", at = @At("HEAD"))
    private void syncHeldBlockClick(boolean leftClick, CallbackInfo callbackInfo) {
    }

    @Inject(method = "setWindowIcon", at = @At("HEAD"), cancellable = true)
    private void setWindowIcon(CallbackInfo callbackInfo) {
        if (Util.getOSType() != Util.EnumOS.OSX) {
            if (GuiClientConfiguration.Companion.getEnabledClientTitle()) {
                final ByteBuffer[] liquidBounceFavicon = IconUtils.INSTANCE.getFavicon();
                if (liquidBounceFavicon != null) {
                    Display.setIcon(liquidBounceFavicon);
                    callbackInfo.cancel();
                }
            }
        }
    }

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void shutdown(CallbackInfo callbackInfo) {
        FDPClient.INSTANCE.stopClient();
    }

    @Inject(method = "displayCrashReport", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/common/FMLCommonHandler;instance()Lnet/minecraftforge/fml/common/FMLCommonHandler;"))
    private void injectDisplayCrashReport(CrashReport crashReport, CallbackInfo callbackInfo) {
        MiscUtils.showErrorPopup(crashReport.getCrashCause(), "Game crashed! ", MiscUtils.generateCrashInfo());
    }

    @Inject(method = "clickMouse", at = @At("HEAD"))
    private void clickMouse(CallbackInfo callbackInfo) {
        if (AutoClicker.INSTANCE.handleEvents()) {
            leftClickCounter = 0;
        }

        if (leftClickCounter <= 0) {
            CPSCounter.INSTANCE.registerClick(CPSCounter.MouseButton.LEFT);
        }
    }

    @Inject(method = "middleClickMouse", at = @At("HEAD"))
    private void middleClickMouse(CallbackInfo ci) {
        CPSCounter.INSTANCE.registerClick(CPSCounter.MouseButton.MIDDLE);
    }

    @Inject(method = "rightClickMouse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;rightClickDelayTimer:I", shift = At.Shift.AFTER))
    private void rightClickMouse(final CallbackInfo callbackInfo) {
        CPSCounter.INSTANCE.registerClick(CPSCounter.MouseButton.RIGHT);

        final FastPlace fastPlace = FastPlace.INSTANCE;
        if (!fastPlace.handleEvents()) return;

        // Don't spam-click when the player isn't holding blocks
        if (fastPlace.getOnlyBlocks() && (thePlayer.getHeldItem() == null || !(thePlayer.getHeldItem().getItem() instanceof ItemBlock)))
            return;

        if (objectMouseOver != null && objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            BlockPos blockPos = objectMouseOver.getBlockPos();
            IBlockState blockState = theWorld.getBlockState(blockPos);
            // Don't spam-click when interacting with a TileEntity (chests, ...)
            // Doesn't prevent spam-clicking anvils, crafting tables, ... (couldn't figure out a non-hacky way)
            if (blockState.getBlock().hasTileEntity(blockState)) return;
            // Return if not facing a block
        } else if (fastPlace.getFacingBlocks()) return;

        rightClickDelayTimer = fastPlace.getSpeed();
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
    private void loadWorld(WorldClient p_loadWorld_1_, String p_loadWorld_2_, final CallbackInfo callbackInfo) {
        if (theWorld != null) {
            MiniMapRegister.INSTANCE.unloadAllChunks();
        }

        // Force-shut the network channel when the world is being unloaded
        // (i.e. disconnecting from a server). Vanilla's closeChannel() is
        // sometimes not aggressive enough — the server may still consider the
        // player online if the TCP close races with server-side cleanup,
        // leading to "You are already logged on to this server" kicks on
        // rejoin. We:
        //   1. Send a "force disconnect" packet sequence (oversized chat
        //      message — guaranteed to cause the server to disconnect us)
        //   2. Close the channel with a clear reason
        if (p_loadWorld_1_ == null) {
            try {
                net.minecraft.network.NetworkManager nm =
                    ((net.asd.union.injection.forge.mixins.client.MixinMinecraftAccessor) this).getMyNetworkManager();
                if (nm != null && nm.isChannelOpen()) {
                    net.asd.union.utils.client.ClientUtils.INSTANCE.getLOGGER()
                        .info("[ForceDisconnect] loadWorld(null): aggressively closing active network channel to prevent stale session on server");
                    try {
                        // Send an oversized chat message — the server MUST close
                        // the connection on receipt of this because chat
                        // messages are limited to 100 chars in 1.8.9. This
                        // forces the server to process the disconnect from
                        // its side, not just from our TCP close.
                        nm.sendPacket(new net.minecraft.network.play.client.C01PacketChatMessage(
                            "asd_union_force_disconnect_" +
                            "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
                            "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
                            "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
                            "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
                        ));
                    } catch (Throwable ignored) {
                        // Outbound queue may be closed; nothing more we can do.
                    }
                    try {
                        nm.closeChannel(new net.minecraft.util.ChatComponentText("Disconnected (force)"));
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable t) {
                net.asd.union.utils.client.ClientUtils.INSTANCE.getLOGGER()
                    .warn("[ForceDisconnect] Error while force-closing channel on loadWorld(null)", t);
            }
        }

        EventManager.INSTANCE.call(new WorldEvent(p_loadWorld_1_));
    }


    @Redirect(method = "sendClickBlockToController", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z"))
    private boolean injectMultiActions(EntityPlayerSP instance) {
        return instance.itemInUse != null;
    }

    @Redirect(method = "sendClickBlockToController", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;resetBlockRemoving()V"))
    private void injectAbortBreaking(PlayerControllerMP instance) {
        instance.resetBlockRemoving();
    }

    @Redirect(method = "runGameLoop", at = @At(value = "INVOKE", target = "Ljava/util/Queue;isEmpty()Z"))
    private boolean injectTickBase(Queue instance) {
        return TickBase.INSTANCE.getDuringTickModification() || instance.isEmpty();
    }

    @Redirect(method = {"middleClickMouse", "rightClickMouse"}, at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I"))
    private int injectSilentHotbar(InventoryPlayer instance) {
        return SilentHotbar.INSTANCE.getCurrentSlot();
    }

    @Inject(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/entity/EntityPlayerSP;inventory:Lnet/minecraft/entity/player/InventoryPlayer;"))
    private void injectSilentHotbarManualPressDetection(CallbackInfo ci) {
        SilentHotbar.INSTANCE.setPressedAtSlot(true);
    }

    /**
     * @author CCBlueX
     */
    @ModifyConstant(method = "getLimitFramerate", constant = @Constant(intValue = 30))
    public int getLimitFramerate(int constant) {
        return 60;
    }
}
