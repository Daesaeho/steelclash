package com.steelclash.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.steelclash.Config;
import com.steelclash.client.dev.PoseSheet;
import com.steelclash.core.WeaponRig;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;
import com.steelclash.core.RotationBlend;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Poses humanoid and illager mob models (zombies, skeletons, piglins, vindicators, the training dummy) from their
 * combat state, so their windups are readable. The weapon arm and the weapon's turn in the hand come from the same
 * {@link WeaponRig} as the player's, so the blade lies on the arc the server traces with the hand at chest height; the
 * item layer applies the turn ({@link #turnHeldItem}). A mob whose renderer doesn't draw its weapon through vanilla's
 * item layer (some modded ones) can't have it turned: its arm is pitched instead to put the blade on the arc, which
 * leaves the hand low.
 */
public final class MobCombatPoses {
    /** Share of the whole-body turn the legs take back, as for players (shoulders twist over the hips). */
    private static final float HIP_LAG = 0.5f;
    /** Whole-body rotation pivot height, blocks above the feet (as Player Animation Library uses for players). */
    private static final float BODY_PIVOT = 0.75f;

    /**
     * The pose {@link #applyBodyRotation} worked out, handed on to {@link #apply} in the same render call (vanilla
     * turns the body before it poses the model), so each frame computes a mob's pose once. Render thread only.
     */
    @Nullable
    private static LivingEntity posedEntity;
    private static float posedPartialTick;
    @Nullable
    private static CombatPose posed;

    /** This frame's turn of each posed mob's weapon in its hand, for {@link #turnHeldItem}. Render thread only. */
    private static final Map<LivingEntity, HeldItemTurn> ITEM_TURNS = new WeakHashMap<>();
    /** Mob types whose held items vanilla's item layer draws, so their weapons can be turned in the hand. */
    private static final Set<EntityType<?>> TURNABLE = new HashSet<>();

    private record HeldItemTurn(HumanoidArm arm, double[] radians) {
    }

    private MobCombatPoses() {
    }

    public static void apply(LivingEntity entity, EntityModel<?> model, float partialTick) {
        if (entity instanceof Player) {
            return; // players use the Player Animation Library layer
        }
        ITEM_TURNS.remove(entity);
        Parts parts = parts(model);
        if (parts == null) {
            return;
        }
        CombatPose pose = takePose(entity, partialTick);
        PoseSheet.recordRenderedPose(entity, pose);
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
        if (!pose.kick()) {
            double[] arm;
            if (TURNABLE.contains(entity.getType())) {
                WeaponRig rig = pose.rig(Config.Client.WEAPON_GRIP_PITCH.get(), Config.Client.BLADE_TWIST.get(),
                        WeaponRig.TwistAxis.valueOf(Config.Client.BLADE_TWIST_AXIS.get().name()));
                arm = rig.arm();
                ITEM_TURNS.put(entity, new HeldItemTurn(pose.leftHanded() ? HumanoidArm.LEFT : HumanoidArm.RIGHT,
                        RotationBlend.blend(0, 0, 0, rig.item(), w)));
            } else {
                arm = pose.mobWeaponArm();
            }
            ModelPart mainArm = pose.leftHanded() ? parts.leftArm : parts.rightArm;
            ModelPart offArm = pose.leftHanded() ? parts.rightArm : parts.leftArm;
            String mainName = pose.leftHanded() ? "leftArm" : "rightArm";
            String offName = pose.leftHanded() ? "rightArm" : "leftArm";
            set(mainArm, w, arm, pose, mainName);
            if (pose.twoHanded()) {
                set(offArm, w, pose.gripArm(arm), pose, ""); // authored free-arm offsets must not pull a solved grip away
            } else {
                add(offArm, pose, offName);
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
        CombatPose pose = CombatPresentation.get(entity, partialTick).orElse(null);
        posedEntity = entity;
        posedPartialTick = partialTick;
        posed = pose;
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

    /**
     * Called by the item layer just before it draws an item in a mob's hand: turns the weapon in the hand as this frame's
     * pose solved it (as Player Animation Library does for players: Z, then Y, then X of the hand-frame angles).
     */
    public static void turnHeldItem(LivingEntity entity, HumanoidArm arm, PoseStack poseStack) {
        if (entity instanceof Player) {
            return;
        }
        TURNABLE.add(entity.getType());
        HeldItemTurn turn = ITEM_TURNS.get(entity);
        if (turn == null || turn.arm() != arm) {
            return;
        }
        poseStack.mulPose(Axis.ZP.rotation((float) turn.radians()[2]));
        poseStack.mulPose(Axis.YP.rotation((float) turn.radians()[1]));
        poseStack.mulPose(Axis.XP.rotation((float) turn.radians()[0]));
    }

    /** The pose {@link #applyBodyRotation} just computed for this entity and frame, or a fresh one. */
    @Nullable
    private static CombatPose takePose(LivingEntity entity, float partialTick) {
        boolean handedOn = posedEntity == entity && posedPartialTick == partialTick;
        CombatPose pose = handedOn ? posed : CombatPresentation.get(entity, partialTick).orElse(null);
        posedEntity = null;
        posed = null;
        return pose;
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
        double[] rotation = RotationBlend.blend(part.xRot, part.yRot, part.zRot, target, w);
        part.xRot = (float) rotation[0] + pose.offset(name, 0);
        part.yRot = (float) rotation[1] + pose.offset(name, 1);
        part.zRot = (float) rotation[2] + pose.offset(name, 2);
    }

    private static void add(ModelPart part, CombatPose pose, String name) {
        part.xRot += pose.offset(name, 0);
        part.yRot += pose.offset(name, 1);
        part.zRot += pose.offset(name, 2);
    }
}
