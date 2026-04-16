/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.network;

import net.asd.union.handler.sessiontabs.LiveTabRuntimeManager;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.util.IThreadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketThreadUtil.class)
public class MixinPacketThreadUtil {

    @Inject(method = "checkThreadAndEnqueue", at = @At("HEAD"), cancellable = true)
    private static void fdp$routeDetachedPacketTasks(Packet<?> packet, INetHandler handler, IThreadListener listener, CallbackInfo ci) throws ThreadQuickExitException {
        if (listener.isCallingFromMinecraftThread()) {
            return;
        }

        if (LiveTabRuntimeManager.INSTANCE.enqueueScheduledTask(handler, () -> {
            @SuppressWarnings("unchecked")
            Packet<INetHandler> rawPacket = (Packet<INetHandler>) packet;
            rawPacket.processPacket(handler);
        })) {
            ci.cancel();
            throw ThreadQuickExitException.INSTANCE;
        }
    }
}
