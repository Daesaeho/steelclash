package com.steelclash.client.anim;

import com.steelclash.Config;
import com.steelclash.client.SwingPose;
import com.steelclash.client.dev.PoseSheet;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.AnimationSet;
import com.steelclash.core.ArcPath;
import com.steelclash.core.ArmAim;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.core.Mat3;
import com.steelclash.core.PoseClip;
import com.steelclash.core.Vec;
import com.steelclash.core.WeaponRig;
import com.steelclash.profile.WeaponProfile;
import com.steelclash.profile.Kicks;
import com.steelclash.profile.WeaponProfiles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;

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
 * @param gripGap     pixels between the hands on a two-handed grip
 */
public record CombatPose(Phase phase, AttackType type, double weight, double aimYaw, double aimPitch, Map<String, double[]> offsets,
                         boolean twoHanded, boolean kick, double bladeTwist, double gripGap, boolean leftHanded,
                         boolean bash, double relativeYaw, double extension, AnimationSet.FirstPerson firstPerson, SwingPose swing) {
    /** How far the weapon hand is drawn in from the blade's line toward the chest (0 = arm and blade in one line). */
    private static final double WRIST_RELAX = 0.45;
    private static final double[] NO_OFFSET = {0, 0, 0};

    /** First-person ready stance: weapon held up at the weapon side, blade rising, degrees from the view. */
    private static final double READY_YAW = 65;
    private static final double READY_PITCH = -45;

    /** The animation layer's activation check needs availability, not an interpolated pose and sampled clips. */
    public static boolean isAvailable(LivingEntity entity) {
        if (!entity.hasData(ModAttachments.COMBAT)) {
            return false;
        }
        CombatData data = entity.getData(ModAttachments.COMBAT);
        return data.machine.phase() != Phase.IDLE && Combat.currentSpec(entity, data).isPresent();
    }

    /**
     * First-person ready stance: the held weapon up at the weapon side, blade rising, following the view. Attacks blend out
     * of it and back into it, so first person never cuts between vanilla's hand and the animated arms. It has no attack, so
     * {@link #type()} and {@link #swing()} are null.
     */
    public static Optional<CombatPose> ready(LivingEntity entity, float partialTick) {
        return WeaponProfiles.resolve(entity.getMainHandItem(), entity.level().registryAccess()).map(resolved -> {
            String archetype = resolved.profile().archetype();
            AnimationSet animation = AnimationLibrary.INSTANCE.get(archetype);
            boolean twoHanded = entity.getOffhandItem().isEmpty() && (animation.twoHanded()
                    || (Config.Client.TWO_HANDED_SWORDS.get() && ("sword".equals(archetype) || "steelclash:sword".equals(archetype))));
            boolean leftHanded = entity.getMainArm() == HumanoidArm.LEFT;
            float headYaw = Mth.wrapDegrees(viewYaw(entity, partialTick) - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot));
            double[] tuning = PoseSheet.readyOverride();
            double readyYaw = (tuning != null ? tuning[0] : READY_YAW) * (leftHanded ? -1 : 1);
            double readyPitch = tuning != null ? tuning[1] : READY_PITCH;
            double aimPitch = Mth.clamp(entity.getViewXRot(partialTick) + readyPitch, -90, 90);
            return new CombatPose(Phase.IDLE, null, 1, headYaw + readyYaw, aimPitch, Map.of(), twoHanded, false, 0,
                    animation.gripGap(), leftHanded, false, readyYaw, 1, animation.firstPerson(), null);
        });
    }

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

        double progress = pose.progress();
        String key = AnimationSet.clipKey(pose.phase(), pose.type());
        boolean mirrored = data.machine.isMirrored() && pose.phase().isAttack();
        boolean bash = pose.type() == AttackType.KICK && Kicks.canBash(entity);
        PoseClip action = actionClip(animation, key, pose, data, entity, bash);
        Map<String, double[]> offsets;
        if (data.machine.isHeavy() && pose.phase() == Phase.WINDUP) {
            // Heavies have their own windup clip; older animation sets just exaggerate the light one.
            PoseClip heavy = animation.clip(AnimationSet.heavyWindupKey(pose.type()));
            double settle = Math.max(0, Math.min(1, (progress - 0.65) / 0.35));
            settle = settle * settle * (3 - 2 * settle);
            offsets = heavy.isEmpty()
                    ? action.sample(progress, 1 + (animation.heavyWindupScale() - 1) * (1 - settle), mirrored)
                    : heavy.sample(progress, 1, mirrored);
        } else if (data.machine.isThwacked() && pose.phase() == Phase.RECOVERY) {
            // The blade stopped in a body: hold the body where the release was at contact while the pose eases out.
            offsets = actionClip(animation, AnimationSet.clipKey(Phase.RELEASE, pose.type()), pose, data, entity, bash)
                    .sample(data.machine.recoverFrom(), 1, mirrored);
        } else {
            // Guard body preparation follows the same three-tick raise as the weapon, regardless of hold duration.
            double clipProgress = pose.phase() == Phase.PARRY ? 0.25 * Math.min(1, pose.elapsedTicks() / 3) : progress;
            offsets = action.sample(clipProgress, 1, mirrored);
        }
        boolean twoHanded = entity.getOffhandItem().isEmpty() && (animation.twoHanded()
                || (Config.Client.TWO_HANDED_SWORDS.get() && ("sword".equals(archetype) || "steelclash:sword".equals(archetype))))
                && pose.type() != AttackType.KICK && (pose.type() != AttackType.THROW || pose.phase() == Phase.WINDUP);

        float headYaw = Mth.wrapDegrees(viewYaw(entity, partialTick) - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot));
        double aimYaw = headYaw + pose.yaw();
        double aimPitch = Mth.clamp(entity.getViewXRot(partialTick) + pose.pitch(), ArmAim.MIN_POSE_PITCH, 90);
        return Optional.of(new CombatPose(pose.phase(), pose.type(), pose.weight(), aimYaw, aimPitch, offsets,
                twoHanded, pose.type() == AttackType.KICK && pose.phase().isAttack(), bladeTwist(pose), animation.gripGap(),
                entity.getMainArm() == HumanoidArm.LEFT, bash, pose.yaw(), pose.extension(), animation.firstPerson(), pose));
    }

    private static PoseClip actionClip(AnimationSet animation, String key, SwingPose pose, CombatData data,
                                      LivingEntity entity, boolean bash) {
        String selected = key;
        if (pose.phase().isAttack()) {
            String phaseName = key.substring(key.lastIndexOf('.') + 1);
            if (bash) selected = "bash." + phaseName;
            else if (pose.type() == AttackType.SPECIAL && Combat.specialKind(entity, data) != null) {
                selected = "special." + Combat.specialKind(entity, data).name().toLowerCase(java.util.Locale.ROOT) + "." + phaseName;
            } else if (data.machine.variant() > 0) {
                selected = pose.type().serializedName() + ".variant_" + data.machine.variant() + "." + phaseName;
            }
        }
        return animation.clip(selected, key);
    }

    /** Edge into the cut for the whole attack: set during the windup, follows the arc, held through recovery. */
    private static double bladeTwist(SwingPose pose) {
        if (!pose.phase().isAttack() || pose.type() == AttackType.KICK || pose.type() == AttackType.THROW) {
            return 0;
        }
        double t = switch (pose.phase()) {
            case WINDUP -> 0;
            default -> pose.releaseProgress(); // release, or recovery from where the release ended
        };
        return pose.path().edgeAngle(t);
    }

    private static float viewYaw(LivingEntity entity, float partialTick) {
        return entity instanceof net.minecraft.world.entity.player.Player
                ? entity.getViewYRot(partialTick)
                : Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
    }

    /** Additive offset for a part, radians, already weighted. */
    public float offset(String part, int axis) {
        String channel = part;
        boolean limb = part.startsWith("right") || part.startsWith("left");
        if (leftHanded && limb) {
            channel = part.startsWith("right") ? "left" + part.substring(5) : "right" + part.substring(4);
        }
        double[] v = offsets.get(channel);
        double sign = leftHanded && limb && axis > 0 ? -1 : 1;
        return v == null ? 0f : (float) Math.toRadians(v[axis] * weight * sign);
    }

    /**
     * Weapon arm and in-hand weapon rotation (players): the blade on the arc, the hand drawn in toward the chest by
     * {@link #WRIST_RELAX} so the arm and blade don't form one straight line, and the arm countering the
     * body's twist.
     *
     * @param twistScale blade twist setting (1 full, 0 off, -1 reversed)
     */
    public WeaponRig rig(double gripDegrees, double twistScale, WeaponRig.TwistAxis twistAxis) {
        double[] body = bodyDegrees();
        WeaponRig solved = WeaponRig.solve(leftHanded ? -aimYaw : aimYaw, aimPitch, gripDegrees,
                bladeTwist * twistScale * (leftHanded ? -1 : 1), twistAxis,
                leftHanded ? WeaponRig.mirrorAngles(body) : body, WRIST_RELAX, gripGap);
        return leftHanded ? solved.mirrored() : solved;
    }

    /**
     * First person only: the swing spread {@code width} times wider around the view and raised {@code lift} degrees, so
     * it sweeps across the screen. The camera sits behind the hand, so the traced arc itself mostly points away from it.
     * Presentation only: the hit sweep never sees this.
     */
    public CombatPose spreadForView(double width, double lift, double viewPitch) {
        double pitchWidth = 1 + (width - 1) / 2; // an overhead already spans most of the vertical view
        double pitch = Mth.clamp(viewPitch + (aimPitch - viewPitch) * pitchWidth - lift, ArmAim.MIN_POSE_PITCH, 90);
        double yaw = spread(relativeYaw * width);
        return new CombatPose(phase, type, weight, aimYaw - relativeYaw + yaw, pitch, offsets, twoHanded, kick,
                bladeTwist, gripGap, leftHanded, bash, yaw, extension, firstPerson, swing);
    }

    /** Spread yaw, eased off past 100 degrees so a deep windup stays at the screen's edge rather than behind it. */
    private static double spread(double yaw) {
        double size = Math.abs(yaw);
        return size <= 100 ? yaw : Math.signum(yaw) * (100 + (size - 100) * 0.35);
    }

    public CombatPose withWeight(double nextWeight) {
        return new CombatPose(phase, type, nextWeight, aimYaw, aimPitch, offsets, twoHanded, kick, bladeTwist, gripGap,
                leftHanded, bash, relativeYaw, extension, firstPerson, swing);
    }

    /** Preserve additive channel ownership and blend their weighted contribution exactly once. */
    public CombatPose blendFrom(CombatPose previous, double fraction) {
        double f = Math.max(0, Math.min(1, fraction));
        double nextWeight = previous.weight + (weight - previous.weight) * f;
        if (nextWeight < 1e-6) return withWeight(0);
        Map<String, double[]> channels = new HashMap<>();
        var parts = new HashSet<>(previous.offsets.keySet());
        parts.addAll(offsets.keySet());
        for (String part : parts) {
            double[] from = previous.offsets.getOrDefault(part, NO_OFFSET);
            double[] to = offsets.getOrDefault(part, NO_OFFSET);
            double[] result = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                result[axis] = (from[axis] * previous.weight * (1 - f) + to[axis] * weight * f) / nextWeight;
            }
            channels.put(part, result);
        }
        return new CombatPose(phase, type, nextWeight, angle(previous.aimYaw, aimYaw, f),
                previous.aimPitch + (aimPitch - previous.aimPitch) * f, channels, twoHanded, kick,
                angle(previous.bladeTwist, bladeTwist, f), gripGap, leftHanded, bash,
                angle(previous.relativeYaw, relativeYaw, f), previous.extension + (extension - previous.extension) * f, firstPerson, swing);
    }

    private static double angle(double from, double to, double f) {
        double delta = ((to - from + 180) % 360 + 360) % 360 - 180;
        return from + delta * f;
    }


    /** The clip's whole-body rotation for this frame, degrees {x, y, z}, already weighted. */
    public double[] bodyDegrees() {
        double[] body = offsets.getOrDefault("body", NO_OFFSET);
        return new double[]{body[0] * weight, body[1] * weight, body[2] * weight};
    }

    /**
     * Weapon arm for mobs whose held item can't be turned in the hand (their renderer doesn't draw it through vanilla's
     * item layer; see {@code MobCombatPoses}): the blade on the arc, with the arm countering the
     * whole-body rotation the renderer applies (see {@code LivingEntityRendererMixin}), so the twist doesn't swing the
     * blade off the traced arc.
     */
    public double[] mobWeaponArm() {
        double[] arm = ArmAim.aimArmForBlade(aimYaw, aimPitch);
        Mat3 inBody = WeaponRig.bodyRotation(bodyDegrees()).transpose().mul(Mat3.zyx(arm[0], arm[1], arm[2]));
        return inBody.toZyx();
    }

    /** Off arm reaching for the grip just behind the weapon hand (mobs; players get it from {@link #rig}). */
    public double[] gripArm(double[] weaponArm) {
        Vec hand = (leftHanded ? WeaponRig.LEFT_SHOULDER : WeaponRig.RIGHT_SHOULDER).add(Mat3.zyx(weaponArm[0], weaponArm[1], weaponArm[2])
                .apply(new Vec(0, WeaponRig.HAND_DISTANCE, 0)));
        Vec bladeInBody = WeaponRig.bodyRotation(bodyDegrees()).transpose().apply(ArmAim.modelDirection(aimYaw, aimPitch));
        Vec grip = hand.subtract(bladeInBody.scale(gripGap));
        return WeaponRig.reach(leftHanded ? WeaponRig.RIGHT_SHOULDER : WeaponRig.LEFT_SHOULDER, grip);
    }
}
