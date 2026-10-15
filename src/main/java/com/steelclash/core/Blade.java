package com.steelclash.core;

/** Blade geometry: a line segment from the wielder's pivot (shoulder) out to the tip. */
public final class Blade {
    private Blade() {
    }

    public record Segment(Vec hilt, Vec tip) {
        /** The point a fraction {@code s} of the way from the hilt to the tip. */
        public Vec along(double s) {
            return hilt.add(tip.subtract(hilt).scale(s));
        }
    }

    /** Unit look vector for a Minecraft yaw/pitch in degrees. */
    public static Vec direction(double yawDegrees, double pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        return new Vec(-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }

    /**
     * The blade at release progress {@code t}, for a wielder at {@code pivot} currently looking along
     * ({@code viewYaw}, {@code viewPitch}).
     */
    public static Segment at(Vec pivot, double viewYaw, double viewPitch, ArcPath path, double t, double length) {
        ArcPath.Keyframe k = path.sample(t);
        double pitch = Math.max(-90, Math.min(90, viewPitch + k.pitch()));
        Vec dir = direction(viewYaw + k.yaw(), pitch);
        return new Segment(pivot, pivot.add(dir.scale(length * k.extension())));
    }

    /**
     * Slab test: does the segment a→b pass through the axis-aligned box [min, max]?
     */
    public static boolean intersectsBox(Vec a, Vec b, Vec min, Vec max) {
        return intersectsBox(a, b, min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    /** Scalar bounds let callers use an existing hitbox without allocating two temporary vectors. */
    public static boolean intersectsBox(Vec a, Vec b, double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ) {
        return !Double.isNaN(entry(a, b, minX, minY, minZ, maxX, maxY, maxZ));
    }

    /**
     * Where the segment a→b first enters the box [min, max], as a fraction of the way from a to b (0 when a is already
     * inside), or NaN when it misses. For a blade from hilt to tip, that's the first point of the blade in the body.
     */
    public static double entry(Vec a, Vec b, double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ) {
        double tMin = 0;
        double tMax = 1;
        // This runs for every candidate at every blade sub-step; keep the slab data in scalars.
        for (int axis = 0; axis < 3; axis++) {
            double start;
            double delta;
            double lo;
            double hi;
            switch (axis) {
                case 0 -> {
                    start = a.x();
                    delta = b.x() - start;
                    lo = minX;
                    hi = maxX;
                }
                case 1 -> {
                    start = a.y();
                    delta = b.y() - start;
                    lo = minY;
                    hi = maxY;
                }
                default -> {
                    start = a.z();
                    delta = b.z() - start;
                    lo = minZ;
                    hi = maxZ;
                }
            }
            if (Math.abs(delta) < 1e-9) {
                if (start < lo || start > hi) {
                    return Double.NaN;
                }
            } else {
                double inv = 1.0 / delta;
                double t1 = (lo - start) * inv;
                double t2 = (hi - start) * inv;
                if (t1 > t2) {
                    double tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) {
                    return Double.NaN;
                }
            }
        }
        return tMin;
    }

    /**
     * Outward unit normal of the unique box face first entered by the segment, or {@code null} when the segment starts
     * inside the box or enters through an edge/corner where a single face normal is not meaningful.
     */
    public static Vec entryNormal(Vec a, Vec b, double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;
        int entryAxis = -1;
        double entrySign = 0;
        boolean tied = false;
        for (int axis = 0; axis < 3; axis++) {
            double start = component(a, axis);
            double delta = component(b, axis) - start;
            double lo = axis == 0 ? minX : axis == 1 ? minY : minZ;
            double hi = axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
            if (Math.abs(delta) < 1e-9) {
                if (start < lo || start > hi) {
                    return null;
                }
                continue;
            }
            double t1 = (lo - start) / delta;
            double t2 = (hi - start) / delta;
            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }
            tMax = Math.min(tMax, t2);
            if (t1 > tMin + 1e-9) {
                tMin = t1;
                entryAxis = axis;
                entrySign = delta > 0 ? -1 : 1;
                tied = false;
            } else if (entryAxis >= 0 && Math.abs(t1 - tMin) <= 1e-9) {
                tied = true;
            }
        }
        // Match entry()'s finite segment interval: a slab face beyond the tip (or behind the hilt) is not a contact.
        if (entryAxis < 0 || tied || tMin < -1e-9 || tMin > 1 || tMax < 0 || tMin > tMax) {
            return null;
        }
        return entryAxis == 0 ? new Vec(entrySign, 0, 0)
                : entryAxis == 1 ? new Vec(0, entrySign, 0) : new Vec(0, 0, entrySign);
    }

    private static double component(Vec v, int axis) {
        return axis == 0 ? v.x() : axis == 1 ? v.y() : v.z();
    }
}
