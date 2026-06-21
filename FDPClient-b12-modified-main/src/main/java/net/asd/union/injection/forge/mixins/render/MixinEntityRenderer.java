/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.render;

import com.google.common.base.Predicates;
import net.asd.union.event.EventManager;
import net.asd.union.event.Render3DEvent;
import net.asd.union.features.module.modules.combat.Backtrack;
import net.asd.union.features.module.modules.combat.ForwardTrack;
import net.asd.union.features.module.modules.other.OverrideRaycast;
import net.asd.union.features.module.modules.visual.*;
import net.asd.union.utils.rotation.Rotation;
import net.asd.union.utils.rotation.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Mixin(EntityRenderer.class)
@SideOnly(Side.CLIENT)
public abstract class MixinEntityRenderer {

    @Shadow
    private Entity pointedEntity;

    @Shadow
    private Minecraft mc;

    @Shadow
    private float thirdPersonDistanceTemp;

    @Shadow
    private float thirdPersonDistance;

    @Mutable
    @Final
    @Shadow
    private final int[] lightmapColors;
    @Mutable
    @Final
    @Shadow
    private final DynamicTexture lightmapTexture;

    @Shadow
    private final float torchFlickerX;

    @Shadow
    private final float bossColorModifier;
    @Shadow
    private final float bossColorModifierPrev;

    @Shadow
    private boolean lightmapUpdateNeeded;

    protected MixinEntityRenderer(int[] lightmapColors, DynamicTexture lightmapTexture, float torchFlickerX, float bossColorModifier, float bossColorModifierPrev, Minecraft mc, float thirdPersonDistanceTemp, float thirdPersonDistance) {
        this.lightmapColors = lightmapColors;
        this.lightmapTexture = lightmapTexture;
        this.torchFlickerX = torchFlickerX;
        this.bossColorModifier = bossColorModifier;
        this.bossColorModifierPrev = bossColorModifierPrev;
        this.mc = mc;
        this.thirdPersonDistanceTemp = thirdPersonDistanceTemp;
        this.thirdPersonDistance = thirdPersonDistance;
    }

    /**
     * Guard updateRenderer against NPE when mc state is cleared during tab switches.
     * updateRenderer accesses mc.theWorld.getLightBrightness() and
     * mc.getRenderViewEntity() unconditionally, which NPEs when theWorld
     * or thePlayer is null after activating a disconnected tab.
     */
    @Inject(method = "updateRenderer", at = @At("HEAD"), cancellable = true)
    private void fdp$guardUpdateRenderer(CallbackInfo ci) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            ci.cancel();
        }
    }

    @Inject(method = "renderWorldPass", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand:Z", shift = At.Shift.BEFORE))
    private void renderWorldPass(int pass, float partialTicks, long finishTimeNano, CallbackInfo callbackInfo) {
        EventManager.INSTANCE.call(new Render3DEvent(partialTicks));
    }

    /**
     * Guard against rendering with an inconsistent world state during tab switches.
     * If mc.theWorld doesn't match the RenderGlobal's world, skip rendering to
     * prevent sky/fog bleeding between tabs.
     */
    @Inject(method = "renderWorldPass", at = @At("HEAD"), cancellable = true)
    private void fdp$guardRenderWorldConsistency(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (mc.theWorld != null && mc.renderGlobal != null) {
            // During tab switches, the RenderGlobal's world and mc.theWorld can
            // briefly be out of sync. Skip rendering in that case to prevent
            // sky/fog color bleeding between tabs.
            // Access theWorld field directly via reflection since we can't call
            // non-private static methods from other mixins.
            try {
                java.lang.reflect.Field theWorldField = mc.renderGlobal.getClass().getDeclaredField("field_72769_h");
                theWorldField.setAccessible(true);
                net.minecraft.client.multiplayer.WorldClient renderGlobalWorld =
                    (net.minecraft.client.multiplayer.WorldClient) theWorldField.get(mc.renderGlobal);
                if (renderGlobalWorld != mc.theWorld) {
                    ci.cancel();
                }
            } catch (Exception ignored) {
                // If reflection fails, don't block rendering
            }
        }
    }

    /**
     * Redirects the vanilla getEntitiesInAABBexcluding call in getMouseOver
     * to use a NPE-safe predicate. The vanilla lambda at
     * EntityRenderer.java:428 calls p_apply_1_.canBeCollidedWith() which
     * can NPE if a mod overrides the method and the entity's world has
     * been torn down mid-iteration (e.g. during background tab disconnect).
     *
     * Wrapping the predicate in a try-catch prevents the entire
     * updateCameraAndRender call from crashing the game.
     */
    @Redirect(
        method = "getMouseOver",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/WorldClient;getEntitiesInAABBexcluding(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;Lcom/google/common/base/Predicate;)Ljava/util/List;"
        )
    )
    private List<Entity> redirectGetEntitiesInAABBExcluding(net.minecraft.client.multiplayer.WorldClient world, Entity exclude, AxisAlignedBB box, com.google.common.base.Predicate<? super Entity> predicate) {
        try {
            return world.getEntitiesInAABBexcluding(exclude, box, entity -> {
                try {
                    return predicate.apply(entity);
                } catch (Throwable t) {
                    return false;
                }
            });
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    /**
     * Redirects the getEntityBoundingBox() call in getMouseOver (line 424)
     * to return a safe bounding box if the entity's state is corrupted.
     * This prevents NPEs when the player's worldObj has been torn down
     * mid-frame (e.g. during a background tab disconnect).
     */
    @Redirect(
        method = "getMouseOver",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;getEntityBoundingBox()Lnet/minecraft/util/AxisAlignedBB;"
        )
    )
    private AxisAlignedBB redirectGetEntityBoundingBox(Entity entity) {
        try {
            AxisAlignedBB bb = entity.getEntityBoundingBox();
            if (bb == null) {
                return new AxisAlignedBB(0, 0, 0, 0, 0, 0);
            }
            return bb;
        } catch (Throwable t) {
            return new AxisAlignedBB(0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * Guard against NPE in updateCameraAndRender when the render state is
     * corrupted during tab switches. This can happen when:
     * - The active tab is disconnected and renderViewEntity is null
     * - The world is null but thePlayer is set (partial state)
     * - The player is null but the world is set (partial state)
     * - The player controller is null (post-disconnect transition)
     *
     * On the main menu, all these fields are legitimately null and the
     * EntityRenderer handles this correctly. We only cancel when we detect
     * an inconsistent state that would cause an NPE.
     */
    @Inject(method = "updateCameraAndRender", at = @At("HEAD"), cancellable = true)
    private void fdp$guardNullRenderView(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        // Inconsistent state: thePlayer is set but world/renderViewEntity is null.
        // This happens during tab switches when the player object exists but
        // the world hasn't been loaded yet.
        if (mc.thePlayer != null && (mc.getRenderViewEntity() == null || mc.theWorld == null)) {
            ci.cancel();
            return;
        }
        // Inconsistent state: theWorld is set but thePlayer/renderViewEntity is null.
        // This shouldn't happen in normal gameplay — if there's a world, there must
        // be a player to render.
        if (mc.theWorld != null && (mc.thePlayer == null || mc.getRenderViewEntity() == null)) {
            ci.cancel();
            return;
        }
        // Inconsistent state: thePlayer and theWorld are set, but
        // playerController is null. The EntityRenderer accesses
        // playerController.getBlockReachDistance() / extendedReach() inside
        // getMouseOver. If the controller is null, those will NPE.
        if (mc.thePlayer != null && mc.theWorld != null && mc.playerController == null) {
            ci.cancel();
            return;
        }
        // When theWorld is null and thePlayer is null, the EntityRenderer
        // correctly handles this case — it skips world rendering and renders
        // the currentScreen (if any). This is the normal main menu / connecting
        // screen state. We should NOT cancel in this case because the screen
        // needs to be rendered.
        //
        // However, there's a problem: updateCameraAndRender also calls
        // mc.thePlayer.setAngles() at line 1083/1089 when inGameHasFocus is
        // true. If thePlayer is null, this NPEs. But this only happens when
        // inGameHasFocus is true, which means the mouse is grabbed. When we
        // display a screen, the mouse is ungrabbed and inGameHasFocus is false,
        // so the setAngles call is skipped. So this is safe as long as we have
        // a screen displayed.
        //
        // The only dangerous case is when thePlayer is null and we DON'T have
        // a screen (inGameHasFocus could be true from a previous state). Cancel
        // in that case.
        if (mc.thePlayer == null && mc.theWorld == null && mc.currentScreen == null) {
            ci.cancel();
        }
    }

    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
    private void injectHurtCameraEffect(CallbackInfo callbackInfo) {
        if (HurtCam.INSTANCE.handleEvents()) {
            callbackInfo.cancel();
        }
    }

    @Unique
    private float NightVisionBrightness(EntityLivingBase p_getNightVisionBrightness_1_, float p_getNightVisionBrightness_2_) {
        int i = p_getNightVisionBrightness_1_.getActivePotionEffect(Potion.nightVision).getDuration();
        return i > 200 ? 1.0F : 0.7F + MathHelper.sin(((float) i - p_getNightVisionBrightness_2_) * 3.1415927F * 0.2F) * 0.3F;
    }

    @ModifyConstant(method = "orientCamera", constant = @Constant(intValue = 8))
    private int injectCameraClip(int eight) {
        return eight;
    }

    @Inject(at = @At("HEAD"), method = "updateCameraAndRender", cancellable = true)
    private void injectCameraModifications(float p_updateCameraAndRender_1_, long p_updateCameraAndRender_2_, CallbackInfo ci) {
        if (mc.theWorld == null || mc.thePlayer == null || mc.getRenderViewEntity() == null) {
            return;
        }
        FreeCam.INSTANCE.useModifiedPosition();
    }

    @Inject(method = "orientCamera", at = @At(value = "HEAD"))
    private void injectFreeLook(float p_orientCamera_1_, CallbackInfo ci) {
        FreeLook.INSTANCE.useModifiedRotation();
    }

    @Inject(at = @At("TAIL"), method = "updateCameraAndRender")
    private void injectCameraRestorations(float p_updateCameraAndRender_1_, long p_updateCameraAndRender_2_, CallbackInfo ci) {
        FreeLook.INSTANCE.restoreOriginalRotation();
        FreeCam.INSTANCE.restoreOriginalPosition();
    }

    /**
     * @author CCBlueX
     */
    @Inject(method = "getMouseOver", at = @At("HEAD"), cancellable = true)
    private void getMouseOver(float p_getMouseOver_1_, CallbackInfo ci) {
        Entity entity = mc.getRenderViewEntity();
        if (entity != null && (entity.isDead || entity.worldObj != mc.theWorld)) {
            entity = mc.thePlayer;
        }

        if (entity == null || mc.theWorld == null || mc.thePlayer == null || mc.playerController == null) {
            mc.pointedEntity = null;
            mc.objectMouseOver = null;
            ci.cancel();
            return;
        }

        if (entity != null && mc.theWorld != null) {
            final Entity raycastEntity = entity;
            mc.mcProfiler.startSection("pick");
            mc.pointedEntity = null;
            double d0 = mc.playerController.getBlockReachDistance();
            Vec3 vec3 = raycastEntity.getPositionEyes(p_getMouseOver_1_);
            Rotation rotation = new Rotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            Vec3 vec31 = RotationUtils.INSTANCE.getVectorForRotation(RotationUtils.INSTANCE.getCurrentRotation() != null && OverrideRaycast.INSTANCE.shouldOverride() ? RotationUtils.INSTANCE.getCurrentRotation() : rotation);
            double p_rayTrace_1_ = d0;
            Vec3 vec32 = vec3.addVector(vec31.xCoord * p_rayTrace_1_, vec31.yCoord * p_rayTrace_1_, vec31.zCoord * p_rayTrace_1_);
            mc.objectMouseOver = raycastEntity.worldObj.rayTraceBlocks(vec3, vec32, false, false, true);
            double d1 = d0;
            boolean flag = false;
            if (mc.playerController.extendedReach()) {
                // d0 = 6;
                d1 = 6;
            } else if (d0 > 3) {
                flag = true;
            }

            if (mc.objectMouseOver != null) {
                d1 = mc.objectMouseOver.hitVec.distanceTo(vec3);
            }

            pointedEntity = null;
            Vec3 vec33 = null;
            // Defensive predicate: catch any NPE thrown by entity-specific
            // canBeCollidedWith() overrides. Some mods override this method
            // and NPE when the entity's world has been torn down mid-iteration
            // (e.g. during background tab disconnects). The vanilla code at
            // EntityRenderer.java:428 NPEs in the same situation.
            List<Entity> list = mc.theWorld.getEntities(Entity.class, Predicates.and(EntitySelectors.NOT_SPECTATING, p_apply_1_ -> {
                try {
                    return p_apply_1_ != null && p_apply_1_.canBeCollidedWith() && p_apply_1_ != raycastEntity;
                } catch (Throwable t) {
                    return false;
                }
            }));
            double d2 = d1;

            for (Entity entity1 : list) {
                if (entity1 == null || entity1.isDead) continue;
                float f1 = entity1.getCollisionBorderSize();

                final ArrayList<AxisAlignedBB> boxes = new ArrayList<>();
                boxes.add(entity1.getEntityBoundingBox().expand(f1, f1, f1));

                Backtrack.INSTANCE.loopThroughBacktrackData(entity1, () -> {
                    boxes.add(entity1.getEntityBoundingBox().expand(f1, f1, f1));
                    return false;
                });

                ForwardTrack.INSTANCE.includeEntityTruePos(entity1, () -> {
                    boxes.add(entity1.getEntityBoundingBox().expand(f1, f1, f1));
                    return null;
                });

                for (final AxisAlignedBB axisalignedbb : boxes) {
                    MovingObjectPosition movingobjectposition = axisalignedbb.calculateIntercept(vec3, vec32);
                    if (axisalignedbb.isVecInside(vec3)) {
                        if (d2 >= 0) {
                            pointedEntity = entity1;
                            vec33 = movingobjectposition == null ? vec3 : movingobjectposition.hitVec;
                            d2 = 0;
                        }
                    } else if (movingobjectposition != null) {
                        double d3 = vec3.distanceTo(movingobjectposition.hitVec);
                        if (d3 < d2 || d2 == 0) {
                            if (entity1 == raycastEntity.ridingEntity && !raycastEntity.canRiderInteract()) {
                                if (d2 == 0) {
                                    pointedEntity = entity1;
                                    vec33 = movingobjectposition.hitVec;
                                }
                            } else {
                                pointedEntity = entity1;
                                vec33 = movingobjectposition.hitVec;
                                d2 = d3;
                            }
                        }
                    }
                }
            }

            if (pointedEntity != null && vec33 == null) {
                pointedEntity = null;
            }

            if (pointedEntity != null && vec33 != null && flag && vec3.distanceTo(vec33) > 3) {
                pointedEntity = null;
                mc.objectMouseOver = new MovingObjectPosition(MovingObjectPosition.MovingObjectType.MISS, vec33, null, new BlockPos(vec33));
            }

            if (pointedEntity != null && vec33 != null && (d2 < d1 || mc.objectMouseOver == null)) {
                mc.objectMouseOver = new MovingObjectPosition(pointedEntity, vec33);
                if (pointedEntity instanceof EntityLivingBase || pointedEntity instanceof EntityItemFrame) {
                    mc.pointedEntity = pointedEntity;
                }
            }

            mc.mcProfiler.endSection();
        }

        ci.cancel();
    }

    /**
     * @author opZywl
     * @reason Update Light Map
     */
    @Overwrite
    private void updateLightmap(float f2) {
        final Ambience ambience = Ambience.INSTANCE;
        if (this.lightmapUpdateNeeded) {
            this.mc.mcProfiler.startSection("lightTex");
            World world = this.mc.theWorld;
            if (world != null) {
                float f3 = world.getSunBrightness(1.0f);
                float f4 = f3 * 0.95f + 0.05f;
                for (int i2 = 0; i2 < 256; ++i2) {
                    float f5;
                    float f6;
                    float f7 = world.provider.getLightBrightnessTable()[i2 / 16] * f4;
                    float f8 = world.provider.getLightBrightnessTable()[i2 % 16] * (this.torchFlickerX * 0.1f + 1.5f);
                    if (world.getLastLightningBolt() > 0) {
                        f7 = world.provider.getLightBrightnessTable()[i2 / 16];
                    }
                    float f9 = f7 * (f3 * 0.65f + 0.35f);
                    float f10 = f7 * (f3 * 0.65f + 0.35f);
                    float f11 = f8 * ((f8 * 0.6f + 0.4f) * 0.6f + 0.4f);
                    float f12 = f8 * (f8 * f8 * 0.6f + 0.4f);
                    float f13 = f9 + f8;
                    float f14 = f10 + f11;
                    float f15 = f7 + f12;
                    f13 = f13 * 0.96f + 0.03f;
                    f14 = f14 * 0.96f + 0.03f;
                    f15 = f15 * 0.96f + 0.03f;
                    if (this.bossColorModifier > 0.0f) {
                        float f16 = this.bossColorModifierPrev + (this.bossColorModifier - this.bossColorModifierPrev) * f2;
                        f13 = f13 * (1.0f - f16) + f13 * 0.7f * f16;
                        f14 = f14 * (1.0f - f16) + f14 * 0.6f * f16;
                        f15 = f15 * (1.0f - f16) + f15 * 0.6f * f16;
                    }
                    if (world.provider.getDimensionId() == 1) {
                        f13 = 0.22f + f8 * 0.75f;
                        f14 = 0.28f + f11 * 0.75f;
                        f15 = 0.25f + f12 * 0.75f;
                    }
                    if (this.mc.thePlayer.isPotionActive(Potion.nightVision)) {
                        f6 = this.NightVisionBrightness(this.mc.thePlayer, f2);
                        f5 = 1.0f / f13;
                        if (f5 > 1.0f / f14) {
                            f5 = 1.0f / f14;
                        }
                        if (f5 > 1.0f / f15) {
                            f5 = 1.0f / f15;
                        }
                        f13 = f13 * (1.0f - f6) + f13 * f5 * f6;
                        f14 = f14 * (1.0f - f6) + f14 * f5 * f6;
                        f15 = f15 * (1.0f - f6) + f15 * f5 * f6;
                    }
                    if (f13 > 1.0f) {
                        f13 = 1.0f;
                    }
                    if (f14 > 1.0f) {
                        f14 = 1.0f;
                    }
                    if (f15 > 1.0f) {
                        f15 = 1.0f;
                    }
                    f6 = this.mc.gameSettings.gammaSetting;
                    f5 = 1.0f - f13;
                    float f17 = 1.0f - f14;
                    float f18 = 1.0f - f15;
                    f5 = 1.0f - f5 * f5 * f5 * f5;
                    f17 = 1.0f - f17 * f17 * f17 * f17;
                    f18 = 1.0f - f18 * f18 * f18 * f18;
                    f13 = f13 * (1.0f - f6) + f5 * f6;
                    f14 = f14 * (1.0f - f6) + f17 * f6;
                    f15 = f15 * (1.0f - f6) + f18 * f6;
                    f13 = f13 * 0.96f + 0.03f;
                    f14 = f14 * 0.96f + 0.03f;
                    f15 = f15 * 0.96f + 0.03f;
                    if (f13 > 1.0f) {
                        f13 = 1.0f;
                    }
                    if (f14 > 1.0f) {
                        f14 = 1.0f;
                    }
                    if (f15 > 1.0f) {
                        f15 = 1.0f;
                    }
                    if (f13 < 0.0f) {
                        f13 = 0.0f;
                    }
                    if (f14 < 0.0f) {
                        f14 = 0.0f;
                    }
                    if (f15 < 0.0f) {
                        f15 = 0.0f;
                    }
                    int n2 = (int) (f13 * 255.0f);
                    int n3 = (int) (f14 * 255.0f);
                    int n4 = (int) (f15 * 255.0f);
                    this.lightmapColors[i2] = ambience.getState() && ambience.getWorldColor()
                            ? new Color(ambience.getColor().getRGB()).getRGB()
                            : 0xFF000000 | n2 << 16 | n3 << 8 | n4;
                }
                this.lightmapTexture.updateDynamicTexture();
                this.lightmapUpdateNeeded = false;
                this.mc.mcProfiler.endSection();
            }
        }
    }

    /**
     * Properly implement the confusion option from AntiBlind module
     */
    @Redirect(method = "setupCameraTransform", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;isPotionActive(Lnet/minecraft/potion/Potion;)Z"))
    private boolean injectAntiBlindA(EntityPlayerSP instance, Potion potion) {
        AntiBlind module = AntiBlind.INSTANCE;

        return (!module.handleEvents() || !module.getConfusionEffect()) && instance.isPotionActive(potion);
    }

    @Redirect(method = {"setupFog", "updateFogColor"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;isPotionActive(Lnet/minecraft/potion/Potion;)Z"))
    private boolean injectAntiBlindB(EntityLivingBase instance, Potion potion) {
        if (instance != mc.thePlayer) {
            return instance.isPotionActive(potion);
        }

        AntiBlind module = AntiBlind.INSTANCE;

        return (!module.handleEvents() || !module.getConfusionEffect()) && instance.isPotionActive(potion);
    }
}
