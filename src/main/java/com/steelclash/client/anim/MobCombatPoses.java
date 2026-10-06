package com.steelclash.client.anim;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Poses humanoid and illager mob models (zombies, skeletons, piglins, vindicators, the training dummy) from their
 * combat state, so their windups are readable. Mobs can't have their held item rotated, so the weapon arm is pitched
 * to put the blade itself on the arc.
 */
public final class MobCombatPoses {
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
        float w = (float) pose.weight();
        double[] arm = pose.weaponArmForFixedItem();
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
        add(parts.head, pose, "head");
        if (parts.body != null) {
            add(parts.body, pose, "body");
        }
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
