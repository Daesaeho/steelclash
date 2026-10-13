package com.steelclash.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.steelclash.client.dev.BladeMeshProbe;
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

/** Development-only read of the arm matrix, before hand rotations and the final item socket. */
@Mixin(ItemInHandLayer.class)
public abstract class BladeMeshArmMixin {
    @Inject(method="renderArmWithItem",at=@At("HEAD"))
    private void steelclash$captureArm(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                      PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        BladeMeshProbe.arm(entity,stack,arm==HumanoidArm.LEFT,pose);
    }
}
