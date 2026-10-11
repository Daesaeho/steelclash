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

/** Replaces the addon/PAL item transforms with the canonical standard-axis item transform. */
@Mixin(value = ItemInHandLayer.class, priority = 2000)
public abstract class PalMoreItemApplyMixin {
    @Inject(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            )
    )
    private void steelclash$applyCanonicalItemTransform(
            LivingEntity entity, ItemStack item, ItemDisplayContext context, HumanoidArm arm,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, CallbackInfo ci
    ) {
        PalMoreRotationCompat.finishItemFix(entity, arm, poseStack);
    }
}
