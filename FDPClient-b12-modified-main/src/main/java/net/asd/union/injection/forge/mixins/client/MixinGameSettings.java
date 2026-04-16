package net.asd.union.injection.forge.mixins.client;

import net.asd.union.handler.sessiontabs.SessionRuntimeScope;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameSettings.class)
public class MixinGameSettings {

    @Shadow public int guiScale;

    /**
     * Defaults gui scale to 2
     *
     * @reason Most people use 2x gui scale, so we default to that and most UI elements are designed for it
     * @param callbackInfo Unused
     */
    @Inject(method = "<init>()V", at = @At("RETURN"))
    private void injectGuiScaleDefault(final CallbackInfo callbackInfo) {
        this.guiScale = 2;
    }

    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void fdp$blockDetachedKeyState(final KeyBinding keyBinding, final CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (SessionRuntimeScope.INSTANCE.isDetachedContextActive()) {
            Boolean mirroredState = SessionRuntimeScope.INSTANCE.resolveDetachedKeyState(keyBinding);
            callbackInfoReturnable.setReturnValue(mirroredState != null ? mirroredState : false);
        }
    }

}
