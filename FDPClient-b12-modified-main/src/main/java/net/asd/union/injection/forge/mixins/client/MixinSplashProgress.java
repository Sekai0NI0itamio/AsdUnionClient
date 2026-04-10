package net.asd.union.injection.forge.mixins.client;

import net.asd.union.utils.performance.StartupProgress;
import net.asd.union.utils.performance.StartupProgressRenderer;
import net.minecraftforge.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SplashProgress.class, remap = false)
public abstract class MixinSplashProgress {

    @Shadow
    private static boolean enabled;

    @Inject(method = "start", at = @At("HEAD"), cancellable = true, remap = false)
    private static void asd$disableForgeSplash(CallbackInfo callbackInfo) {
        enabled = false;

        if (!StartupProgress.INSTANCE.isActive()) {
            StartupProgress.INSTANCE.start();
        }

        StartupProgressRenderer.render();
        callbackInfo.cancel();
    }
}
