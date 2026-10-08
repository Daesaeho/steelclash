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
import com.steelclash.core.PoseBlend;
import com.steelclash.core.PoseClip;
import com.steelclash.core.Vec;
import com.steelclash.core.WeaponRig;
import com.steelclash.profile.WeaponProfile;
import com.steelclash.profile.WeaponProfiles;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
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
 * @param gripGap     pixels between the hands on a two-handed grip
 */
public record CombatPose(Phase phase, double weight, double aimYaw, double aimPitch, Map<String, double[]> offsets,
                         boolean twoHanded, boolean kick, double bladeTwist, double gripGap) {
    /** How far the weapon hand is drawn in from the blade's line toward the chest (0 = arm and blade in one line). */
    private static final double WRIST_RELAX = 0.45;
    private static final double[] NO_OFFSET = {0, 0, 0};

    /** First-person ready stance: weapon held up at the right, blade rising, degrees from the view. */
    private static final double READY_YAW = 65;
    private static final double READY_PITCH = -45;

    /** Per fighter: the pose last shown and any hand-over in progress ({@link PoseBlend}). Render thread only. */
    private static final Map<LivingEntity, Transition> TRANSITIONS = new WeakHashMap<>();

    private static final class Transition {
        boolean seen;
        int serial;
        AttackType type;
        boolean mirrored;
        Phase phase;
        CombatPose shown;
        CombatPose from;
        double startTicks;
        /** This windup started while the arm was still posed (a combo, riposte, morph): it stays at full weight. */
        boolean chained;
    }

    /** The animation layer's activation check needs availability, not an interpolated pose and sampled clips. */
    public static boolean isAvailable(LivingEntity entity) {
        if (!entity.hasData(ModAttachments.COMBAT)) {
            return false;
        }
        CombatData data = entity.getData(ModAttachments.COMBAT);
        if (data.machine.phase() == Phase.IDLE) {
            Transition transition = TRANSITIONS.get(entity);
            return transition != null && transition.from != null; // still fading out (a feint)
        }
        return Combat.currentSpec(entity, data).isPresent();
    }

    /**
     * This frame's combat pose, with discontinuous phase changes (combos, ripostes, morphs, staggers, feints) blended
     * from the pose shown before over {@link PoseBlend#BLEND_TICKS}.
     */
    public static Optional<CombatPose> of(LivingEntity entity, float partialTick) {
        Optional<CombatPose> raw = raw(entity, partialTick);
        if (PoseSheet.running() || !entity.hasData(ModAttachments.COMBAT)) {
            return raw; // the pose sheet pins exact poses
        }
        CombatData data = entity.getData(ModAttachments.COMBAT);
        Transition t = TRANSITIONS.computeIfAbsent(entity, e -> new Transition());
        double now = entity.level().getGameTime() + partialTick;
        Phase phase = data.machine.phase();
        int serial = data.machine.attackSerial();
        AttackType type = data.machine.type();
        boolean mirrored = data.machine.isMirrored();
        if (!t.seen || !PoseBlend.continuous(t.serial, t.type, t.mirrored, t.phase, serial, type, mirrored, phase)) {
            boolean visible = t.seen && t.shown != null && t.shown.weight() > 0.02;
            t.from = visible ? t.shown : null;
            t.startTicks = now;
            t.chained = visible && phase == Phase.WINDUP;
        }
        t.seen = true;
        t.serial = serial;
        t.type = type;
        t.mirrored = mirrored;
        t.phase = phase;
        if (phase != Phase.WINDUP) {
            t.chained = false;
        }
        CombatPose target = raw.orElse(null);
        if (target != null && t.chained) {
            target = target.withWeight(1); // straight from the last pose into the windup, not back down to rest first
        }
        if (t.from != null) {
            double progress = (now - t.startTicks) / PoseBlend.BLEND_TICKS;
            if (progress >= 1 || progress < 0) {
                t.from = null;
            } else {
                double s = PoseBlend.ease(progress);
                target = target == null ? t.from.withWeight(t.from.weight() * (1 - s)) : blend(t.from, target, s);
            }
        }
        t.shown = target;
        return Optional.ofNullable(target);
    }

    /**
     * First-person ready stance: the held weapon up at the right, blade rising, following the view. Attacks blend out of
     * it and back into it, so first person never cuts between vanilla's hand and the animated arms.
     */
    public static Optional<CombatPose> ready(LivingEntity entity, float partialTick) {
        return WeaponProfiles.resolve(entity.getMainHandItem(), entity.level().registryAccess()).map(resolved -> {
            String archetype = resolved.profile().archetype();
            AnimationSet animation = AnimationLibrary.INSTANCE.get(archetype);
            boolean twoHanded = animation.twoHanded()
                    || (Config.Client.TWO_HANDED_SWORDS.get() && "sword".equals(archetype) && entity.getOffhandItem().isEmpty());
            float headYaw = Mth.wrapDegrees(viewYaw(entity, partialTick) - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot));
            double[] tuning = PoseSheet.readyOverride();
            double readyYaw = tuning != null ? tuning[0] : READY_YAW;
            double readyPitch = tuning != null ? tuning[1] : READY_PITCH;
            double aimPitch = Mth.clamp(entity.getViewXRot(partialTick) + readyPitch, -90, 90);
            return new CombatPose(Phase.IDLE, 1, headYaw + readyYaw, aimPitch, Map.of(), twoHanded, false, 0, animation.gripGap());
        });
    }

    private CombatPose withWeight(double newWeight) {
        return new CombatPose(phase, newWeight, aimYaw, aimPitch, offsets, twoHanded, kick, bladeTwist, gripGap);
    }

    private static CombatPose blend(CombatPose from, CombatPose to, double s) {
        return new CombatPose(to.phase, PoseBlend.lerp(s, from.weight, to.weight), PoseBlend.angle(s, from.aimYaw, to.aimYaw),
                PoseBlend.lerp(s, from.aimPitch, to.aimPitch), PoseBlend.offsets(s, from.offsets, to.offsets), to.twoHanded,
                to.kick, PoseBlend.lerp(s, from.bladeTwist, to.bladeTwist), PoseBlend.lerp(s, from.gripGap, to.gripGap));
    }

    private static Optional<CombatPose> raw(LivingEntity entity, float partialTick) {
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
        boolean mirrored = data.machine.isMirrored() && pose.phase().isAttack();
        Map<String, double[]> offsets;
        if (data.machine.isHeavy() && pose.phase() == Phase.WINDUP) {
            // Heavies have their own windup clip; older animation sets just exaggerate the light one.
            PoseClip heavy = animation.clip(pose.type().serializedName() + ".heavy_windup");
            offsets = heavy.isEmpty()
                    ? animation.clip(key).sample(progress, animation.heavyWindupScale(), mirrored)
                    : heavy.sample(progress, 1, mirrored);
        } else if (data.machine.isThwacked() && pose.phase() == Phase.RECOVERY) {
            // The blade stopped in a body: hold the body where the release was at contact while the pose eases out.
            offsets = animation.clip(AnimationSet.clipKey(Phase.RELEASE, pose.type()))
                    .sample(data.machine.recoverFrom(), 1, mirrored);
        } else {
            offsets = animation.clip(key).sample(progress, 1, mirrored);
        }
        boolean twoHanded = animation.twoHanded()
                || (Config.Client.TWO_HANDED_SWORDS.get() && "sword".equals(archetype) && entity.getOffhandItem().isEmpty());

        float headYaw = Mth.wrapDegrees(viewYaw(entity, partialTick) - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot));
        double aimYaw = headYaw + pose.yaw();
        double aimPitch = Mth.clamp(entity.getViewXRot(partialTick) + pose.pitch(), -90, 90);
        return Optional.of(new CombatPose(pose.phase(), pose.weight(), aimYaw, aimPitch, offsets,
                twoHanded, pose.type() == AttackType.KICK && pose.phase().isAttack(), bladeTwist(data, pose), animation.gripGap()));
    }

    /** Edge into the cut for the whole attack: set during the windup, follows the arc, held through recovery. */
    private static double bladeTwist(CombatData data, SwingPose pose) {
        if (!pose.phase().isAttack() || pose.type() == AttackType.KICK || pose.type() == AttackType.THROW) {
            return 0;
        }
        double t = switch (pose.phase()) {
            case WINDUP -> 0;
            default -> pose.releaseProgress(); // release, or recovery from where the release ended
        };
        return Combat.currentPath(data, pose.spec()).edgeAngle(t);
    }

    private static float viewYaw(LivingEntity entity, float partialTick) {
        return entity instanceof net.minecraft.world.entity.player.Player
                ? entity.getViewYRot(partialTick)
                : Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
    }

    /** Additive offset for a part, radians, already weighted. */
    public float offset(String part, int axis) {
        double[] v = offsets.get(part);
        return v == null ? 0f : (float) Math.toRadians(v[axis] * weight);
    }

    /**
     * Weapon arm and in-hand weapon rotation (players): the blade on the arc, the hand drawn in toward the chest by
     * {@link #WRIST_RELAX} so the arm and blade don't form one straight line, and the arm countering the
     * body's twist.
     *
     * @param twistScale blade twist setting (1 full, 0 off, -1 reversed)
     */
    public WeaponRig rig(double gripDegrees, double twistScale, WeaponRig.TwistAxis twistAxis) {
        return WeaponRig.solve(aimYaw, aimPitch, gripDegrees, bladeTwist * twistScale, twistAxis, bodyDegrees(), WRIST_RELAX, gripGap);
    }


    /** The clip's whole-body rotation for this frame, degrees {x, y, z}, already weighted. */
    public double[] bodyDegrees() {
        double[] body = offsets.getOrDefault("body", NO_OFFSET);
        return new double[]{body[0] * weight, body[1] * weight, body[2] * weight};
    }

    /**
     * Weapon arm for mobs (the held item can't be rotated): the blade on the arc, with the arm countering the
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
        Vec hand = WeaponRig.RIGHT_SHOULDER.add(Mat3.zyx(weaponArm[0], weaponArm[1], weaponArm[2])
                .apply(new Vec(0, WeaponRig.HAND_DISTANCE, 0)));
        Vec bladeInBody = WeaponRig.bodyRotation(bodyDegrees()).transpose().apply(ArmAim.modelDirection(aimYaw, aimPitch));
        Vec grip = hand.subtract(bladeInBody.scale(gripGap));
        return WeaponRig.reach(WeaponRig.LEFT_SHOULDER, grip);
    }
}
