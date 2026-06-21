/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.network;

import io.netty.buffer.Unpooled;
import net.asd.union.event.EntityMovementEvent;
import net.asd.union.event.EventManager;
import net.asd.union.features.module.modules.other.NoRotateSet;
import net.asd.union.features.module.modules.visual.NoParticles;
import net.asd.union.handler.payload.ClientFixes;
import net.asd.union.handler.render.AntiSpawnLag;
import net.asd.union.handler.render.LazyChunkCache;
import net.asd.union.handler.render.NoTitle;
import net.asd.union.handler.sessiontabs.LiveTabRuntime;
import net.asd.union.handler.sessiontabs.LiveTabRuntimeManager;
import net.asd.union.handler.sessiontabs.SessionRuntimeScope;
import net.asd.union.event.EventState;
import net.asd.union.event.PacketEvent;
import net.asd.union.utils.client.ClientUtils;
import net.asd.union.utils.client.PacketUtils;
import net.asd.union.utils.rotation.Rotation;
import net.asd.union.utils.rotation.RotationUtils;
import net.asd.union.utils.extensions.PlayerExtensionKt;
import net.asd.union.utils.kotlin.RandomUtils;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.client.C19PacketResourcePackStatus;
import net.minecraft.network.play.server.*;
import net.minecraft.util.MathHelper;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.util.BlockPos;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScorePlayerTeam;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URI;
import java.net.URISyntaxException;

import static net.asd.union.utils.client.MinecraftInstance.mc;
import static net.minecraft.network.play.client.C19PacketResourcePackStatus.Action.FAILED_DOWNLOAD;

@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerPlayClient {

    @Shadow
    public int currentServerMaxPlayers;
    @Shadow
    @Final
    private NetworkManager netManager;
    @Shadow
    private Minecraft gameController;
    @Shadow
    private WorldClient clientWorldController;

    @Inject(method = "handleParticles", at = @At("HEAD"), cancellable = true)
    private void fdp$cancelAllParticles(S2APacketParticles packetParticles, CallbackInfo ci) {
        if (NoParticles.shouldBlockAllParticles()) {
            ci.cancel();
        }
    }

    @Redirect(method = "handleParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/play/server/S2APacketParticles;getParticleCount()I", ordinal = 1))
    private int onParticleAmount(S2APacketParticles packetParticles) {
        return packetParticles.getParticleCount();
    }

    @Redirect(method = "handleParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/play/server/S2APacketParticles;getParticleSpeed()F"))
    private float onParticleSpeed(S2APacketParticles packetParticles) {
        return packetParticles.getParticleSpeed();
    }

    @Redirect(method = "handleSpawnObject", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/play/server/S0EPacketSpawnObject;getType()I"))
    private int onSpawnObjectType(S0EPacketSpawnObject packet) {
        return packet.getType();
    }

    /**
     * Guard for handleSpawnObject that prevents NPE in WorldClient.removeEntityFromWorld.
     * The server may send spawn packets for entities in a world that has been torn down
     * during a tab switch, causing NPE at WorldClient.func_73045_a.
     */
    @Inject(method = "func_147235_a", at = @At("HEAD"), cancellable = true)
    private void fdp$guardSpawnObject(net.minecraft.network.play.server.S0EPacketSpawnObject packetIn, CallbackInfo ci) {
        if (gameController == null || gameController.theWorld == null) {
            ci.cancel();
            return;
        }
        if (fdp$isHandlerForDifferentTab()) {
            ci.cancel();
        }
    }

    @Redirect(method = "handleChangeGameState", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/play/server/S2BPacketChangeGameState;getGameState()I"))
    private int onChangeGameState(S2BPacketChangeGameState packet) {
        return packet.getGameState();
    }

    @Inject(method = "handleTitle", at = @At("HEAD"), cancellable = true)
    private void noTitle$cancelServerTitles(S45PacketTitle packetIn, CallbackInfo ci) {
        // Background tab simulation threads should not display titles
        if (Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            ci.cancel();
            return;
        }

        if (SessionRuntimeScope.INSTANCE.isDetachedContextActive()) {
            ci.cancel();
            return;
        }

        if (NoTitle.INSTANCE.getEnabled()) {
            NoTitle.INSTANCE.clearRenderedTitle();
            ci.cancel();
        }
    }

    @Inject(method = "handleTitle", at = @At("HEAD"))
    private void fdp$dispatchTitlePacketEvent(S45PacketTitle packetIn, CallbackInfo ci) {
        PacketEvent event = new PacketEvent(packetIn, EventState.RECEIVE);
        EventManager.INSTANCE.call(event);
    }

    @Redirect(
            method = {
                "handleTeams",
                "func_147247_a",
                "handleScoreboardObjective",
                "handleUpdateScore",
                "handleDisplayScoreboard"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getScoreboard()Lnet/minecraft/scoreboard/Scoreboard;"
            )
    )
    private Scoreboard fdp$safeGetScoreboard(net.minecraft.world.World world) {
        if (world == null) {
            return new Scoreboard();
        }
        return world.getScoreboard();
    }

    @Redirect(
            method = {"handleTeams", "func_147247_a"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/scoreboard/Scoreboard;removePlayerFromTeam(Ljava/lang/String;Lnet/minecraft/scoreboard/ScorePlayerTeam;)V"
            )
    )
    private void fdp$safeRemovePlayerFromTeam(Scoreboard scoreboard, String playerName, ScorePlayerTeam team) {
        try {
            scoreboard.removePlayerFromTeam(playerName, team);
        } catch (IllegalStateException ignored) {
        }
    }

    /**
     * Redirects Scoreboard.getTeam() calls in handleTeams to prevent NPE when
     * the team doesn't exist. When the action is UPDATE (2), ADD_PLAYERS (3),
     * or REMOVE_PLAYERS (4), the team must already exist. If it doesn't, we
     * create the team to prevent NPE on the subsequent scoreplayerteam.method()
     * calls. This is safe because the server will send the full team data.
     */
    @Redirect(
            method = {"handleTeams", "func_147247_a"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/scoreboard/Scoreboard;getTeam(Ljava/lang/String;)Lnet/minecraft/scoreboard/ScorePlayerTeam;"
            )
    )
    private ScorePlayerTeam fdp$safeGetTeam(Scoreboard scoreboard, String teamName) {
        ScorePlayerTeam team = scoreboard.getTeam(teamName);
        if (team == null) {
            // Team doesn't exist yet — create it to prevent NPE.
            // This can happen when packets arrive out of order (e.g., UPDATE
            // before CREATE) which is common with multiple tab connections.
            try {
                team = scoreboard.createTeam(teamName);
            } catch (Exception e) {
                // Can't create team — return null, but the fdp$suppressNullWorldNPE
                // or the safeRemovePlayerFromTeam redirect should handle downstream issues
            }
        }
        return team;
    }

    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void fdp$dispatchChatPacketEvent(S02PacketChat packetIn, CallbackInfo ci) {
        // If we're on a TabSimulationThread, this is a background tab's chat.
        // Record it via TabChatManager but don't display on the foreground tab.
        if (Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            final IChatComponent component = packetIn.getChatComponent();
            if (component != null && packetIn.getType() != 2) {
                // Find the runtime for this handler to get the correct tab ID
                LiveTabRuntime runtime = LiveTabRuntimeManager.INSTANCE.findRuntimeForHandler((INetHandler)(Object)this);
                String tabId = runtime != null ? runtime.getTabId() : null;
                if (tabId != null) {
                    net.asd.union.handler.sessiontabs.TabChatManager.INSTANCE.captureBackgroundChat(tabId, component, 0);
                } else {
                    net.asd.union.handler.sessiontabs.TabChatManager.INSTANCE.capturePrintedMessage(component, 0);
                }
            }
            ci.cancel();
            return;
        }

        if (SessionRuntimeScope.INSTANCE.isDetachedContextActive()) {
            final IChatComponent component = ForgeEventFactory.onClientChat(packetIn.getType(), packetIn.getChatComponent());

            if (component != null && packetIn.getType() != 2) {
                // Record the chat message directly to the detached runtime's history.
                // We can't use printChatMessage because MixinGuiNewChat cancels it
                // for detached contexts.
                net.asd.union.handler.sessiontabs.TabChatManager.INSTANCE.capturePrintedMessage(component, 0);
            }

            ci.cancel();
            return;
        }

        PacketEvent event = new PacketEvent(packetIn, EventState.RECEIVE);
        EventManager.INSTANCE.call(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
    private void fdp$handleDetachedDisconnect(IChatComponent reason, CallbackInfo ci) {
        // If on a simulation thread, the runtime's own disconnect handling applies
        if (Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            LiveTabRuntime runtime = LiveTabRuntimeManager.INSTANCE.findRuntimeForHandler((INetHandler) (Object) this);
            if (runtime != null) {
                ClientUtils.INSTANCE.getLOGGER().warn("[TabSim][{}] Server disconnected: {}", runtime.debugLabel(), reason != null ? reason.getUnformattedText() : "null");
                runtime.markDisconnectedScreen(reason);
                // Notify the multi-select queue that this tab has disconnected.
                net.asd.union.handler.sessiontabs.MultiSelectJoinQueue.INSTANCE.onTabDisconnected(runtime.getTabId());
            }
            ci.cancel();
            return;
        }

        if (SessionRuntimeScope.INSTANCE.isDetachedContextActive()) {
            LiveTabRuntimeManager.INSTANCE.markCurrentRuntimeDisconnected(reason);
            ci.cancel();
            return;
        }

        LiveTabRuntime runtime = LiveTabRuntimeManager.INSTANCE.findRuntimeForHandler((INetHandler) (Object) this);
        if (runtime != null) {
            LiveTabRuntimeManager.INSTANCE.scheduleRuntimeDisconnect(runtime, reason);

            // Detect "already connected to this proxy" kicks and notify the user
            if (reason != null) {
                String text = reason.getUnformattedText();
                if (text != null && (text.contains("already connected") || text.contains("Already connected"))) {
                    ClientUtils.INSTANCE.displayChatMessage("§c§l[Tab] §fBackground tab was kicked: §e" + text);
                }
            }

            ci.cancel();
            return;
        }

        if (reason != null) {
            String text = reason.getUnformattedText();
            if (text != null && !text.isEmpty()) {
                ClientUtils.INSTANCE.displayChatMessage("§c§lKicked: §f" + text);
            }
        }
    }
    @Inject(method = "handleResourcePack", at = @At("HEAD"), cancellable = true)
    private void handleResourcePack(final S48PacketResourcePackSend p_handleResourcePack_1_, final CallbackInfo callbackInfo) {
        final String url = p_handleResourcePack_1_.getURL();
        final String hash = p_handleResourcePack_1_.getHash();

        if (ClientFixes.INSTANCE.getBlockResourcePackExploit()) {
            try {
                final String scheme = new URI(url).getScheme();
                final boolean isLevelProtocol = "level".equals(scheme);

                if (!"http".equals(scheme) && !"https".equals(scheme) && !isLevelProtocol)
                    throw new URISyntaxException(url, "Wrong protocol");

                if (isLevelProtocol && (url.contains("..") || !url.endsWith("/resources.zip")))
                    throw new URISyntaxException(url, "Invalid levelstorage resourcepack path");
            } catch (final URISyntaxException e) {
                ClientUtils.INSTANCE.getLOGGER().error("Failed to handle resource pack", e);

                // We fail of course.
                netManager.sendPacket(new C19PacketResourcePackStatus(hash, FAILED_DOWNLOAD));

                callbackInfo.cancel();
            }
        }
    }

    @Inject(method = "handleChunkData", at = @At("HEAD"), cancellable = true)
    private void onChunkData(S21PacketChunkData packet, CallbackInfo ci) {
        try {
            if (!LazyChunkCache.INSTANCE.getEnabled() || clientWorldController == null) {
                return;
            }

            int chunkX = packet.getChunkX();
            int chunkZ = packet.getChunkZ();
            boolean isFullChunk = packet.func_149274_i();

            if (isNearPlayerChunk(chunkX, chunkZ)) {
                return;
            }

            if (isFullChunk && packet.getExtractedSize() == 0) {
                LazyChunkCache.INSTANCE.remove(chunkX, chunkZ);
                return;
            }

            if (!isFullChunk || !LazyChunkCache.INSTANCE.contains(chunkX, chunkZ)) {
                return;
            }

            Chunk existingChunk = clientWorldController.getChunkFromChunkCoords(chunkX, chunkZ);

            if (existingChunk != null && !existingChunk.getAreLevelsEmpty(0, 255)) {
                LazyChunkCache.INSTANCE.recordSkip();
                ci.cancel();
                return;
            }

            LazyChunkCache.INSTANCE.remove(chunkX, chunkZ);
        } catch (Exception e) {
            ci.cancel();
        }
    }

    @Inject(method = "handleChunkData", at = @At("RETURN"))
    private void cacheChunkAfterLoad(S21PacketChunkData packet, CallbackInfo ci) {
        if (!LazyChunkCache.INSTANCE.getEnabled() || clientWorldController == null) {
            return;
        }

        if (!packet.func_149274_i() || packet.getExtractedSize() == 0) {
            return;
        }

        int chunkX = packet.getChunkX();
        int chunkZ = packet.getChunkZ();
        Chunk chunk = clientWorldController.getChunkFromChunkCoords(chunkX, chunkZ);

        if (chunk != null && !chunk.getAreLevelsEmpty(0, 255)) {
            LazyChunkCache.INSTANCE.add(chunkX, chunkZ);
        }
    }

    @Redirect(
            method = {"handleChunkData", "func_147263_a"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;func_177439_a(Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/block/state/IBlockState;"
            )
    )
    private IBlockState fdp$safeSetBlockStateSrg(Chunk chunk, BlockPos pos, IBlockState state) {
        return fdp$safeSetBlockState(chunk, pos, state);
    }

    @Redirect(
            method = {"handleChunkData", "func_147263_a"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;setBlockState(Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/block/state/IBlockState;"
            )
    )
    private IBlockState fdp$safeSetBlockStateDeobf(Chunk chunk, BlockPos pos, IBlockState state) {
        return fdp$safeSetBlockState(chunk, pos, state);
    }

    private IBlockState fdp$safeSetBlockState(Chunk chunk, BlockPos pos, IBlockState state) {
        int y = pos.getY();
        int localX = pos.getX() - (chunk.xPosition << 4);
        int localZ = pos.getZ() - (chunk.zPosition << 4);

        if (y < 0 || y >= 256 || localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
            return null;
        }

        try {
            return chunk.setBlockState(pos, state);
        } catch (ArrayIndexOutOfBoundsException ignored) {
            return null;
        }
    }

    @Inject(method = "handleMapChunkBulk", at = @At("HEAD"), cancellable = true)
    private void onChunkBulk(S26PacketMapChunkBulk packet, CallbackInfo ci) {
        try {
            if (!LazyChunkCache.INSTANCE.getEnabled() || clientWorldController == null) {
                return;
            }

            int chunkCount = packet.getChunkCount();
            boolean allCached = true;

            for (int index = 0; index < chunkCount; index++) {
                int chunkX = packet.getChunkX(index);
                int chunkZ = packet.getChunkZ(index);

                if (isNearPlayerChunk(chunkX, chunkZ)) {
                    allCached = false;
                    continue;
                }

                if (!LazyChunkCache.INSTANCE.contains(chunkX, chunkZ)) {
                    allCached = false;
                    continue;
                }

                Chunk existingChunk = clientWorldController.getChunkFromChunkCoords(chunkX, chunkZ);
                if (existingChunk == null || existingChunk.getAreLevelsEmpty(0, 255)) {
                    allCached = false;
                    LazyChunkCache.INSTANCE.remove(chunkX, chunkZ);
                }
            }

if (allCached) {
                LazyChunkCache.INSTANCE.recordSkip();
                ci.cancel();
            }
        } catch (Exception e) {
            ci.cancel();
        }
    }

    @Inject(method = "handleJoinGame", at = @At("HEAD"))
    private void clearCacheOnJoin(S01PacketJoinGame packetIn, CallbackInfo ci) {
        LazyChunkCache.INSTANCE.clear();
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void clearCacheOnRespawn(S07PacketRespawn packetIn, CallbackInfo ci) {
        LazyChunkCache.INSTANCE.clear();
    }

    /**
     * Complete custom handleRespawn for simulation threads.
     *
     * The vanilla handleRespawn uses gameController.mcProfiler (not thread-safe
     * from background threads) and modifies gameController.gameSettings.difficulty
     * (shared state). This inject replaces the entire method for sim threads,
     * using a private Profiler and avoiding shared state modifications.
     *
     * Without this, terrain reloads on background tabs (e.g., BungeeCord server
     * transfers) corrupt the main thread's profiler state and can cause the
     * connection to drop.
     */
    @Inject(method = "handleRespawn", at = @At("HEAD"), cancellable = true)
    private void fdp$handleRespawnForSimThread(S07PacketRespawn packetIn, CallbackInfo ci) {
        if (Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            // On sim thread — process the respawn for the background tab
            fdp$processRespawnOnSimThread(packetIn, ci);
            return;
        }

        // On main thread or Netty IO thread — this respawn MUST ONLY proceed
        // if the handler belongs to the currently active tab. If it belongs to
        // a background tab, processing it would overwrite mc.theWorld/mc.thePlayer
        // with the background tab's state, making the active tab unusable.
        //
        // We use a DIRECT NetworkManager comparison instead of relying on
        // isHandlerForBackgroundTab (which can fail if nmToTabId is stale).
        // The active tab's NM is mc.thePlayer.sendQueue.networkManager.
        // If this handler's NM doesn't match, it belongs to a different tab.
        if (fdp$isHandlerForDifferentTab()) {
            ClientUtils.INSTANCE.getLOGGER().warn("[TabSim] handleRespawn CANCELLED — handler belongs to a different tab! " +
                "dimension={}, gameType={}, thread={}. This prevents active tab state corruption.",
                packetIn.getDimensionID(), packetIn.getGameType(), Thread.currentThread().getName());
            ci.cancel();
            return;
        }

        // Handler matches the active tab — let vanilla process it
        ClientUtils.INSTANCE.getLOGGER().info("[TabSim] handleRespawn on main thread for active tab: dimension={}, gameType={}",
            packetIn.getDimensionID(), packetIn.getGameType());
    }

    /**
     * Checks if this NetHandlerPlayClient belongs to a DIFFERENT tab than the
     * currently active one. This is the bulletproof isolation check.
     *
     * On the main thread, the active tab's NetworkManager is always
     * mc.thePlayer.sendQueue.networkManager. If this handler's NM doesn't
     * match, the handler belongs to a background tab and processing its
     * packets would corrupt the active tab's state.
     *
     * Returns true if the handler belongs to a DIFFERENT tab (should be cancelled).
     * Returns false if the handler belongs to the active tab (safe to process).
     */
    private boolean fdp$isHandlerForDifferentTab() {
        // If there's no active player, we can't determine the active tab's NM.
        // In this case, fall back to isHandlerForBackgroundTab.
        if (gameController.thePlayer == null) {
            return LiveTabRuntimeManager.INSTANCE.isHandlerForBackgroundTab((INetHandler)(Object)this);
        }

        // Use the centralized NM comparison in LiveTabRuntimeManager.
        // This compares the handler's NM with the active tab's NM directly.
        if (LiveTabRuntimeManager.INSTANCE.isHandlerForDifferentTab((INetHandler)(Object)this)) {
            return true;
        }

        // Additional check: if the NM mapping says this is a background tab,
        // trust it even if the direct NM comparison didn't catch it.
        return LiveTabRuntimeManager.INSTANCE.isHandlerForBackgroundTab((INetHandler)(Object)this);
    }

    private void fdp$processRespawnOnSimThread(S07PacketRespawn packetIn, CallbackInfo ci) {
        LiveTabRuntime runtime = LiveTabRuntimeManager.INSTANCE.findRuntimeForHandler((INetHandler)(Object)this);
        if (runtime == null) {
            ClientUtils.INSTANCE.getLOGGER().warn("[TabSim] handleRespawn: no runtime found for handler, cancelling");
            ci.cancel();
            return;
        }

        ClientUtils.INSTANCE.getLOGGER().info("[TabSim][{}] Processing respawn: dimension={}, gameType={}, difficulty={}",
            runtime.debugLabel(), packetIn.getDimensionID(), packetIn.getGameType(), packetIn.getDifficulty());

        // Save the OLD player's state before creating the new one.
        // Vanilla's setDimensionAndSpawnPlayer preserves these values.
        EntityPlayerSP oldPlayer = runtime.getPlayer();
        int oldEntityId = oldPlayer != null ? oldPlayer.getEntityId() : 0;
        @SuppressWarnings("unchecked")
        java.util.List<net.minecraft.entity.DataWatcher.WatchableObject> oldDataWatcherValues = oldPlayer != null
            ? (java.util.List<net.minecraft.entity.DataWatcher.WatchableObject>) oldPlayer.getDataWatcher().getAllWatched()
            : java.util.Collections.emptyList();
        String oldClientBrand = oldPlayer != null ? oldPlayer.getClientBrand() : null;
        boolean oldReducedDebug = oldPlayer != null && oldPlayer.hasReducedDebug();
        net.minecraft.stats.StatFileWriter oldStatFileWriter = oldPlayer != null
            ? oldPlayer.getStatFileWriter()
            : new net.minecraft.stats.StatFileWriter();

        // Clear chunk cache
        LazyChunkCache.INSTANCE.clear();

        // Create a new player controller and store it in the runtime
        PlayerControllerMP newController = new PlayerControllerMP(gameController, (NetHandlerPlayClient)(Object)this);
        runtime.setPlayerController(newController);
        newController.setGameType(packetIn.getGameType());

        // Create a new world with a private profiler (thread-safe for sim threads)
        // Note: S07PacketRespawn doesn't carry hardcore flag; use false (same as vanilla behavior)
        net.minecraft.profiler.Profiler worldProfiler = new net.minecraft.profiler.Profiler();
        WorldClient newWorld = new WorldClient(
            (NetHandlerPlayClient)(Object)this,
            new WorldSettings(0L, packetIn.getGameType(), false, false, packetIn.getWorldType()),
            packetIn.getDimensionID(),
            packetIn.getDifficulty(),
            worldProfiler
        );

        // Set the handler's world reference
        clientWorldController = newWorld;

        // Load the world for the detached context (creates new player, etc.)
        // Pass the old StatFileWriter to preserve stats across respawns.
        // notifyQueue=false because this is a respawn, not an initial join.
        loadWorldForDetachedContext((NetHandlerPlayClient)(Object)this, newWorld, oldStatFileWriter, false);

        // Apply the preserved state to the new player — this is what vanilla's
        // setDimensionAndSpawnPlayer does. Without this:
        //   - Entity ID mismatch: server thinks a different entity is placing blocks
        //   - DataWatcher not copied: armor items, health, etc. are lost
        //   - Client brand lost: server may reject subsequent packets
        EntityPlayerSP newPlayer = runtime.getPlayer();
        if (newPlayer != null) {
            newPlayer.setEntityId(oldEntityId);
            newPlayer.getDataWatcher().updateWatchedObjectsFromList(oldDataWatcherValues);
            newPlayer.setClientBrand(oldClientBrand);
            newPlayer.dimension = packetIn.getDimensionID();
            newPlayer.setReducedDebug(oldReducedDebug);
        }

        // Update the runtime's renderViewEntity to the new player.
        // After respawn, the old renderViewEntity points to the dead player,
        // which causes NPE in EntityRenderer when the tab is activated.
        if (newPlayer != null) {
            runtime.setRenderViewEntity(newPlayer);
        }

        // Send client settings after respawn. The server needs these to
        // properly track the player (render distance, chat visibility, etc.).
        // Vanilla sends these via GuiDownloadTerrain, which we skip on sim threads.
        try {
            net.minecraft.client.settings.GameSettings settings = gameController.gameSettings;
            int modelParts = 0;
            for (net.minecraft.entity.player.EnumPlayerModelParts part : settings.getModelParts()) {
                modelParts |= part.getPartMask();
            }
            netManager.sendPacket(new net.minecraft.network.play.client.C15PacketClientSettings(
                settings.language, settings.renderDistanceChunks,
                settings.chatVisibility, settings.chatColours, modelParts
            ));
        } catch (Exception e) {
            ClientUtils.INSTANCE.getLOGGER().warn("[TabSim] Failed to send client settings after respawn", e);
        }

        ClientUtils.INSTANCE.getLOGGER().info("[TabSim][{}] Processed respawn: dimension={}, gameType={}, entityId={}",
            runtime.debugLabel(), packetIn.getDimensionID(), packetIn.getGameType(), oldEntityId);

        // Cancel the vanilla method — we've handled everything
        ci.cancel();
    }

    @Redirect(
            method = "handleRespawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V"
            )
    )
    private void antiSpawnLag$skipRespawnLoadingScreen(Minecraft instance, GuiScreen guiScreen) {
        // Skip displayGuiScreen entirely for background tabs
        if (SessionRuntimeScope.INSTANCE.isDetachedContextActive() || Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            return;
        }
        // Also skip if this handler belongs to a different tab — this redirect
        // is called from handleRespawn, and if the handler's NM doesn't match
        // the active tab's NM, we must NOT modify the active tab's GUI.
        if (fdp$isHandlerForDifferentTab()) {
            return;
        }
        if (guiScreen instanceof GuiDownloadTerrain && AntiSpawnLag.INSTANCE.consumeTerrainScreenBypass()) {
            instance.displayGuiScreen(null);
        } else {
            instance.displayGuiScreen(guiScreen);
        }
    }

    @Inject(method = "handleJoinGame", at = @At("HEAD"), cancellable = true)
    private void handleJoinGameWithAntiForge(S01PacketJoinGame packetIn, final CallbackInfo callbackInfo) {
        // Always check for background tabs FIRST, regardless of FML settings.
        // On the main thread, cancel to prevent state corruption (the world/player
        // would be created in the mc singleton, overwriting the active tab's state).
        // On the sim thread, let it continue — the world/player need to be created
        // for the background tab.
        if (!(Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread)) {
            // On main thread or Netty IO thread — use the bulletproof NM comparison
            if (fdp$isHandlerForDifferentTab()) {
                ClientUtils.INSTANCE.getLOGGER().warn("[TabSim] handleJoinGame CANCELLED — handler belongs to a different tab! " +
                    "thread={}. This prevents active tab state corruption.", Thread.currentThread().getName());
                callbackInfo.cancel();
                return;
            }
        }

        if (!ClientFixes.INSTANCE.getFmlFixesEnabled() || !ClientFixes.INSTANCE.getBlockFML() || mc.isIntegratedServerRunning())
            return;

        boolean isSimThread = Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread;
        boolean isDetached = SessionRuntimeScope.INSTANCE.isDetachedContextActive();

        // On simulation threads, process the packet directly (MixinPacketThreadUtil cancels the enqueue)
        if (!isSimThread && !isDetached) {
            PacketThreadUtil.checkThreadAndEnqueue(packetIn, (NetHandlerPlayClient) (Object) this, gameController);
        }

        // Create the player controller
        PlayerControllerMP newController = new PlayerControllerMP(gameController, (NetHandlerPlayClient) (Object) this);

        // Store the controller in the correct place:
        // - Sim thread: store in runtime (the blanket redirect will also intercept
        //   gameController.playerController = ... but we set it explicitly to be safe)
        // - Main thread: store in mc
        if (isSimThread) {
            LiveTabRuntime runtime = LiveTabRuntimeManager.INSTANCE.findRuntimeForHandler((INetHandler)(Object)this);
            if (runtime != null) {
                runtime.setPlayerController(newController);
            }
        } else {
            gameController.playerController = newController;
        }

        // Create the world — this is per-handler, so it's safe.
        // On sim threads, pass a dummy profiler to avoid interfering with
        // the main thread's profiling state.
        net.minecraft.profiler.Profiler worldProfiler = (isSimThread)
            ? new net.minecraft.profiler.Profiler()
            : gameController.mcProfiler;
        clientWorldController = new WorldClient((NetHandlerPlayClient) (Object) this, new WorldSettings(0L, packetIn.getGameType(), false, packetIn.isHardcoreMode(), packetIn.getWorldType()), packetIn.getDimension(), packetIn.getDifficulty(), worldProfiler);

        // Only change shared mc.gameSettings on the main thread for the ACTIVE tab —
        // sim threads and background tab handlers must not modify the shared settings
        // that belong to the active tab.
        if (!isSimThread && !isDetached && !fdp$isHandlerForDifferentTab()) {
            gameController.gameSettings.difficulty = packetIn.getDifficulty();
        }

        if (isDetached || isSimThread || fdp$isHandlerForDifferentTab()) {
            loadWorldForDetachedContext((NetHandlerPlayClient) (Object) this, clientWorldController);
        } else {
            gameController.loadWorld(clientWorldController);
        }

        // Set dimension/entityId on the correct player
        if (isSimThread) {
            LiveTabRuntime runtime = LiveTabRuntimeManager.INSTANCE.findRuntimeForHandler((INetHandler)(Object)this);
            if (runtime != null && runtime.getPlayer() != null) {
                runtime.getPlayer().dimension = packetIn.getDimension();
                runtime.getPlayer().setEntityId(packetIn.getEntityId());
                runtime.getPlayer().setReducedDebug(packetIn.isReducedDebugInfo());
            }
        } else {
            gameController.thePlayer.dimension = packetIn.getDimension();
            gameController.thePlayer.setEntityId(packetIn.getEntityId());
            gameController.thePlayer.setReducedDebug(packetIn.isReducedDebugInfo());
        }

        if (!isDetached && !isSimThread) {
            gameController.displayGuiScreen(new GuiDownloadTerrain((NetHandlerPlayClient) (Object) this));
        }

        currentServerMaxPlayers = packetIn.getMaxPlayers();

        // Set game type on the correct controller
        newController.setGameType(packetIn.getGameType());

        // Send client brand packet — always needed for server compatibility
        netManager.sendPacket(new C17PacketCustomPayload("MC|Brand", (new PacketBuffer(Unpooled.buffer())).writeString(ClientBrandRetriever.getClientModName())));

        // Send client settings to the server. This is critical — without
        // C15PacketClientSettings, the server uses default render distance
        // (which may be very small), doesn't send chat messages, and may
        // not properly track the player's position.
        if (isSimThread || fdp$isHandlerForDifferentTab()) {
            // On sim threads or for background tab handlers on the main thread,
            // send settings directly via the background tab's NM.
            // GameSettings.sendSettingsToServer() uses mc.thePlayer which
            // is the active tab's player, so we construct the packet manually.
            try {
                net.minecraft.client.settings.GameSettings settings = gameController.gameSettings;
                int modelParts = 0;
                for (net.minecraft.entity.player.EnumPlayerModelParts part : settings.getModelParts()) {
                    modelParts |= part.getPartMask();
                }
                netManager.sendPacket(new net.minecraft.network.play.client.C15PacketClientSettings(
                    settings.language, settings.renderDistanceChunks,
                    settings.chatVisibility, settings.chatColours, modelParts
                ));
            } catch (Exception e) {
                ClientUtils.INSTANCE.getLOGGER().warn("[TabSim] Failed to send client settings", e);
            }
        } else {
            gameController.gameSettings.sendSettingsToServer();
        }

        callbackInfo.cancel();
    }

    @Inject(method = "handleEntityMovement", at = @At("HEAD"), cancellable = true)
    private void suppressEntityMovementNPE(S14PacketEntity packetIn, CallbackInfo ci) {
        if (clientWorldController == null || packetIn.getEntity(clientWorldController) == null) {
            ci.cancel();
        }
    }

    @Inject(method = {
        "handleEntityHeadLook",
        "handleEntityTeleport",
        "handleEntityMetadata",
        "handleEntityEquipment",
        "handleEntityProperties",
        "handleEntityVelocity",
        "handleTimeUpdate",
        "handleSoundEffect",
        "handleTeams",
        "func_147247_a",
        "handleScoreboardObjective",
        "handleUpdateScore",
        "handleDisplayScoreboard",
        "handleUpdateTileEntity",
        "func_147273_a",
        "func_147279_a",   // handleEntityAnimation
        "func_147238_a"    // handleDestroyEntities
    }, at = @At("HEAD"), cancellable = true)
    private void fdp$suppressNullWorldNPE(CallbackInfo ci) {
        // BULLETPROOF TAB ISOLATION: If this handler belongs to a different tab
        // than the currently active one, cancel immediately. This prevents
        // background tab packets from contaminating the active tab's state.
        // This is the first line of defense for all handler methods covered
        // by this inject.
        if (fdp$isHandlerForDifferentTab()) {
            ci.cancel();
            return;
        }

        if (clientWorldController == null) {
            ci.cancel();
            return;
        }
        // Also cancel if gameController.theWorld is null. This can happen when
        // packets are processed on the main thread before the world is fully
        // set up (e.g., during initial connection). The fdp$redirectGetTheWorld
        // redirect only helps on sim threads — on the main thread it returns
        // mc.theWorld which can be null.
        if (gameController != null && gameController.theWorld == null) {
            ci.cancel();
            return;
        }
        // Also cancel if thePlayer is null on the main thread (happens during
        // initial connection before handleJoinGame creates the world/player).
        // On sim threads, the blanket redirect handles this via the runtime.
        if (!(Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread)
            && gameController.thePlayer == null) {
            ci.cancel();
        }
    }

    /**
     * Pre-emptive guard for handleUpdateTileEntity that runs in addition to
     * fdp$suppressNullWorldNPE. This catches the case where the world exists
     * and the block is loaded, but the tile entity at the packet's position
     * is null (e.g. server sent a stale packet for a position that doesn't
     * have a tile entity on the client). Without this check, the vanilla
     * code NPEs at `tileentity.onDataPacket(...)`.
     */
    @Inject(method = "func_147273_a", at = @At("HEAD"), cancellable = true)
    private void fdp$guardUpdateTileEntity(net.minecraft.network.play.server.S35PacketUpdateTileEntity packetIn, CallbackInfo ci) {
        if (gameController == null || gameController.theWorld == null) {
            ci.cancel();
            return;
        }
        if (!gameController.theWorld.isBlockLoaded(packetIn.getPos())) {
            ci.cancel();
            return;
        }
        if (gameController.theWorld.getTileEntity(packetIn.getPos()) == null) {
            // Tile entity doesn't exist at the packet's position. The vanilla
            // code will NPE on the unchecked `tileentity.onDataPacket(...)`
            // call. Silently drop the packet.
            ci.cancel();
        }
    }

    /**
     * Comprehensive tab isolation guard for handler methods that could modify
     * mc singleton state. This covers methods NOT already guarded by
     * fdp$suppressNullWorldNPE (which handles entity/chunk/tileentity methods).
     *
     * These methods can modify mc singleton state (displayGuiScreen, gameSettings,
     * effectRenderer, etc.) and MUST NOT run for background tab handlers on the
     * main thread.
     */
    @Inject(method = {
        "handleChat",
        "handleDisconnect",
        "handleChangeGameState",
        "handleOpenWindow",
        "handleCloseWindow",
        "handleConfirmTransaction",
        "handleHeldItemChange",
        "handleSetSlot",
        "handleWindowItems",
        "handleWindowProperty",
        "handleSignEditorOpen",
        "handleUpdateSign",
        "handleCollectItem",
        "handleUseBed",
        "handleEntityStatus",
        "handleUpdateHealth",
        "handleSetExperience",
        "handlePlayerAbilities",
        "handleTabComplete",
        "handlePlayerListItem",
        "handleKeepAlive",
        "handleCustomPayload",
        "handleResourcePack",
        "handleStatistics",
        "handleSpawnPosition",
        "handleServerDifficulty",
        "handleCombatEvent",
        "handleCamera",
        "handleTitle",
        "handlePlayerListHeaderFooter",
        "handleMapChunkBulk",
        "handleBlockAction",
        "handleBlockBreakAnim",
        "handleEffect",
        "handleMaps",
        "handleEntityNBT",
        "handleSpawnExperienceOrb",
        "handleSpawnGlobalEntity",
        "handleAnimation",
        "handleSetCompressionLevel",
        "handleParticles",
        "handleRemoveEntityEffect"
    }, at = @At("HEAD"), cancellable = true)
    private void fdp$guardTabIsolation(CallbackInfo ci) {
        if (fdp$isHandlerForDifferentTab()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleEntityMovement", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;onGround:Z"))
    private void handleEntityMovementEvent(S14PacketEntity packetIn, final CallbackInfo callbackInfo) {
        final Entity entity = packetIn.getEntity(clientWorldController);

        if (entity != null)
            EventManager.INSTANCE.call(new EntityMovementEvent(entity));
    }

    @Inject(method = "handlePlayerPosLook", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayer;setPositionAndRotation(DDDFF)V", shift = At.Shift.BEFORE))
    private void injectNoRotateSetPositionOnly(S08PacketPlayerPosLook p_handlePlayerPosLook_1_, CallbackInfo ci) {
        // Skip NoRotateSet logic on simulation threads — it references mc.thePlayer
        // which belongs to the active tab, not the background tab.
        if (Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            return;
        }
        NoRotateSet module = NoRotateSet.INSTANCE;

        // Save the server's requested rotation before it resets the rotations
        module.setSavedRotation(PlayerExtensionKt.getRotation(Minecraft.getMinecraft().thePlayer));
    }

    @Inject(method = "handleSpawnPlayer", at = @At("HEAD"), cancellable = true)
    private void suppressSpawnNPE(S0CPacketSpawnPlayer packetIn, CallbackInfo ci) {
        boolean isSimThread = Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread;
        if (clientWorldController == null) {
            if (isSimThread) {
                net.asd.union.handler.sessiontabs.LiveTabRuntime rt =
                    net.asd.union.handler.sessiontabs.TabSimulationThread.getCurrentProcessingRuntime();
                System.err.println("[TabSim] suppressSpawnNPE: clientWorldController is NULL on sim thread! runtimeWorld=" +
                    (rt != null ? rt.getWorld() : "no-runtime"));
            }
            ci.cancel();
            return;
        }
        // On simulation threads, we must allow entity spawning so that
        // background tabs maintain their entity lists. We check the
        // runtime's player instead of mc.thePlayer (which may be null
        // or belong to the active tab).
        if (isSimThread) {
            LiveTabRuntime runtime = LiveTabRuntimeManager.INSTANCE.findRuntimeForHandler((INetHandler)(Object)this);
            if (runtime == null || runtime.getPlayer() == null) {
                System.err.println("[TabSim] suppressSpawnNPE: cancelling on sim thread - runtime=" + runtime + ", player=" + (runtime != null ? runtime.getPlayer() : "null"));
                ci.cancel();
                return;
            }
            // Don't cancel — let the vanilla code run. The safeGetPlayerInfoForSpawn
            // redirect handles null NetworkPlayerInfo, and MixinPacketThreadUtil
            // handles the checkThreadAndEnqueue call.
            return;
        }
        if (gameController.thePlayer == null) {
            ci.cancel();
            return;
        }
        if (SessionRuntimeScope.INSTANCE.isDetachedContextActive()) {
            ci.cancel();
        }
    }

    /**
     * Redirects the getPlayerInfo(UUID) call inside handleSpawnPlayer to prevent NPE.
     * The NPE occurs because getPlayerInfo returns null when the player
     * info hasn't been added to the map yet (e.g., during tab switching).
     * If null, we create a dummy NetworkPlayerInfo to prevent the crash.
     */
    @Redirect(
            method = "handleSpawnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/NetHandlerPlayClient;getPlayerInfo(Ljava/util/UUID;)Lnet/minecraft/client/network/NetworkPlayerInfo;"
            )
    )
    private NetworkPlayerInfo safeGetPlayerInfoForSpawn(NetHandlerPlayClient handler, java.util.UUID uuid) {
        NetworkPlayerInfo info = handler.getPlayerInfo(uuid);
        if (info == null) {
            // Create a dummy NetworkPlayerInfo to prevent NPE downstream.
            // The entity will be spawned with a default skin and 0 ping.
            try {
                com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(uuid, uuid.toString().substring(0, 8));
                info = new NetworkPlayerInfo(profile);
                // DEBUG: System.err.println("[TabSim] safeGetPlayerInfoForSpawn: created dummy NetworkPlayerInfo for " + uuid + " on thread " + Thread.currentThread().getName());
            } catch (Exception e) {
                // If we can't create a dummy, return null and accept the NPE
                // (the HEAD check should have caught this case already)
                // DEBUG: System.err.println("[TabSim] safeGetPlayerInfoForSpawn: FAILED to create dummy for " + uuid + ": " + e.getMessage());
            }
        }
        return info;
    }

    /**
     * Ensures the GameProfile passed to EntityOtherPlayerMP constructor is never null.
     * This is a safety net in case safeGetPlayerInfoForSpawn returns null (e.g., if
     * the redirect doesn't match due to remapping issues).
     */
    @Redirect(
            method = "handleSpawnPlayer",
            at = @At(
                    value = "NEW",
                    target = "Lnet/minecraft/client/entity/EntityOtherPlayerMP;<init>(Lnet/minecraft/client/multiplayer/WorldClient;Lcom/mojang/authlib/GameProfile;)V"
            )
    )
    private EntityOtherPlayerMP fdp$safeCreateOtherPlayer(WorldClient world, com.mojang.authlib.GameProfile profile) {
        if (profile == null) {
            // This should never happen if safeGetPlayerInfoForSpawn works,
            // but if it does, create a dummy profile to prevent NPE.
            // DEBUG: System.err.println("[TabSim] fdp$safeCreateOtherPlayer: GameProfile is NULL! Creating dummy on thread " + Thread.currentThread().getName());
            profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Unknown");
        }
        return new net.minecraft.client.entity.EntityOtherPlayerMP(world, profile);
    }

    @Inject(method = "handleConfirmTransaction", at = @At("HEAD"), cancellable = true)
    private void suppressConfirmTransactionNPE(S32PacketConfirmTransaction packetIn, CallbackInfo ci) {
        if (clientWorldController == null) {
            ci.cancel();
            return;
        }
        // On simulation threads, check the runtime's player instead of mc.thePlayer
        if (Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            LiveTabRuntime runtime = LiveTabRuntimeManager.INSTANCE.findRuntimeForHandler((INetHandler)(Object)this);
            if (runtime == null || runtime.getPlayer() == null) {
                ci.cancel();
            }
            return;
        }
        if (gameController.thePlayer == null) {
            ci.cancel();
        }
    }

    @Redirect(method = "handlePlayerPosLook", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkManager;sendPacket(Lnet/minecraft/network/Packet;)V"))
    private void injectNoRotateSetAndAntiServerRotationOverride(NetworkManager instance, Packet p_sendPacket_1_) {
        // On simulation threads, send the teleport confirmation directly via
        // the background tab's NetworkManager. PacketUtils.sendPacket uses
        // mc.netHandler (the active tab's handler), which sends the packet to
        // the WRONG server — the background tab's server never receives the
        // confirmation and keeps teleporting the player.
        if (Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            instance.sendPacket(p_sendPacket_1_);
            return;
        }

        PacketUtils.sendPacket(p_sendPacket_1_, true);

        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        NoRotateSet module = NoRotateSet.INSTANCE;

        if (player == null || !module.shouldModify(player)) {
            return;
        }

        int sign = RandomUtils.INSTANCE.nextBoolean() ? 1 : -1;

        Rotation rotation = player.ticksExisted == 0 ? RotationUtils.INSTANCE.getServerRotation() : module.getSavedRotation();

        if (module.getAffectRotation()) {
            NoRotateSet.INSTANCE.rotateBackToPlayerRotation();
        }

        player.rotationYaw = (rotation.getYaw() + 0.000001f * sign) % 360.0F;
        player.rotationPitch = (rotation.getPitch() + 0.000001f * sign) % 360.0F;
        RotationUtils.INSTANCE.syncRotations();
    }

    @Redirect(method = "handlePlayerPosLook", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V"))
    private void fdp$skipDisplayGuiScreenInDetachedContext(Minecraft instance, GuiScreen screen) {
        if (fdp$isHandlerForDifferentTab()) {
            return;
        }
        if (!SessionRuntimeScope.INSTANCE.isDetachedContextActive() && !(Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread)) {
            instance.displayGuiScreen(screen);
        }
    }

    /**
     * Performs a lightweight loadWorld for background tabs.
     * Creates a new player entity and world, then stores them in a runtime
     * associated with this network handler so they persist independently.
     *
     * When called from a TabSimulationThread, we avoid touching the mc singleton
     * and instead operate only on the runtime's own fields.
     */
    private void loadWorldForDetachedContext(NetHandlerPlayClient handler, WorldClient world) {
        loadWorldForDetachedContext(handler, world, null, true);
    }

    /**
     * Performs a lightweight loadWorld for background tabs.
     * @param statFileWriter If non-null, use this StatFileWriter for the new player
     *                       (preserves stats across respawns). If null, creates a new one.
     * @param notifyQueue   Whether to notify the MultiSelectJoinQueue that this tab
     *                       has connected. Should be true for initial joins, false for respawns.
     */
    private void loadWorldForDetachedContext(NetHandlerPlayClient handler, WorldClient world, StatFileWriter statFileWriter, boolean notifyQueue) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean isSimThread = Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread;

        if (isSimThread) {
            // On a simulation thread: don't touch mc singleton, find the runtime
            // and set its fields directly. The world/player are already being
            // managed by the simulation thread's runtime.
            net.asd.union.handler.sessiontabs.LiveTabRuntime runtime =
                LiveTabRuntimeManager.INSTANCE.findRuntimeForHandler((INetHandler) handler);
            if (runtime != null) {
                PlayerControllerMP controller = runtime.getPlayerController();
                if (controller != null) {
                    StatFileWriter writer = statFileWriter != null ? statFileWriter : new StatFileWriter();
                    EntityPlayerSP newPlayer = controller.func_178892_a(world, writer);
                    controller.flipPlayer(newPlayer);
                    newPlayer.preparePlayerToSpawn();
                    world.spawnEntityInWorld(newPlayer);
                    newPlayer.movementInput = new MovementInputFromOptions(mc.gameSettings);
                    controller.setPlayerCapabilities(newPlayer);
                    runtime.setWorld(world);
                    runtime.setPlayer(newPlayer);
                    runtime.setRenderViewEntity(newPlayer);
                    runtime.setBackgroundNetworkManager(netManager);
                    runtime.setCurrentHandler((INetHandler) handler);
                    runtime.setConnected(true);
                    runtime.setDisconnectedReason(null);
                    runtime.setLastJoinTime(System.currentTimeMillis());

                    // Notify the multi-select queue that this tab has connected.
                    // Only for initial joins — not for respawns (tab is already connected).
                    if (notifyQueue) {
                        net.asd.union.handler.sessiontabs.MultiSelectJoinQueue.INSTANCE.onTabConnected(runtime.getTabId());
                    }

                    ClientUtils.INSTANCE.getLOGGER().info("[TabSim][{}] Detached join on sim thread: world={}, player={}",
                        runtime.debugLabel(), world.provider.getDimensionId(), newPlayer.getName());
                }
            }
            return;
        }

        // Detached context (initial connection setup): temporarily use mc singleton
        mc.theWorld = world;

        if (world != null) {
            EntityPlayerSP newPlayer = mc.playerController.func_178892_a(world, new StatFileWriter());
            mc.playerController.flipPlayer(newPlayer);
            newPlayer.preparePlayerToSpawn();
            world.spawnEntityInWorld(newPlayer);
            newPlayer.movementInput = new MovementInputFromOptions(mc.gameSettings);
            mc.playerController.setPlayerCapabilities(newPlayer);
            mc.thePlayer = newPlayer;

            LiveTabRuntimeManager.INSTANCE.registerDetachedJoin(
                handler,
                mc.session,
                mc.getCurrentServerData(),
                world,
                newPlayer,
                mc.playerController
            );
        }
    }

    @Redirect(method = "handleJoinGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;)V"))
    private void fdp$handleLoadWorldInDetachedContext(Minecraft instance, WorldClient world) {
        if (SessionRuntimeScope.INSTANCE.isDetachedContextActive() || Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            loadWorldForDetachedContext((NetHandlerPlayClient) (Object) this, world);
        } else if (fdp$isHandlerForDifferentTab()) {
            // This handler belongs to a different tab — load the world in the
            // detached context instead of the mc singleton to prevent contamination.
            loadWorldForDetachedContext((NetHandlerPlayClient) (Object) this, world);
        } else {
            instance.loadWorld(world);
        }
    }

    @Redirect(method = "handleJoinGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V"))
    private void fdp$skipJoinGameDisplayGuiScreenInDetachedContext(Minecraft instance, GuiScreen screen) {
        if (fdp$isHandlerForDifferentTab()) {
            // This handler belongs to a different tab — don't modify mc singleton
            return;
        }
        if (!SessionRuntimeScope.INSTANCE.isDetachedContextActive() && !(Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread)) {
            instance.displayGuiScreen(screen);
        }
    }

    @Redirect(method = "handleRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;)V"))
    private void fdp$handleRespawnLoadWorldInDetachedContext(Minecraft instance, WorldClient world) {
        if (SessionRuntimeScope.INSTANCE.isDetachedContextActive() || Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            loadWorldForDetachedContext((NetHandlerPlayClient) (Object) this, world);
        } else if (fdp$isHandlerForDifferentTab()) {
            // This handler belongs to a different tab — load the world in the
            // detached context instead of the mc singleton to prevent contamination.
            loadWorldForDetachedContext((NetHandlerPlayClient) (Object) this, world);
        } else {
            instance.loadWorld(world);
        }
    }

    @Redirect(method = "onDisconnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;)V"))
    private void fdp$skipDisconnectLoadWorldInDetachedContext(Minecraft instance, WorldClient world) {
        if (fdp$isHandlerForDifferentTab()) {
            // This handler belongs to a different tab — don't modify mc singleton
            return;
        }
        if (!SessionRuntimeScope.INSTANCE.isDetachedContextActive() && !(Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread)) {
            instance.loadWorld(world);
        }
    }

    @Redirect(method = "onDisconnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V"))
    private void fdp$skipDisconnectDisplayGuiScreenInDetachedContext(Minecraft instance, GuiScreen screen) {
        if (fdp$isHandlerForDifferentTab()) {
            // This handler belongs to a different tab — don't modify mc singleton
            return;
        }
        if (!SessionRuntimeScope.INSTANCE.isDetachedContextActive() && !(Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread)) {
            instance.displayGuiScreen(screen);
        }
    }

    private boolean isNearPlayerChunk(int chunkX, int chunkZ) {
        // On simulation threads, use the runtime's player position instead of mc.thePlayer
        if (Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            LiveTabRuntime runtime = LiveTabRuntimeManager.INSTANCE.findRuntimeForHandler((INetHandler)(Object)this);
            EntityPlayerSP runtimePlayer = runtime != null ? runtime.getPlayer() : null;
            if (runtimePlayer == null) {
                return false;
            }
            int playerChunkX = MathHelper.floor_double(runtimePlayer.posX) >> 4;
            int playerChunkZ = MathHelper.floor_double(runtimePlayer.posZ) >> 4;
            return Math.abs(playerChunkX - chunkX) <= 1 && Math.abs(playerChunkZ - chunkZ) <= 1;
        }

        if (mc.thePlayer == null) {
            return false;
        }

        int playerChunkX = MathHelper.floor_double(mc.thePlayer.posX) >> 4;
        int playerChunkZ = MathHelper.floor_double(mc.thePlayer.posZ) >> 4;

        return Math.abs(playerChunkX - chunkX) <= 1 && Math.abs(playerChunkZ - chunkZ) <= 1;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Blanket field-access redirects for simulation thread isolation.
    //
    // When a TabSimulationThread is processing a packet, these redirects
    // intercept ALL reads and writes to gameController.thePlayer and
    // gameController.playerController in NetHandlerPlayClient, routing them
    // to the correct LiveTabRuntime instead of the mc singleton.
    //
    // This is thread-safe: the main thread never has its mc fields overwritten
    // by a background tab, eliminating the race condition that caused
    // teleportation and physics corruption during terrain reloads.
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Intercepts ALL reads of gameController.thePlayer (GETFIELD) in this class.
     * On a TabSimulationThread, returns the runtime's player instead of the
     * mc singleton's player. This prevents background tabs from reading the
     * active tab's player position/state, which caused teleportation bugs.
     */
    @Redirect(
            method = "*",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;thePlayer:Lnet/minecraft/client/entity/EntityPlayerSP;", opcode = 180),
            expect = 0
    )
    private EntityPlayerSP fdp$redirectGetThePlayer(Minecraft mcInstance) {
        net.asd.union.handler.sessiontabs.LiveTabRuntime runtime =
                net.asd.union.handler.sessiontabs.TabSimulationThread.getCurrentProcessingRuntime();
        if (runtime != null) {
            EntityPlayerSP player = runtime.getPlayer();
            if (player != null) return player;
        }
        return mcInstance.thePlayer;
    }

    /**
     * Intercepts ALL writes to gameController.thePlayer (PUTFIELD) in this class.
     * On a TabSimulationThread, stores the player in the runtime instead of
     * overwriting the mc singleton. This prevents background tabs from
     * corrupting the active tab's player reference.
     */
    @Redirect(
            method = "*",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;thePlayer:Lnet/minecraft/client/entity/EntityPlayerSP;", opcode = 181),
            expect = 0
    )
    private void fdp$redirectSetThePlayer(Minecraft mcInstance, EntityPlayerSP player) {
        net.asd.union.handler.sessiontabs.LiveTabRuntime runtime =
                net.asd.union.handler.sessiontabs.TabSimulationThread.getCurrentProcessingRuntime();
        if (runtime != null) {
            runtime.setPlayer(player);
            return;
        }
        mcInstance.thePlayer = player;
    }

    /**
     * Intercepts ALL reads of gameController.playerController (GETFIELD).
     * On a TabSimulationThread, returns the runtime's playerController.
     */
    @Redirect(
            method = "*",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;playerController:Lnet/minecraft/client/multiplayer/PlayerControllerMP;", opcode = 180),
            expect = 0
    )
    private PlayerControllerMP fdp$redirectGetPlayerController(Minecraft mcInstance) {
        net.asd.union.handler.sessiontabs.LiveTabRuntime runtime =
                net.asd.union.handler.sessiontabs.TabSimulationThread.getCurrentProcessingRuntime();
        if (runtime != null) {
            PlayerControllerMP controller = runtime.getPlayerController();
            if (controller != null) return controller;
        }
        return mcInstance.playerController;
    }

    /**
     * Intercepts ALL writes to gameController.playerController (PUTFIELD).
     * On a TabSimulationThread, stores in the runtime instead of mc.
     */
    @Redirect(
            method = "*",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;playerController:Lnet/minecraft/client/multiplayer/PlayerControllerMP;", opcode = 181),
            expect = 0
    )
    private void fdp$redirectSetPlayerController(Minecraft mcInstance, PlayerControllerMP controller) {
        net.asd.union.handler.sessiontabs.LiveTabRuntime runtime =
                net.asd.union.handler.sessiontabs.TabSimulationThread.getCurrentProcessingRuntime();
        if (runtime != null) {
            runtime.setPlayerController(controller);
            return;
        }
        mcInstance.playerController = controller;
    }

    /**
     * Intercepts ALL reads of gameController.theWorld (GETFIELD).
     * On a TabSimulationThread, returns the runtime's world.
     */
    @Redirect(
            method = "*",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;theWorld:Lnet/minecraft/client/multiplayer/WorldClient;", opcode = 180),
            expect = 0
    )
    private WorldClient fdp$redirectGetTheWorld(Minecraft mcInstance) {
        net.asd.union.handler.sessiontabs.LiveTabRuntime runtime =
                net.asd.union.handler.sessiontabs.TabSimulationThread.getCurrentProcessingRuntime();
        if (runtime != null) {
            WorldClient world = runtime.getWorld();
            if (world != null) return world;
        }
        return mcInstance.theWorld;
    }

    /**
     * Intercepts ALL writes to gameController.theWorld (PUTFIELD).
     * On a TabSimulationThread, stores in the runtime instead of mc.
     */
    @Redirect(
            method = "*",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;theWorld:Lnet/minecraft/client/multiplayer/WorldClient;", opcode = 181),
            expect = 0
    )
    private void fdp$redirectSetTheWorld(Minecraft mcInstance, WorldClient world) {
        net.asd.union.handler.sessiontabs.LiveTabRuntime runtime =
                net.asd.union.handler.sessiontabs.TabSimulationThread.getCurrentProcessingRuntime();
        if (runtime != null) {
            runtime.setWorld(world);
            return;
        }
        mcInstance.theWorld = world;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // clientWorldController (field_147300_g) redirects for sim thread isolation.
    //
    // clientWorldController is a SHADOW FIELD on NetHandlerPlayClient that many
    // packet handlers read directly instead of through gameController.theWorld.
    // The blanket redirects above only cover gameController.thePlayer/theWorld/
    // playerController — they do NOT intercept clientWorldController access.
    //
    // Without these redirects, when a sim thread processes a packet:
    //   - clientWorldController may be null (if the handler was just created)
    //   - clientWorldController may point to the wrong world (stale reference)
    //   - fdp$suppressNullWorldNPE cancels the handler because clientWorldController is null
    //   - Result: packets are "Routed" but never "Processed"
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Intercepts ALL reads of clientWorldController (GETFIELD) in this class.
     * On a TabSimulationThread, returns the runtime's world instead of the
     * handler's clientWorldController field. This ensures packet handlers on
     * sim threads always see the correct world, even if the handler's
     * clientWorldController field is null or stale.
     */
    @Redirect(
            method = "*",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/NetHandlerPlayClient;field_147300_g:Lnet/minecraft/client/multiplayer/WorldClient;", opcode = 180),
            expect = 0
    )
    private WorldClient fdp$redirectGetClientWorldController(NetHandlerPlayClient handler) {
        // On sim thread, return the runtime's world
        net.asd.union.handler.sessiontabs.LiveTabRuntime runtime =
                net.asd.union.handler.sessiontabs.TabSimulationThread.getCurrentProcessingRuntime();
        if (runtime != null) {
            WorldClient world = runtime.getWorld();
            if (world != null) return world;
        }

        // If clientWorldController is null on the main thread, try to find the
        // runtime's world via the NM mapping. This handles the critical case where:
        //   - A tab is the active tab (so isHandlerForBackgroundTab returns false)
        //   - But its NetHandlerPlayClient was just created by handleLoginSuccess
        //   - clientWorldController is null because handleJoinGame hasn't run yet
        //   - PLAY-state packets arrive and are processed on the main thread
        // Without this fallback, packet handlers NPE on clientWorldController access.
        if (clientWorldController == null) {
            net.asd.union.handler.sessiontabs.LiveTabRuntime nmRuntime =
                    LiveTabRuntimeManager.INSTANCE.findRuntimeByNetworkManagerPublic(netManager);
            if (nmRuntime != null) {
                WorldClient world = nmRuntime.getWorld();
                if (world != null) return world;
            }
        }

        return clientWorldController;
    }

    /**
     * Intercepts ALL writes to clientWorldController (PUTFIELD) in this class.
     * On a TabSimulationThread, stores the world in the runtime AND in the
     * handler's field. This keeps both in sync so that:
     *   - The runtime always has the latest world reference
     *   - Subsequent reads without the ThreadLocal set still work
     */
    @Redirect(
            method = "*",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/NetHandlerPlayClient;field_147300_g:Lnet/minecraft/client/multiplayer/WorldClient;", opcode = 181),
            expect = 0
    )
    private void fdp$redirectSetClientWorldController(NetHandlerPlayClient handler, WorldClient world) {
        net.asd.union.handler.sessiontabs.LiveTabRuntime runtime =
                net.asd.union.handler.sessiontabs.TabSimulationThread.getCurrentProcessingRuntime();
        if (runtime != null) {
            runtime.setWorld(world);
        }
        clientWorldController = world;
    }
}
