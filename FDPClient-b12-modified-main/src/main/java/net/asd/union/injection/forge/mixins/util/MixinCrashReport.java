package net.asd.union.injection.forge.mixins.util;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.crash.CrashReport.class)
public class MixinCrashReport {

    @Inject(method = "func_85057_a", at = @At("HEAD"), cancellable = true)
    private void fdp$suppressCrashReportSpam(String p_85057_1_, int p_85057_2_, CallbackInfoReturnable<StringBuilder> cir) {
        if (Thread.currentThread() instanceof net.asd.union.handler.sessiontabs.TabSimulationThread) {
            cir.setReturnValue(new StringBuilder());
        }
    }
}
