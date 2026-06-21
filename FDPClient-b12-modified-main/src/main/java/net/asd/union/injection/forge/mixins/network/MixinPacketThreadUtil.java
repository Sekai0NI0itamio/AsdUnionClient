/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.network;

import net.asd.union.handler.sessiontabs.LiveTabRuntimeManager;
import net.asd.union.handler.sessiontabs.TabSimulationThread;
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
        // If we're on a TabSimulationThread, skip the thread check entirely.
        // The sim thread IS the correct thread for processing this tab's packets.
        if (Thread.currentThread() instanceof TabSimulationThread) {
            ci.cancel();
            return;
        }

        // BULLETPROOF ISOLATION CHECK: If this handler belongs to a different
        // tab than the currently active one, we must NOT process it on the main
        // thread. Processing it would overwrite mc.theWorld/mc.thePlayer with
        // the background tab's state, making the active tab unusable.
        //
        // This uses direct NetworkManager comparison via reflection, which is
        // the most reliable way to determine tab ownership.
        if (LiveTabRuntimeManager.INSTANCE.isHandlerForDifferentTab(handler)) {
            // Try to route it to the sim thread, or drop it.
            if (LiveTabRuntimeManager.INSTANCE.enqueueScheduledTask(handler, () -> {
                @SuppressWarnings("unchecked")
                Packet<INetHandler> rawPacket = (Packet<INetHandler>) packet;
                try {
                    rawPacket.processPacket(handler);
                } catch (RuntimeException e) {
                    if (e instanceof NullPointerException || e instanceof IndexOutOfBoundsException) {
                        LiveTabRuntimeManager.INSTANCE.logPacketError(packet, handler, e);
                    }
                }
            })) {
                ci.cancel();
                throw ThreadQuickExitException.INSTANCE;
            }
            // Can't route to sim thread — drop the packet entirely.
            // Processing it on the main thread would corrupt the active tab.
            ci.cancel();
            throw ThreadQuickExitException.INSTANCE;
        }

        if (listener.isCallingFromMinecraftThread()) {
            return;
        }

        // If this packet belongs to a background tab, enqueue it to that tab's
        // simulation thread or task queue instead of the main thread's queue.
        if (LiveTabRuntimeManager.INSTANCE.enqueueScheduledTask(handler, () -> {
            @SuppressWarnings("unchecked")
            Packet<INetHandler> rawPacket = (Packet<INetHandler>) packet;
            try {
                rawPacket.processPacket(handler);
            } catch (RuntimeException e) {
                if (e instanceof NullPointerException || e instanceof IndexOutOfBoundsException) {
                    LiveTabRuntimeManager.INSTANCE.logPacketError(packet, handler, e);
                }
            }
        })) {
            ci.cancel();
            throw ThreadQuickExitException.INSTANCE;
        }

        // If enqueueScheduledTask failed, check if this handler belongs to a
        // background tab. If so, the packet MUST NOT be processed on the main
        // thread — it would access a null clientWorldController and corrupt
        // the active tab's state. Drop it instead.
        if (LiveTabRuntimeManager.INSTANCE.isHandlerForBackgroundTab(handler)) {
            ci.cancel();
            throw ThreadQuickExitException.INSTANCE;
        }

        // Additional safety net: check if the handler belongs to a tab whose
        // world is not yet ready (clientWorldController is null because
        // handleJoinGame hasn't been processed yet).
        if (LiveTabRuntimeManager.INSTANCE.isHandlerWorldNotReady(handler)) {
            ci.cancel();
            throw ThreadQuickExitException.INSTANCE;
        }
    }
}
