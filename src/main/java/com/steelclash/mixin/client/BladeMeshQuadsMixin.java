package com.steelclash.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.steelclash.client.dev.BladeMeshProbe;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Reads the actual baked positions after display transforms and centering, before buffer submission. */
@Mixin(value=ItemRenderer.class, priority=2000)
public abstract class BladeMeshQuadsMixin {
    @WrapOperation(method="renderModelLists",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/ItemRenderer;renderQuadList(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Ljava/util/List;Lnet/minecraft/world/item/ItemStack;II)V"))
    private void steelclash$captureDrawQuads(ItemRenderer renderer, PoseStack pose, VertexConsumer buffer, List<BakedQuad> quads,
                                            ItemStack stack, int light, int overlay, Operation<Void> original) {
        BladeMeshProbe.quads(pose,quads);
        original.call(renderer,pose,buffer,quads,stack,light,overlay);
    }
}
