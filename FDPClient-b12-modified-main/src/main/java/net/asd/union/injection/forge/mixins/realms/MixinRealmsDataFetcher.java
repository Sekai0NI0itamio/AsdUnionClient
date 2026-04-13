/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */

package net.asd.union.injection.forge.mixins.realms;

import com.mojang.realmsclient.gui.RealmsDataFetcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RealmsDataFetcher.class)
public abstract class MixinRealmsDataFetcher {
    private static final Logger LOGGER = LogManager.getLogger("RealmsBlocker");

    @Inject(method = "initWithSpecificTaskList", at = @At("HEAD"), cancellable = true, remap = false)
    private void onInitWithSpecificTaskList(java.util.List tasks, CallbackInfo ci) {
        LOGGER.info("Blocked RealmsDataFetcher initWithSpecificTaskList");
        ci.cancel();
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true, remap = false)
    private void onInit(CallbackInfo ci) {
        LOGGER.info("Blocked RealmsDataFetcher init");
        ci.cancel();
    }

    @Inject(method = "forceUpdate", at = @At("HEAD"), cancellable = true, remap = false)
    private void onForceUpdate(CallbackInfo ci) {
        LOGGER.info("Blocked RealmsDataFetcher forceUpdate");
        ci.cancel();
    }
}
