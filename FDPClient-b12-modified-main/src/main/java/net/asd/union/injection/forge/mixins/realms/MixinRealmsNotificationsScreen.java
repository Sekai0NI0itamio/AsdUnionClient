/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */

package net.asd.union.injection.forge.mixins.realms;

import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RealmsNotificationsScreen.class)
public abstract class MixinRealmsNotificationsScreen {
    private static final Logger LOGGER = LogManager.getLogger("RealmsBlocker");

    @Inject(method = "checkIfMcoEnabled", at = @At("HEAD"), cancellable = true, remap = false)
    private void onCheckIfMcoEnabled(CallbackInfo ci) {
        LOGGER.info("Blocked RealmsNotificationsScreen.checkIfMcoEnabled");
        ci.cancel();
    }
}
