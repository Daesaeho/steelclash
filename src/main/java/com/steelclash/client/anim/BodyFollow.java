package com.steelclash.client.anim;

import com.steelclash.Config;
import com.steelclash.client.HitReactions;
import com.steelclash.client.dev.PoseSheet;
import com.steelclash.core.PoseChannels;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Two adjustments to an attack's body pose (client config, visual only): the upper body leans with where the fighter
 * looks ({@code aimLean}), and while the fighter moves the legs keep walking under the attack instead of being carried
 * by its stance and twist ({@code legsKeepFootwork}). The weapon arm already counters the whole-body rotation, so the
 * blade stays on the traced arc.
 */
final class BodyFollow {
    private static final double[] ZERO = {0, 0, 0};
    /** Walk-animation speed at which the legs are fully the walk's (walking is about 0.4, sprinting nearer 1). */
    private static final double FULL_STRIDE = 0.3;

    private BodyFollow() {
    }

    @Nullable
    static CombatPose apply(LivingEntity entity, @Nullable CombatPose pose, float partialTick) {
        if (pose == null || PoseSheet.running() || pose.kick()) {
            return pose;
        }
        double lean = Config.Client.AIM_LEAN.get();
        boolean leaning = lean > 0 && pose.phase().isAttack() && !HitReactions.ownFirstPerson(entity);
        double stride = Config.Client.LEGS_KEEP_FOOTWORK.get() ? stride(entity, partialTick) : 0;
        if (!leaning && stride <= 0) {
            return pose;
        }
        Map<String, double[]> offsets = new HashMap<>(pose.offsets());
        double[] body = offsets.getOrDefault("body", ZERO).clone();
        if (leaning) {
            // In model space a positive body X tips the top backward, so looking down (pitch +) leans the other way.
            double bend = entity.getViewXRot(partialTick) * lean;
            body[0] -= bend;
            offsets.put("body", body);
            // The head already pitches to the full view on its own; the bend would tip it further than the eyes look.
            double[] head = offsets.getOrDefault("head", ZERO).clone();
            head[0] -= bend;
            offsets.put("head", head);
        }
        if (stride > 0) {
            // The whole model turns about the hips; the legs turn back by as much, so they stay upright and keep their
            // walk. The model part angles that undo body (x, y, z) are (x, y, -z); limb channels are read mirrored for a
            // left-handed fighter (CombatPose.offset), so their z goes in mirrored too.
            double counterZ = pose.leftHanded() ? body[2] : -body[2];
            for (String leg : new String[]{"rightLeg", "leftLeg"}) {
                double[] authored = offsets.getOrDefault(leg, ZERO);
                offsets.put(leg, new double[]{Mth.lerp(stride, authored[0], body[0]), authored[1] * (1 - stride),
                        Mth.lerp(stride, authored[2], counterZ)});
            }
            double hipLag = PoseChannels.value(pose.offsets(), "hipLag")[1];
            offsets.put("hipLag", new double[]{0, Mth.lerp(stride, hipLag, body[1]), 0});
        }
        return new CombatPose(pose.phase(), pose.type(), pose.weight(), pose.aimYaw(), pose.aimPitch(), offsets,
                pose.twoHanded(), pose.kick(), pose.bladeTwist(), pose.gripGap(), pose.leftHanded(), pose.bash(),
                pose.relativeYaw(), pose.extension(), pose.firstPerson(), pose.swing());
    }

    /** 0 standing still, 1 walking or faster, eased between. */
    private static double stride(LivingEntity entity, float partialTick) {
        double t = Math.min(1, entity.walkAnimation.speed(partialTick) / FULL_STRIDE);
        return t * t * (3 - 2 * t);
    }
}
