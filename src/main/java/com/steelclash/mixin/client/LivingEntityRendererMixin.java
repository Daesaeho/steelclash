package com.steelclash.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.steelclash.client.anim.MobCombatPoses;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies Steel Clash combat poses to mob models right after their own {@code setupAnim}, so zombie-arm and illager
 * poses can't overwrite them. Players are animated through Player Animation Library instead. See docs/mixin-risk.md.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow
    protected M model;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER))
    private void steelclash$applyCombatPose(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                                            MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        MobCombatPoses.apply(entity, model, partialTick);
    }
}
