package net.asd.union.injection.forge.mixins.gui;

import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Safety net for tab switching: GuiInventory.updateScreen() and initGui() both
 * call this.mc.playerController.isInCreativeMode(), and drawScreen() ultimately
 * calls drawEntityOnScreen(..., this.mc.thePlayer). During session tab
 * transitions the mc singleton's playerController/thePlayer can be null,
 * causing NullPointerException crashes. These guards cancel the calls when the
 * required state is missing.
 */
@Mixin(GuiInventory.class)
@SideOnly(Side.CLIENT)
public abstract class MixinGuiInventory extends MixinGuiScreen {

    @Inject(method = "updateScreen", at = @At("HEAD"), cancellable = true)
    private void asdUnion$guardUpdateScreen(CallbackInfo ci) {
        if (mc.playerController == null || mc.thePlayer == null) {
            ci.cancel();
        }
    }

    @Inject(method = "initGui", at = @At("HEAD"), cancellable = true)
    private void asdUnion$guardInitGui(CallbackInfo ci) {
        if (mc.playerController == null || mc.thePlayer == null) {
            ci.cancel();
        }
    }

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    private void asdUnion$guardDrawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (mc.thePlayer == null) {
            ci.cancel();
        }
    }
}
