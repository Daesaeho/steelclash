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
 * progress in [0, 1]. Parts are free-form names ("body", "head", "leftArm", "leftLeg", "rightArmBend", ...); a part
 * missing from a keyframe counts as zero there.
 * <p>
 * Motion eases in at the first keyframe and out at the last, and carries its speed through keyframes in between (a
 * monotone cubic): a body turning 45, 0, -50 sweeps through the middle key instead of stopping on it and lurching on.
 * A keyframe where a part reverses direction still holds it there for an instant, and nothing overshoots a key.
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
        int ia = 0; // the last keyframe at or before t, and the next one (the same one before the first or at a key)
        for (int k = 0; k < keyframes.size(); k++) {
            if (keyframes.get(k).t() <= t) {
                ia = k;
            }
        }
        int ib = keyframes.get(ia).t() < t && ia + 1 < keyframes.size() ? ia + 1 : ia;
        Keyframe a = keyframes.get(ia);
        Keyframe b = keyframes.get(ib);
        double span = b.t() - a.t();
        double s = span <= 1e-9 ? 0 : (t - a.t()) / span;
        // Cubic Hermite basis: positions at both keys, plus each key's tangent scaled to the segment.
        double s2 = s * s;
        double s3 = s2 * s;
        double h00 = 2 * s3 - 3 * s2 + 1;
        double h10 = (s3 - 2 * s2 + s) * span;
        double h01 = 3 * s2 - 2 * s3;
        double h11 = (s3 - s2) * span;
        for (String part : partNames) {
            double[] v = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                v[axis] = (h00 * value(ia, part, axis) + h01 * value(ib, part, axis)
                        + (span <= 1e-9 ? 0 : h10 * tangent(ia, part, axis) + h11 * tangent(ib, part, axis))) * scale;
            }
            double x = v[0];
            double y = v[1];
            double z = v[2];
            if (mirrored && (part.equals("body") || part.equals("head") || part.equals("torso"))) {
                y = -y;
                z = -z;
            }
            out.put(part, new double[]{x, y, z});
        }
        return out;
    }

    private static final double[] ZERO = {0, 0, 0};

    private double value(int key, String part, int axis) {
        double[] v = keyframes.get(key).parts().getOrDefault(part, ZERO);
        return axis < v.length ? v[axis] : 0;
    }

    /**
     * Rate of change at a keyframe (per unit of progress): zero at the first and last keys (ease in and out) and where
     * the part turns round or holds; otherwise a weighted harmonic mean of the two neighbouring slopes (Fritsch-Butland),
     * which keeps each segment monotone, so the curve never overshoots a key.
     */
    private double tangent(int key, String part, int axis) {
        if (key == 0 || key == keyframes.size() - 1) {
            return 0;
        }
        double h0 = keyframes.get(key).t() - keyframes.get(key - 1).t();
        double h1 = keyframes.get(key + 1).t() - keyframes.get(key).t();
        if (h0 <= 1e-9 || h1 <= 1e-9) {
            return 0;
        }
        double d0 = (value(key, part, axis) - value(key - 1, part, axis)) / h0;
        double d1 = (value(key + 1, part, axis) - value(key, part, axis)) / h1;
        if (d0 * d1 <= 0) {
            return 0;
        }
        double w0 = 2 * h1 + h0;
        double w1 = h1 + 2 * h0;
        return (w0 + w1) / (w0 / d0 + w1 / d1);
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
