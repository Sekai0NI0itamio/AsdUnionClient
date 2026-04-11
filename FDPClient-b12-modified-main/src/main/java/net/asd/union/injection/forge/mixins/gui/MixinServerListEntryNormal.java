package net.asd.union.injection.forge.mixins.gui;

import net.asd.union.utils.client.ServerPingController;
import net.minecraft.client.gui.ServerListEntryNormal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

@Mixin(ServerListEntryNormal.class)
public abstract class MixinServerListEntryNormal {

    @Redirect(
            method = "drawEntry",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/ThreadPoolExecutor;submit(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;",
                    remap = false
            )
    )
    private Future<?> fdp$submitManagedPingTask(ThreadPoolExecutor executor, Runnable task) {
        return ServerPingController.submitPingTask(executor, task);
    }
}
