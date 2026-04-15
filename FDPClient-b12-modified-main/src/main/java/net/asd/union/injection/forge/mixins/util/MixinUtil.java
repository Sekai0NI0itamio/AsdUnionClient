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
            if (isKnownChunkArrayError(e)) {
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

    private static boolean isKnownChunkArrayError(ExecutionException exception) {
        Throwable cause = exception.getCause();

        if (!(cause instanceof ArrayIndexOutOfBoundsException)) {
            return false;
        }

        for (StackTraceElement element : cause.getStackTrace()) {
            if ("net.minecraft.world.chunk.Chunk".equals(element.getClassName()) && "func_177439_a".equals(element.getMethodName())) {
                return true;
            }

            if ("net.minecraft.client.network.NetHandlerPlayClient".equals(element.getClassName()) && "func_147263_a".equals(element.getMethodName())) {
                return true;
            }
        }

        return "257".equals(cause.getMessage());
    }
}