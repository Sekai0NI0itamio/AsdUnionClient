package net.asd.union.injection.forge.mixins.gui;

import net.asd.union.ui.client.gui.GuiOptimize;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiVideoSettings.class)
public abstract class MixinGuiVideoSettings extends GuiScreen {

    @Inject(method = "initGui", at = @At("RETURN"))
    private void addCustomButtons(CallbackInfo ci) {
        this.buttonList.add(new GuiButton(9001, 5, this.height - 25, 80, 20, "Optimize"));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"))
    private void onActionPerformed(GuiButton button, CallbackInfo ci) {
        if (button.id == 9001) {
            this.mc.displayGuiScreen(new GuiOptimize(this));
        }
    }
}
