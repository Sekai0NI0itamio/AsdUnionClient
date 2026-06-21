/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import org.apache.logging.log4j.Logger;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Util.class)
public class MixinUtil {

    @Inject(method = "func_181617_a", at = @At("HEAD"), cancellable = true)
    private static void onRunTask(FutureTask<?> futureTask, Logger logger, CallbackInfoReturnable<Object> cir) {
        try {
            futureTask.run();
            cir.setReturnValue(futureTask.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cir.setReturnValue(null);
        } catch (ExecutionException e) {
            if (isKnownClientTaskError(e)) {
                cir.setReturnValue(null);
                return;
            }

            logger.error("Error executing task", e);
            cir.setReturnValue(null);
        } catch (Throwable t) {
            logger.error("Error executing task", t);
            cir.setReturnValue(null);
        }
    }

    private static boolean isKnownClientTaskError(ExecutionException exception) {
        Throwable cause = exception.getCause();

        if (cause instanceof ArrayIndexOutOfBoundsException || cause instanceof IndexOutOfBoundsException) {
            // AIOOBE with no message or "257" is always from chunk access with
            // missing sections — safe to suppress unconditionally
            if (cause.getMessage() == null || cause.getMessage().isEmpty() || "257".equals(cause.getMessage())) {
                return true;
            }

            for (StackTraceElement element : cause.getStackTrace()) {
                if ("net.minecraft.world.chunk.Chunk".equals(element.getClassName()) && "func_177439_a".equals(element.getMethodName())) {
                    return true;
                }

                if ("net.minecraft.client.network.NetHandlerPlayClient".equals(element.getClassName()) && "func_147263_a".equals(element.getMethodName())) {
                    return true;
                }

                // AIOOBE from handleUpdateTileEntity when chunk sections aren't loaded yet
                if ("net.minecraft.client.network.NetHandlerPlayClient".equals(element.getClassName()) && "func_147273_a".equals(element.getMethodName())) {
                    return true;
                }
            }

            // Suppress all AIOOBEs from PacketThreadUtil tasks — these are always
            // from packets processed before the world is fully loaded
            for (StackTraceElement element : cause.getStackTrace()) {
                if ("net.minecraft.network.PacketThreadUtil$1".equals(element.getClassName()) && "run".equals(element.getMethodName())) {
                    return true;
                }
            }

            // Suppress AIOOBEs from any scheduled FutureTask on the main thread
            // that originate from chunk/world access during tab connections.
            // These are always transient and non-fatal.
            for (StackTraceElement element : cause.getStackTrace()) {
                if ("net.minecraft.util.Util".equals(element.getClassName()) && "func_181617_a".equals(element.getMethodName())) {
                    return true;
                }
            }

            return false;
        }

        if (!(cause instanceof NullPointerException)) {
            return false;
        }

        // Pre-scan: determine if the error came from a scheduled packet processing task.
        // We do this in a separate pass because the order of stack elements matters:
        // the NPE is thrown inside NetHandlerPlayClient.func_147273_a, which appears
        // HIGHER in the stack than PacketThreadUtil$1.run (the runner). Iterating
        // top-to-bottom and checking both per-element causes us to return false
        // before we see the PacketThreadUtil frame, so the suppression check fails.
        boolean packetThreadTask = false;
        for (StackTraceElement element : cause.getStackTrace()) {
            if ("net.minecraft.network.PacketThreadUtil$1".equals(element.getClassName()) && "run".equals(element.getMethodName())) {
                packetThreadTask = true;
                break;
            }
        }

        for (StackTraceElement element : cause.getStackTrace()) {
            if ("net.minecraft.client.network.NetHandlerPlayClient".equals(element.getClassName())
                    && ("func_147239_a".equals(element.getMethodName())
                        || "func_147285_a".equals(element.getMethodName())
                        || "func_147247_a".equals(element.getMethodName())  // handleTeams - NPE when clientWorldController is null
                        || "func_147256_a".equals(element.getMethodName())  // handleScoreboardObjective
                        || "func_147249_a".equals(element.getMethodName())  // handleUpdateScore
                        || "func_147266_a".equals(element.getMethodName())  // handleDisplayScoreboard
                        || "func_147273_a".equals(element.getMethodName())  // handleUpdateTileEntity
                        || "func_147279_a".equals(element.getMethodName())  // handleEntityAnimation
                        || "func_147238_a".equals(element.getMethodName())  // handleDestroyEntities
                        || "func_147235_a".equals(element.getMethodName())  // handleSpawnObject - NPE in WorldClient.removeEntityFromWorld
                    )) {
                // Always suppress these NPEs from packet handlers - they are transient
                // and non-fatal. The server can send stale packets for tile entities
                // that don't exist on the client, or the world may be unloading.
                return true;
            }

            if ("net.minecraft.client.multiplayer.WorldClient".equals(element.getClassName()) && "func_73045_a".equals(element.getMethodName())) {
                return true;
            }
        }

        return false;
    }
}
