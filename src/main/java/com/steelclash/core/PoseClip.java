package com.steelclash.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Keyframed body-part offsets for one phase of an animation (e.g. "slash windup"), in degrees, sampled by phase
 * progress in [0, 1] with smooth easing between keyframes. Parts are free-form names ("body", "head", "leftArm",
 * "leftLeg", "rightArmBend", ...); a part missing from a keyframe counts as zero there.
 */
public final class PoseClip {
    public record Keyframe(double t, Map<String, double[]> parts) {
    }

    public static final PoseClip EMPTY = new PoseClip(List.of());

    private final List<Keyframe> keyframes;
    private final Set<String> partNames;

    public PoseClip(List<Keyframe> keyframes) {
        List<Keyframe> sorted = new ArrayList<>(keyframes);
        sorted.sort(Comparator.comparingDouble(Keyframe::t));
        this.keyframes = List.copyOf(sorted);
        Set<String> names = new TreeSet<>();
        for (Keyframe k : sorted) {
            names.addAll(k.parts().keySet());
        }
        this.partNames = Set.copyOf(names);
    }

    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    public Set<String> partNames() {
        return partNames;
    }

    /** Offsets (degrees, xyz) for every part this clip animates, at progress {@code t}. */
    public Map<String, double[]> sample(double t) {
        return sample(t, 1, false);
    }

    /**
     * Samples, scales and mirrors in one pass, so a heavy mirrored windup needs only one map and one array per part.
     * Mirroring flips yaw and roll of the body, head and torso; limb offsets stay on their authored side.
     */
    public Map<String, double[]> sample(double t, double scale, boolean mirrored) {
        Map<String, double[]> out = new HashMap<>();
        if (keyframes.isEmpty()) {
            return out;
        }
        t = Math.max(0, Math.min(1, t));
        Keyframe a = keyframes.get(0);
        Keyframe b = a;
        for (Keyframe k : keyframes) {
            if (k.t() <= t) {
                a = k;
            }
            if (k.t() >= t) {
                b = k;
                break;
            }
            b = k;
        }
        double span = b.t() - a.t();
        double f = span <= 1e-9 ? 0 : smooth((t - a.t()) / span);
        for (String part : partNames) {
            double[] va = a.parts().getOrDefault(part, ZERO);
            double[] vb = b.parts().getOrDefault(part, ZERO);
            double x = lerp(va, vb, 0, f) * scale;
            double y = lerp(va, vb, 1, f) * scale;
            double z = lerp(va, vb, 2, f) * scale;
            if (mirrored && (part.equals("body") || part.equals("head") || part.equals("torso"))) {
                y = -y;
                z = -z;
            }
            out.put(part, new double[]{x, y, z});
        }
        return out;
    }

    private static final double[] ZERO = {0, 0, 0};

    private static double lerp(double[] a, double[] b, int i, double f) {
        double av = i < a.length ? a[i] : 0;
        double bv = i < b.length ? b[i] : 0;
        return av + (bv - av) * f;
    }

    static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /** Multiplies every offset in a sample (e.g. heavies exaggerate their windup). */
    public static Map<String, double[]> scale(Map<String, double[]> sample, double factor) {
        Map<String, double[]> out = new HashMap<>();
        sample.forEach((k, v) -> out.put(k, new double[]{v[0] * factor, v[1] * factor, v[2] * factor}));
        return out;
    }
}
