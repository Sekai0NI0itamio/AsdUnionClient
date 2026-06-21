/*
 * Fix for NoSuchMethodError: javax.vecmath.Matrix4f.<init>(Lorg/lwjgl/util/vector/Matrix4f;)V
 *
 * Forge 1.8.9's ModelRotation.getMatrix() calls ForgeHooksClient.getMatrix(this) which
 * tries to construct javax.vecmath.Matrix4f with an LWJGL Matrix4f argument. The
 * vecmath-1.5.2.jar doesn't have this constructor, causing a crash on some platforms.
 *
 * This mixin intercepts getMatrix() to use the safe TRSRTransformation.toVecmath()
 * conversion path that uses the 16-float constructor instead.
 */
package net.asd.union.injection.forge.mixins.render;

import net.minecraft.client.resources.model.ModelRotation;
import net.minecraftforge.client.model.TRSRTransformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelRotation.class)
public class MixinModelRotation {

    /**
     * Intercept getMatrix() and provide a safe implementation that avoids the
     * missing javax.vecmath.Matrix4f(Lorg/lwjgl/util/vector/Matrix4f) constructor.
     */
    @Inject(method = "getMatrix", at = @At("HEAD"), cancellable = true, remap = false)
    private void fdp$fixVecmathConstructor(CallbackInfoReturnable<javax.vecmath.Matrix4f> cir) {
        ModelRotation self = (ModelRotation) (Object) this;
        javax.vecmath.Matrix4f ret = TRSRTransformation.toVecmath(self.getMatrix4d());
        javax.vecmath.Matrix4f tmp = new javax.vecmath.Matrix4f();
        tmp.setIdentity();
        tmp.m03 = tmp.m13 = tmp.m23 = 0.5f;
        ret.mul(tmp, ret);
        tmp.invert();
        ret.mul(tmp);
        cir.setReturnValue(ret);
    }
}
