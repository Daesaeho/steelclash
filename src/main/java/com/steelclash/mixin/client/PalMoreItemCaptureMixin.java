package com.steelclash.mixin.client;

import com.steelclash.compat.PalMoreRotationCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the item matrix before PAL More's correction and PAL's later item transform. */
@Mixin(value = ItemInHandLayer.class, priority = 100)
public abstract class PalMoreItemCaptureMixin {
    @Inject(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            )
    )
    private void steelclash$captureBeforeItemTransforms(
            LivingEntity entity, ItemStack item, ItemDisplayContext context, HumanoidArm arm,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, CallbackInfo ci
    ) {
        PalMoreRotationCompat.beginItemFix(entity, arm, poseStack);
    }
}
