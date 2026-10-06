package com.steelclash.client.anim;

import com.steelclash.Config;
import com.steelclash.client.SwingPose;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.AnimationSet;
import com.steelclash.core.ArcPath;
import com.steelclash.core.ArmAim;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.core.PoseClip;
import com.steelclash.profile.WeaponProfile;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * One frame of a fighter's combat pose, shared by the player animation layer (Player Animation Library) and the mob model
 * hook. The weapon arm is solved from the same arc the server traces; everything else comes from the archetype's
 * pose clips.
 *
 * @param weight      blend weight over the vanilla pose (eases in during windup, out during recovery)
 * @param aimYaw      weapon direction yaw relative to the body, degrees
 * @param aimPitch    weapon direction pitch, degrees (positive = down)
 * @param offsets     additive part offsets from the clip, degrees ({@code body}, {@code head}, {@code leftArm}, ...)
 * @param twoHanded   the off hand grips the weapon
 * @param kick        the legs, not the weapon arm, do the attacking
 * @param bladeTwist  degrees to roll the weapon around its length so the edge leads the cut (see {@link ArcPath#edgeAngle})
 */
public record CombatPose(Phase phase, double weight, double aimYaw, double aimPitch, Map<String, double[]> offsets,
                         boolean twoHanded, boolean kick, double bladeTwist) {
    /** Degrees the off arm is turned toward the weapon hand for a two-handed grip. */
    private static final double TWO_HAND_CONVERGE = 28;

    public static Optional<CombatPose> of(LivingEntity entity, float partialTick) {
        if (!entity.hasData(ModAttachments.COMBAT)) {
            return Optional.empty();
        }
        CombatData data = entity.getData(ModAttachments.COMBAT);
        Optional<SwingPose> swing = SwingPose.of(entity, data, partialTick);
        if (swing.isEmpty()) {
            return Optional.empty();
        }
        SwingPose pose = swing.get();
        String archetype = Combat.currentProfile(entity, data).map(WeaponProfile::archetype).orElse("default");
        AnimationSet animation = AnimationLibrary.INSTANCE.get(archetype);

        double progress = data.machine.phaseProgress(partialTick);
        String key = AnimationSet.clipKey(pose.phase(), pose.type());
        Map<String, double[]> offsets;
        if (data.machine.isHeavy() && pose.phase() == Phase.WINDUP) {
            // Heavies have their own windup clip; older animation sets just exaggerate the light one.
            PoseClip heavy = animation.clip(pose.type().serializedName() + ".heavy_windup");
            offsets = heavy.isEmpty()
                    ? PoseClip.scale(animation.clip(key).sample(progress), animation.heavyWindupScale())
                    : heavy.sample(progress);
        } else {
            offsets = animation.clip(key).sample(progress);
        }
        if (data.machine.isMirrored() && pose.phase().isAttack()) {
            offsets = mirror(offsets);
        }
        boolean twoHanded = animation.twoHanded()
                || (Config.Client.TWO_HANDED_SWORDS.get() && "sword".equals(archetype) && entity.getOffhandItem().isEmpty());

        float headYaw = Mth.wrapDegrees(viewYaw(entity, partialTick) - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot));
        double aimYaw = headYaw + pose.yaw();
        double aimPitch = Mth.clamp(entity.getViewXRot(partialTick) + pose.pitch(), -90, 90);
        return Optional.of(new CombatPose(pose.phase(), pose.weight(), aimYaw, aimPitch, offsets,
                twoHanded, pose.type() == AttackType.KICK && pose.phase().isAttack(), bladeTwist(data, pose)));
    }

    /** Edge into the cut for the whole attack: set during the windup, follows the arc, held through recovery. */
    private static double bladeTwist(CombatData data, SwingPose pose) {
        if (!pose.phase().isAttack() || pose.type() == AttackType.KICK || pose.type() == AttackType.THROW) {
            return 0;
        }
        double t = switch (pose.phase()) {
            case WINDUP -> 0;
            case RELEASE -> pose.releaseProgress();
            default -> 1;
        };
        return Combat.currentPath(data, pose.spec()).edgeAngle(t);
    }

    private static float viewYaw(LivingEntity entity, float partialTick) {
        return entity instanceof net.minecraft.world.entity.player.Player
                ? entity.getViewYRot(partialTick)
                : Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
    }

    /** A swing from the other side: twist and lean the other way (yaw and roll flip, pitch stays). */
    private static Map<String, double[]> mirror(Map<String, double[]> offsets) {
        Map<String, double[]> out = new java.util.HashMap<>();
        offsets.forEach((part, v) -> out.put(part, switch (part) {
            case "body", "head", "torso" -> new double[]{v[0], -v[1], -v[2]};
            default -> v;
        }));
        return out;
    }

    /** Additive offset for a part, radians, already weighted. */
    public float offset(String part, int axis) {
        double[] v = offsets.get(part);
        return v == null ? 0f : (float) Math.toRadians(v[axis] * weight);
    }

    /** Weapon arm rotation (radians) with the arm itself along the arc; the item is rotated in the hand to match. */
    public double[] weaponArmForRotatedItem() {
        return ArmAim.aimArm(aimYaw, aimPitch);
    }

    /** Weapon arm rotation (radians) that puts an un-rotated held blade on the arc (mobs). */
    public double[] weaponArmForFixedItem() {
        return ArmAim.aimArmForBlade(aimYaw, aimPitch);
    }

    /** Off arm reaching across to the weapon hand. */
    public double[] gripArm(double[] weaponArm) {
        // Positive yaw turns an arm toward the body's right, where the weapon hand is.
        return new double[]{weaponArm[0], weaponArm[1] + Math.toRadians(TWO_HAND_CONVERGE), weaponArm[2]};
    }
}
