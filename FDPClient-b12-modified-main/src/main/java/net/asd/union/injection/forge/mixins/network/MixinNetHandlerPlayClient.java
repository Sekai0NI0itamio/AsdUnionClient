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
import net.asd.union.utils.client.ClientUtils;
import net.asd.union.utils.client.PacketUtils;
import net.asd.union.utils.rotation.Rotation;
import net.asd.union.utils.rotation.RotationUtils;
import net.asd.union.utils.extensions.PlayerExtensionKt;
import net.asd.union.utils.kotlin.RandomUtils;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.client.C19PacketResourcePackStatus;
import net.minecraft.network.play.server.*;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Redirect(method = "handleChangeGameState", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/play/server/S2BPacketChangeGameState;getGameState()I"))
    private int onChangeGameState(S2BPacketChangeGameState packet) {
        return packet.getGameState();
    }

    @Inject(method = "handleTitle", at = @At("HEAD"), cancellable = true)
    private void noTitle$cancelServerTitles(S45PacketTitle packetIn, CallbackInfo ci) {
        if (NoTitle.INSTANCE.getEnabled()) {
            NoTitle.INSTANCE.clearRenderedTitle();
            ci.cancel();
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

    @Inject(method = "handleMapChunkBulk", at = @At("HEAD"), cancellable = true)
    private void onChunkBulk(S26PacketMapChunkBulk packet, CallbackInfo ci) {
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
    }

    @Inject(method = "handleMapChunkBulk", at = @At("RETURN"))
    private void cacheBulkChunksAfterLoad(S26PacketMapChunkBulk packet, CallbackInfo ci) {
        if (!LazyChunkCache.INSTANCE.getEnabled() || clientWorldController == null) {
            return;
        }

        for (int index = 0; index < packet.getChunkCount(); index++) {
            int chunkX = packet.getChunkX(index);
            int chunkZ = packet.getChunkZ(index);
            Chunk chunk = clientWorldController.getChunkFromChunkCoords(chunkX, chunkZ);

            if (chunk != null && !chunk.getAreLevelsEmpty(0, 255)) {
                LazyChunkCache.INSTANCE.add(chunkX, chunkZ);
            }
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

    @Redirect(
            method = "handleRespawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V"
            )
    )
    private void antiSpawnLag$skipRespawnLoadingScreen(Minecraft instance, GuiScreen guiScreen) {
        if (guiScreen instanceof GuiDownloadTerrain && AntiSpawnLag.INSTANCE.consumeTerrainScreenBypass()) {
            instance.displayGuiScreen(null);
        } else {
            instance.displayGuiScreen(guiScreen);
        }
    }

    @Inject(method = "handleJoinGame", at = @At("HEAD"), cancellable = true)
    private void handleJoinGameWithAntiForge(S01PacketJoinGame packetIn, final CallbackInfo callbackInfo) {
        if (!ClientFixes.INSTANCE.getFmlFixesEnabled() || !ClientFixes.INSTANCE.getBlockFML() || mc.isIntegratedServerRunning())
            return;

        PacketThreadUtil.checkThreadAndEnqueue(packetIn, (NetHandlerPlayClient) (Object) this, gameController);
        gameController.playerController = new PlayerControllerMP(gameController, (NetHandlerPlayClient) (Object) this);
        clientWorldController = new WorldClient((NetHandlerPlayClient) (Object) this, new WorldSettings(0L, packetIn.getGameType(), false, packetIn.isHardcoreMode(), packetIn.getWorldType()), packetIn.getDimension(), packetIn.getDifficulty(), gameController.mcProfiler);
        gameController.gameSettings.difficulty = packetIn.getDifficulty();
        gameController.loadWorld(clientWorldController);
        gameController.thePlayer.dimension = packetIn.getDimension();
        gameController.displayGuiScreen(new GuiDownloadTerrain((NetHandlerPlayClient) (Object) this));
        gameController.thePlayer.setEntityId(packetIn.getEntityId());
        currentServerMaxPlayers = packetIn.getMaxPlayers();
        gameController.thePlayer.setReducedDebug(packetIn.isReducedDebugInfo());
        gameController.playerController.setGameType(packetIn.getGameType());
        gameController.gameSettings.sendSettingsToServer();
        netManager.sendPacket(new C17PacketCustomPayload("MC|Brand", (new PacketBuffer(Unpooled.buffer())).writeString(ClientBrandRetriever.getClientModName())));
        callbackInfo.cancel();
    }

    @Inject(method = "handleEntityMovement", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;onGround:Z"))
    private void handleEntityMovementEvent(S14PacketEntity packetIn, final CallbackInfo callbackInfo) {
        final Entity entity = packetIn.getEntity(clientWorldController);

        if (entity != null)
            EventManager.INSTANCE.call(new EntityMovementEvent(entity));
    }

    @Inject(method = "handlePlayerPosLook", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayer;setPositionAndRotation(DDDFF)V", shift = At.Shift.BEFORE))
    private void injectNoRotateSetPositionOnly(S08PacketPlayerPosLook p_handlePlayerPosLook_1_, CallbackInfo ci) {
        NoRotateSet module = NoRotateSet.INSTANCE;

        // Save the server's requested rotation before it resets the rotations
        module.setSavedRotation(PlayerExtensionKt.getRotation(Minecraft.getMinecraft().thePlayer));
    }

    @Redirect(method = "handlePlayerPosLook", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkManager;sendPacket(Lnet/minecraft/network/Packet;)V"))
    private void injectNoRotateSetAndAntiServerRotationOverride(NetworkManager instance, Packet p_sendPacket_1_) {
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

        // Slightly modify the client-side rotations, so they pass the rotation difference check in onUpdateWalkingPlayer, EntityPlayerSP.
        player.rotationYaw = (rotation.getYaw() + 0.000001f * sign) % 360.0F;
        player.rotationPitch = (rotation.getPitch() + 0.000001f * sign) % 360.0F;
        RotationUtils.INSTANCE.syncRotations();
    }

    private boolean isNearPlayerChunk(int chunkX, int chunkZ) {
        if (mc.thePlayer == null) {
            return false;
        }

        int playerChunkX = MathHelper.floor_double(mc.thePlayer.posX) >> 4;
        int playerChunkZ = MathHelper.floor_double(mc.thePlayer.posZ) >> 4;

        return Math.abs(playerChunkX - chunkX) <= 1 && Math.abs(playerChunkZ - chunkZ) <= 1;
    }
}
