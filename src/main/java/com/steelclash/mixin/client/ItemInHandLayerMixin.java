package com.steelclash.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.steelclash.client.anim.MobCombatPoses;
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

/**
 * Turns a fighting mob's weapon in its hand, just before the item is drawn, so the blade lies on the swing's arc with the
 * arm held naturally (players get the same turn from Player Animation Library at this point). See docs/mixin-risk.md.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void steelclash$turnHeldItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                         PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        MobCombatPoses.turnHeldItem(entity, arm, poseStack);
    }
}
