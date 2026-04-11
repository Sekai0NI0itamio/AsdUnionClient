/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.entity;

import net.asd.union.features.module.modules.visual.NameProtect;
import net.asd.union.features.module.modules.visual.NoFOV;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.init.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

import static net.asd.union.utils.client.MinecraftInstance.mc;

@Mixin(AbstractClientPlayer.class)
@SideOnly(Side.CLIENT)
public abstract class MixinAbstractClientPlayer extends MixinEntityPlayer {

    @Inject(method = "getFovModifier", at = @At("HEAD"), cancellable = true)
    private void getFovModifier(CallbackInfoReturnable<Float> callbackInfoReturnable) {
        final NoFOV fovModule = NoFOV.INSTANCE;

        if (fovModule.handleEvents()) {
            float newFOV = fovModule.getFov();

            if (!isUsingItem()) {
                callbackInfoReturnable.setReturnValue(newFOV);
                return;
            }

            if (getItemInUse().getItem() != Items.bow) {
                callbackInfoReturnable.setReturnValue(newFOV);
                return;
            }

            int i = getItemInUseDuration();
            float f1 = (float) i / 20f;
            f1 = f1 > 1f ? 1f : f1 * f1;
            newFOV *= 1f - f1 * 0.15f;
            callbackInfoReturnable.setReturnValue(newFOV);
        }
    }

    @Inject(method = "getLocationSkin()Lnet/minecraft/util/ResourceLocation;", at = @At("HEAD"), cancellable = true)
    private void getSkin(CallbackInfoReturnable<ResourceLocation> callbackInfoReturnable) {
        final NameProtect nameProtect = NameProtect.INSTANCE;

        if (nameProtect.handleEvents() && nameProtect.getSkinProtect()) {
            if (!nameProtect.getAllPlayers() && !Objects.equals(getGameProfile().getName(), mc.thePlayer.getGameProfile().getName()))
                return;

            callbackInfoReturnable.setReturnValue(DefaultPlayerSkin.getDefaultSkin(getUniqueID()));
        }
    }
}
