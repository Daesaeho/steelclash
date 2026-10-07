package com.steelclash.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Poses humanoid and illager mob models (zombies, skeletons, piglins, vindicators, the training dummy) from their
 * combat state, so their windups are readable. Mobs can't have their held item rotated, so the weapon arm is pitched
 * to put the blade itself on the arc.
 */
public final class MobCombatPoses {
    /** Share of the whole-body turn the legs take back, as for players (shoulders twist over the hips). */
    private static final float HIP_LAG = 0.5f;
    /** Whole-body rotation pivot height, blocks above the feet (as Player Animation Library uses for players). */
    private static final float BODY_PIVOT = 0.75f;

    private MobCombatPoses() {
    }

    public static void apply(LivingEntity entity, EntityModel<?> model, float partialTick) {
        if (entity instanceof Player) {
            return; // players use the Player Animation Library layer
        }
        Parts parts = parts(model);
        if (parts == null) {
            return;
        }
        CombatPose pose = CombatPose.of(entity, partialTick).orElse(null);
        if (pose == null) {
            return;
        }
        if (model instanceof IllagerModel<?> illager) {
            // A crossed-arms illager hides its real arms; the swing needs them (the server also keeps fighters
            // aggressive, which uncrosses them and shows the weapon).
            illager.arms.visible = false;
            illager.rightArm.visible = true;
            illager.leftArm.visible = true;
        }
        float w = (float) pose.weight();
        double[] arm = pose.mobWeaponArm();
        if (!pose.kick()) {
            set(parts.rightArm, w, arm, pose, "rightArm");
            if (pose.twoHanded()) {
                set(parts.leftArm, w, pose.gripArm(arm), pose, "leftArm");
            } else {
                add(parts.leftArm, pose, "leftArm");
            }
        } else {
            add(parts.rightArm, pose, "rightArm");
            add(parts.leftArm, pose, "leftArm");
        }
        add(parts.rightLeg, pose, "rightLeg");
        add(parts.leftLeg, pose, "leftLeg");
        // The whole model turns (applyBodyRotation); turning the legs part of the way back keeps the feet planted.
        // ModelPart yaw is in model space, where the body's turn is -y (see WeaponRig.bodyRotation).
        parts.rightLeg.yRot += HIP_LAG * pose.offset("body", 1);
        parts.leftLeg.yRot += HIP_LAG * pose.offset("body", 1);
        add(parts.head, pose, "head");
        if (parts.body != null) {
            add(parts.body, pose, "torso"); // the torso part only gets the clip's extra torso motion
        }
    }

    /**
     * Whole-body lean and twist from the clip, applied to the entity's pose stack right after vanilla's own body
     * rotation, exactly as Player Animation Library does for players (rotate Z, Y, X of the clip's {@code body} angles
     * about a point 0.75 blocks up). Turning just the torso part instead pulled it away from the legs, arms and head.
     */
    public static void applyBodyRotation(LivingEntity entity, PoseStack poseStack, float partialTick) {
        if (entity instanceof Player) {
            return;
        }
        CombatPose pose = CombatPose.of(entity, partialTick).orElse(null);
        if (pose == null) {
            return;
        }
        double[] body = pose.bodyDegrees();
        if (body[0] == 0 && body[1] == 0 && body[2] == 0) {
            return;
        }
        poseStack.translate(0, BODY_PIVOT, 0);
        poseStack.mulPose(new Quaternionf().rotationZYX((float) Math.toRadians(body[2]), (float) Math.toRadians(body[1]),
                (float) Math.toRadians(body[0])));
        poseStack.translate(0, -BODY_PIVOT, 0);
    }

    private record Parts(ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg, ModelPart head,
                         @Nullable ModelPart body) {
    }

    @Nullable
    private static Parts parts(EntityModel<?> model) {
        if (model instanceof HumanoidModel<?> humanoid) {
            return new Parts(humanoid.rightArm, humanoid.leftArm, humanoid.rightLeg, humanoid.leftLeg, humanoid.head, humanoid.body);
        }
        if (model instanceof IllagerModel<?> illager) {
            return new Parts(illager.rightArm, illager.leftArm, illager.rightLeg, illager.leftLeg, illager.getHead(), null);
        }
        return null;
    }

    private static void set(ModelPart part, float w, double[] target, CombatPose pose, String name) {
        part.xRot = Mth.lerp(w, part.xRot, (float) target[0]) + pose.offset(name, 0);
        part.yRot = Mth.lerp(w, part.yRot, (float) target[1]) + pose.offset(name, 1);
        part.zRot = Mth.lerp(w, part.zRot, (float) target[2]) + pose.offset(name, 2);
    }

    private static void add(ModelPart part, CombatPose pose, String name) {
        part.xRot += pose.offset(name, 0);
        part.yRot += pose.offset(name, 1);
        part.zRot += pose.offset(name, 2);
    }
}
