/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.gui;

import net.asd.union.FDPClient;
import net.asd.union.features.command.CommandManager;
import net.asd.union.features.module.modules.combat.AutoArmor;
import net.asd.union.features.module.modules.client.HUDModule;
import net.asd.union.handler.sessiontabs.ClientTabManager;
import net.asd.union.ui.client.gui.GuiClientConfiguration;
import net.asd.union.utils.inventory.ArmorComparator;
import net.asd.union.utils.render.shader.Background;
import net.asd.union.utils.render.ParticleUtils;
import net.asd.union.utils.render.shader.shaders.BackgroundShader;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static net.minecraft.client.renderer.GlStateManager.disableFog;
import static net.minecraft.client.renderer.GlStateManager.disableLighting;

@Mixin(GuiScreen.class)
@SideOnly(Side.CLIENT)
public abstract class MixinGuiScreen {
    @Shadow
    public Minecraft mc;

    @Shadow
    protected List<GuiButton> buttonList;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Shadow
    protected FontRenderer fontRendererObj;

    @Shadow
    public void updateScreen() {
    }

    @Shadow
    public abstract void handleComponentHover(IChatComponent component, int x, int y);

    @Shadow
    protected abstract void drawHoveringText(List<String> textLines, int x, int y, FontRenderer fontRenderer);

    @Unique
    private ItemStack asdUnion$currentTooltipStack;


    @Inject(method = "drawWorldBackground", at = @At("HEAD"))
    private void drawWorldBackground(final CallbackInfo callbackInfo) {
        final HUDModule hud = HUDModule.INSTANCE;

        if (hud.getInventoryParticle() && mc.thePlayer != null) {
            final ScaledResolution scaledResolution = new ScaledResolution(mc);
            final int width = scaledResolution.getScaledWidth();
            final int height = scaledResolution.getScaledHeight();
            ParticleUtils.INSTANCE.drawParticles(Mouse.getX() * width / mc.displayWidth, height - Mouse.getY() * height / mc.displayHeight - 1);
        }
    }


    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void drawParticles(final int mouseX, final int mouseY, final float partialTicks, final CallbackInfo callbackInfo) {
        // Particles functionality removed - do nothing
        // Original functionality was removed to simplify client
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void drawClientTabs(final int mouseX, final int mouseY, final float partialTicks, final CallbackInfo callbackInfo) {
        ClientTabManager.INSTANCE.renderOnScreen((GuiScreen) (Object) this, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void handleClientTabMouse(final int mouseX, final int mouseY, final int mouseButton, final CallbackInfo callbackInfo) {
        if (ClientTabManager.INSTANCE.handleScreenMouseClick((GuiScreen) (Object) this, mouseX, mouseY, mouseButton)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void messageSend(String msg, boolean addToChat, final CallbackInfo callbackInfo) {
        if (msg.startsWith(String.valueOf(CommandManager.INSTANCE.getPrefix())) && addToChat) {
            mc.ingameGUI.getChatGUI().addToSentMessages(msg);

            CommandManager.INSTANCE.executeCommands(msg);
            callbackInfo.cancel();
        }
    }

    @Inject(method = "handleComponentHover", at = @At("HEAD"))
    private void handleHoverOverComponent(IChatComponent component, int x, int y, final CallbackInfo callbackInfo) {
        if (component == null || component.getChatStyle().getChatClickEvent() == null)
            return;

        final ChatStyle chatStyle = component.getChatStyle();

        final ClickEvent clickEvent = chatStyle.getChatClickEvent();
        final HoverEvent hoverEvent = chatStyle.getChatHoverEvent();

        drawHoveringText(Collections.singletonList("§c§l" + clickEvent.getAction().getCanonicalName().toUpperCase() + ": §a" + clickEvent.getValue()), x, y - (hoverEvent != null ? 17 : 0), fontRendererObj);
    }

    @Inject(method = "renderToolTip", at = @At("HEAD"))
    private void captureHoveredArmorStack(ItemStack stack, int x, int y, final CallbackInfo callbackInfo) {
        asdUnion$currentTooltipStack = stack;
    }

    @Inject(method = "renderToolTip", at = @At("RETURN"))
    private void clearHoveredArmorStack(ItemStack stack, int x, int y, final CallbackInfo callbackInfo) {
        asdUnion$currentTooltipStack = null;
    }

    @ModifyArg(
            method = "renderToolTip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;drawHoveringText(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V"),
            index = 0
    )
    private List<String> addAutoArmorTooltip(List<String> textLines) {
        ItemStack stack = asdUnion$currentTooltipStack;
        if (AutoArmor.INSTANCE.handleEvents() && stack != null && stack.getItem() instanceof ItemArmor) {
            List<String> tooltipLines = new ArrayList<>(textLines);
            double armorScore = ArmorComparator.getArmorScore(stack);
            ItemArmor armorItem = (ItemArmor) stack.getItem();
            int armorPoints = armorItem.getArmorMaterial().getDamageReductionAmount(armorItem.armorType);
            int protectionLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack);
            String scoreText = String.format(Locale.ROOT, "%.3f", armorScore);

            tooltipLines.add("§7AutoArmor score: §e" + scoreText + " §7(armor " + armorPoints + ", prot " + protectionLevel + ")");
            return tooltipLines;
        }

        return textLines;
    }

    /**
     * @author CCBlueX (superblaubeere27)
     * @reason Making it possible for other mixins to receive actions
     */
    @Overwrite
    protected void actionPerformed(GuiButton button) {
        injectedActionPerformed(button);
    }

    protected void injectedActionPerformed(GuiButton button) {

    }
}
