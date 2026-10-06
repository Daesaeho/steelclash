package com.steelclash.client;

import com.steelclash.entity.TrainingDummy;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Placeholder look: a Steve-shaped humanoid that renders its held weapon and shield. */
public class TrainingDummyRenderer extends HumanoidMobRenderer<TrainingDummy, HumanoidModel<TrainingDummy>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public TrainingDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(TrainingDummy entity) {
        return TEXTURE;
    }
}
