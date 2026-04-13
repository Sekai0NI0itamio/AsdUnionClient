/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */

package net.asd.union.injection.forge.mixins.realms;

import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.PendingInvitesList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RealmsClient.class)
public abstract class MixinRealmsClient {
    private static final Logger LOGGER = LogManager.getLogger("RealmsBlocker");

    @Inject(method = "pendingInvites", at = @At("HEAD"), cancellable = true, remap = false)
    private void onPendingInvites(CallbackInfoReturnable<PendingInvitesList> ci) {
        LOGGER.info("Blocked Realms pendingInvites request");
        ci.setReturnValue(null);
    }

    @Inject(method = "pendingInvitesCount", at = @At("HEAD"), cancellable = true, remap = false)
    private void onPendingInvitesCount(CallbackInfoReturnable<Integer> ci) {
        LOGGER.info("Blocked Realms pendingInvitesCount request");
        ci.setReturnValue(0);
    }

    @Inject(method = "mcoEnabled", at = @At("HEAD"), cancellable = true, remap = false)
    private void onMcoEnabled(CallbackInfoReturnable<Boolean> ci) {
        LOGGER.info("Blocked Realms mcoEnabled check");
        ci.setReturnValue(false);
    }

    @Inject(method = "stageAvailable", at = @At("HEAD"), cancellable = true, remap = false)
    private void onStageAvailable(CallbackInfoReturnable<Boolean> ci) {
        LOGGER.info("Blocked Realms stageAvailable check");
        ci.setReturnValue(false);
    }

    @Inject(method = "trialAvailable", at = @At("HEAD"), cancellable = true, remap = false)
    private void onTrialAvailable(CallbackInfoReturnable<Boolean> ci) {
        LOGGER.info("Blocked Realms trialAvailable check");
        ci.setReturnValue(false);
    }
}
