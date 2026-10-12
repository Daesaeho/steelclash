package com.steelclash.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.steelclash.client.dev.BladeMeshProbe;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Development-only scope includes the PAL model hand path in first and third person. */
@Mixin(ItemInHandRenderer.class)
public abstract class BladeMeshContextMixin {
    @WrapMethod(method="renderItem")
    private void steelclash$captureDrawScope(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean left,
                                            PoseStack pose, MultiBufferSource buffers, int light, Operation<Void> original) {
        var scope=BladeMeshProbe.begin(entity,stack,context,left,pose);
        try {original.call(entity,stack,context,left,pose,buffers,light);}
        finally {BladeMeshProbe.end(scope);}
    }
}
