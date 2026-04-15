package net.asd.union.injection.forge.mixins.network;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(targets = "com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService")
public abstract class MixinYggdrasilSessionService {

    @Inject(method = "fillGameProfile", at = @At("HEAD"), cancellable = true, remap = false)
    private void blockProfileLookup(GameProfile profile, boolean requireSecure, CallbackInfoReturnable<GameProfile> cir) {
        cir.setReturnValue(profile);
    }

    @Inject(method = "getTextures", at = @At("HEAD"), cancellable = true, remap = false)
    private void blockAllSkins(GameProfile profile, boolean requireSecure, CallbackInfoReturnable<Map<MinecraftProfileTexture.Type, MinecraftProfileTexture>> cir) {
        cir.setReturnValue(new HashMap<>());
        cir.cancel();
    }
}
