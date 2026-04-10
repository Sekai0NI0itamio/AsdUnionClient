/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.gui;

import net.asd.union.features.module.modules.combat.AutoArmor;
import net.asd.union.features.module.modules.player.InventoryCleaner;
import net.asd.union.utils.inventory.InventoryManager;
import net.asd.union.utils.render.RenderUtils;
import net.asd.union.utils.timing.TickTimer;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

import static org.lwjgl.opengl.GL11.*;

@Mixin(GuiContainer.class)
@SideOnly(Side.CLIENT)
public abstract class MixinGuiContainer extends MixinGuiScreen {

    // Separate TickTimer instances to avoid timing conflicts
    @Unique
    final TickTimer tick0 = new TickTimer();
    @Unique
    final TickTimer tick1 = new TickTimer();
    @Unique
    final TickTimer tick2 = new TickTimer();

    @Inject(method = "initGui", at = @At("RETURN"), cancellable = true)
    private void init(CallbackInfo ci) {
    }

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    private void drawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
    }

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void drawSlot(Slot slot, CallbackInfo ci) {
        // Instances
        final InventoryManager inventoryManager = InventoryManager.INSTANCE;
        final InventoryCleaner inventoryCleaner = InventoryCleaner.INSTANCE;
        final AutoArmor autoArmor = AutoArmor.INSTANCE;
        final RenderUtils renderUtils = RenderUtils.INSTANCE;

        // Slot X/Y
        int x = slot.xDisplayPosition;
        int y = slot.yDisplayPosition;

        // InvCleaner & AutoArmor Highlight Values
        int invManagerBackgroundColor = inventoryManager.getBackgroundColor().selectedColor().getRGB();
        int invManagerBorderColor = inventoryManager.getBorderColor().selectedColor().getRGB();

        // Get the current slot being stolen
        int currentSlotInvCleaner = inventoryManager.getInvCleanerCurrentSlot();
        int currentSlotAutoArmor = inventoryManager.getAutoArmorCurrentSlot();

        glPushMatrix();
        glPushAttrib(GL_ENABLE_BIT);
        glDisable(GL_LIGHTING);

        if (mc.currentScreen instanceof GuiInventory) {
            if (inventoryManager.getHighlightSlotValue().get()) {
                if (inventoryCleaner.handleEvents()) {
                    if (slot.slotNumber == currentSlotInvCleaner && currentSlotInvCleaner != -1 && currentSlotInvCleaner != inventoryManager.getInvCleanerLastSlot()) {
                        renderUtils.drawBorderedRect(x, y, x + 16, y + 16, inventoryManager.getBorderStrength().get(), invManagerBorderColor, invManagerBackgroundColor);

                        // Prevent rendering the highlighted rectangle twice
                        if (!slot.getHasStack() && tick1.hasTimePassed(100)) {
                            inventoryManager.setInvCleanerLastSlot(currentSlotInvCleaner);
                            tick1.reset();
                        } else {
                            tick1.update();
                        }
                    }
                }

                if (autoArmor.handleEvents()) {
                    if (slot.slotNumber == currentSlotAutoArmor && currentSlotAutoArmor != -1 && currentSlotAutoArmor != inventoryManager.getAutoArmorLastSlot()) {
                        renderUtils.drawBorderedRect(x, y, x + 16, y + 16, inventoryManager.getBorderStrength().get(), invManagerBorderColor, invManagerBackgroundColor);

                        // Prevent rendering the highlighted rectangle twice
                        if (!slot.getHasStack() && tick2.hasTimePassed(100)) {
                            inventoryManager.setAutoArmorLastSlot(currentSlotAutoArmor);
                            tick2.reset();
                        } else {
                            tick2.update();
                        }
                    }
                }
            }
        }
        glPopAttrib();
        glPopMatrix();
    }
}
