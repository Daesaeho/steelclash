package com.steelclash.client;

import com.steelclash.SteelClash;
import com.steelclash.entity.Soldier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/** Humanoid soldier with armour; archers draw their bow like skeletons. Skins in textures/entity/soldier/. */
public class SoldierRenderer extends HumanoidMobRenderer<Soldier, SoldierRenderer.Model> {
    private final ResourceLocation texture;

    public SoldierRenderer(EntityRendererProvider.Context context, String skin) {
        super(context, new Model(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.texture = SteelClash.id("textures/entity/soldier/" + skin + ".png");
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(Soldier entity) {
        return texture;
    }

    /** Raises the bow while an archer is aiming (as vanilla skeletons do). */
    public static class Model extends HumanoidModel<Soldier> {
        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void prepareMobModel(Soldier entity, float limbSwing, float limbSwingAmount, float partialTick) {
            rightArmPose = ArmPose.EMPTY;
            leftArmPose = ArmPose.EMPTY;
            if (entity.isAggressive() && entity.getItemInHand(InteractionHand.MAIN_HAND).is(Items.BOW)) {
                rightArmPose = ArmPose.BOW_AND_ARROW;
            }
            super.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        }
    }
}
