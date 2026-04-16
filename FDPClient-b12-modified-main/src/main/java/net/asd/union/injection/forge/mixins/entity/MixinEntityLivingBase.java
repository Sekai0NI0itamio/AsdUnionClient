/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.injection.forge.mixins.entity;

import net.asd.union.event.EventManager;
import net.asd.union.event.EventState;
import net.asd.union.event.JumpEvent;
import net.asd.union.event.LivingUpdateEvent;
import net.asd.union.features.module.modules.movement.NoJumpDelay;
import net.asd.union.features.module.modules.movement.Sprint;
import net.asd.union.features.module.modules.client.Animations;
import net.asd.union.features.module.modules.client.Rotations;
import net.asd.union.features.module.modules.combat.ProjectileVelocity;
import net.asd.union.features.module.modules.combat.RodVelocity;
import net.asd.union.utils.rotation.RotationSettings;
import net.asd.union.features.module.modules.player.scaffolds.Scaffold;
import net.asd.union.utils.movement.MovementUtils;
import net.asd.union.utils.rotation.Rotation;
import net.asd.union.utils.rotation.RotationUtils;
import net.asd.union.utils.extensions.MathExtensionsKt;
import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.asd.union.utils.client.MinecraftInstance.mc;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase extends MixinEntity {

    @Shadow
    public float rotationYawHead;
    @Shadow
    public boolean isJumping;
    @Shadow
    public int jumpTicks;

    @Shadow
    protected abstract float getJumpUpwardsMotion();

    @Shadow
    public abstract PotionEffect getActivePotionEffect(Potion potionIn);

    @Shadow
    public abstract boolean isPotionActive(Potion potionIn);

    @Shadow
    public void onLivingUpdate() {
    }

    @Shadow
    protected abstract void updateFallState(double y, boolean onGroundIn, Block blockIn, BlockPos pos);

    @Shadow
    public abstract float getHealth();

    @Shadow
    public abstract ItemStack getHeldItem();

    @Shadow
    protected abstract void updateAITick();

    /**
     * @author CCBlueX
     */
    @Overwrite
    protected void jump() {
        final JumpEvent prejumpEvent = new JumpEvent(getJumpUpwardsMotion(), EventState.PRE);
        EventManager.INSTANCE.call(prejumpEvent);
        if (prejumpEvent.isCancelled()) return;

        motionY = prejumpEvent.getMotion();

        if (isPotionActive(Potion.jump))
            motionY += (float) (getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F;

        if (isSprinting()) {
            float fixedYaw = this.rotationYaw;

            final RotationUtils rotationUtils = RotationUtils.INSTANCE;
            final Rotation currentRotation = rotationUtils.getCurrentRotation();
            final RotationSettings rotationData = rotationUtils.getActiveSettings();
            if (currentRotation != null && rotationData != null && rotationData.getStrafe()) {
                fixedYaw = currentRotation.getYaw();
            }

            final Sprint sprint = Sprint.INSTANCE;
            if (sprint.handleEvents() && sprint.getMode().equals("Vanilla") && sprint.getAllDirections() && sprint.getJumpDirections()) {
                fixedYaw += MathExtensionsKt.toDegreesF(MovementUtils.INSTANCE.getDirection()) - this.rotationYaw;
            }

            final float f = fixedYaw * 0.017453292F;
            motionX -= MathHelper.sin(f) * 0.2F;
            motionZ += MathHelper.cos(f) * 0.2F;
        }

        isAirBorne = true;

        final JumpEvent postjumpEvent = new JumpEvent((float) motionY, EventState.POST);
        EventManager.INSTANCE.call(postjumpEvent);
    }

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void headLiving(CallbackInfo callbackInfo) {
        if (NoJumpDelay.INSTANCE.handleEvents()) jumpTicks = 0;
    }

    @Inject(method = "getLook", at = @At("HEAD"), cancellable = true)
    private void getLook(CallbackInfoReturnable<Vec3> callbackInfoReturnable) {
        //noinspection ConstantConditions
        if (((EntityLivingBase) (Object) this) instanceof EntityPlayerSP)
            callbackInfoReturnable.setReturnValue(getVectorForRotation(rotationPitch, rotationYaw));
    }

    /**
     * Inject head yaw rotation modification
     */
    @Inject(method = "onLivingUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;updateEntityActionState()V", shift = At.Shift.AFTER))
    private void hookHeadRotations(CallbackInfo ci) {
        Rotation rotation = Rotations.INSTANCE.getRotation();

        //noinspection ConstantValue
        this.rotationYawHead = ((EntityLivingBase) (Object) this) instanceof EntityPlayerSP && Rotations.INSTANCE.shouldUseRealisticMode() && rotation != null ? rotation.getYaw() : this.rotationYawHead;
    }

    /**
     * Inject body rotation modification
     */
    @Redirect(method = "onUpdate", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;rotationYaw:F", ordinal = 0))
    private float hookBodyRotationsA(EntityLivingBase instance) {
        Rotation rotation = Rotations.INSTANCE.getRotation();

        return instance instanceof EntityPlayerSP && Rotations.INSTANCE.shouldUseRealisticMode() && rotation != null ? rotation.getYaw() : instance.rotationYaw;
    }


    /**
     * Inject body rotation modification
     */
    @Redirect(method = "updateDistance", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;rotationYaw:F"))
    private float hookBodyRotationsB(EntityLivingBase instance) {
        Rotation rotation = Rotations.INSTANCE.getRotation();

        return instance instanceof EntityPlayerSP && Rotations.INSTANCE.shouldUseRealisticMode() && rotation != null ? rotation.getYaw() : instance.rotationYaw;
    }

    /**
     * @author SuperSkidder
     * @reason Animations swing speed
     */
    @ModifyConstant(method = "getArmSwingAnimationEnd", constant = @Constant(intValue = 6))
    private int injectAnimationsModule(int constant) {
        Animations module = Animations.INSTANCE;

        return module.handleEvents() ? (2 + (20 - module.getSwingSpeed())) : constant;
    }

    /**
     * Injects event for entity updates
     * creates a LivingUpdateEvent for the current entity
     */
    @Inject(method = "onEntityUpdate", at = @At("HEAD"))
    public void onEntityUpdate(CallbackInfo info) {
        LivingUpdateEvent livingUpdateEvent = new LivingUpdateEvent((EntityLivingBase) (Object) this);
        EventManager.INSTANCE.call(livingUpdateEvent);
    }

    /**
     * Detect projectile hits for ProjectileVelocity and RodVelocity modules
     */
    @Inject(method = "attackEntityFrom", at = @At("HEAD"))
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity attacker = source.getEntity();
        Entity directSource = source.getSourceOfDamage();

        if ((Object) this != mc.thePlayer) {
            return;
        }

        boolean projectileArmed = ProjectileVelocity.INSTANCE.onMinecraftDamageSource(source, directSource, attacker);
        boolean rodArmed = RodVelocity.INSTANCE.onMinecraftDamageSource(source, directSource, attacker);
    }

    @Inject(method = "knockBack", at = @At("HEAD"), cancellable = true)
    private void fdp$directKnockBack(Entity attacker, float strength, double xRatio, double zRatio, CallbackInfo ci) {
        if ((Object) this != mc.thePlayer) {
            return;
        }

        boolean projectileMatch = attacker instanceof EntityArrow || attacker instanceof EntitySnowball || attacker instanceof EntityEgg || attacker instanceof EntityPotion || attacker instanceof EntityEnderPearl;
        boolean rodMatch = attacker instanceof EntityFishHook;
        boolean projectileArmed = ProjectileVelocity.INSTANCE.isKnockbackBlockArmed();
        boolean rodArmed = RodVelocity.INSTANCE.isKnockbackBlockArmed();

        if (projectileMatch && projectileArmed && ProjectileVelocity.INSTANCE.handleEvents()) {
            ci.cancel();
            return;
        }

        if (rodMatch && rodArmed && RodVelocity.INSTANCE.handleEvents()) {
            ci.cancel();
        }
    }
}
