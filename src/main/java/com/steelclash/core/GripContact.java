package com.steelclash.core;

import java.util.List;
import java.util.Objects;

/**
 * Immutable opaque mesh texels, each represented by a transformed cuboid in item-root model pixels.
 * The cuboids are tested against the main hand's bounded palm volume after the weapon pose is solved.
 */
public final class GripContact {
    public record Prism(Vec centre, Vec halfX, Vec halfY, Vec halfZ) {
        public Prism {
            Objects.requireNonNull(centre);
            Objects.requireNonNull(halfX);
            Objects.requireNonNull(halfY);
            Objects.requireNonNull(halfZ);
            requireFinite(centre);
            requireFinite(halfX);
            requireFinite(halfY);
            requireFinite(halfZ);
            if (halfX.length() < 1e-9 || halfY.length() < 1e-9 || halfZ.length() < 1e-9)
                throw new IllegalArgumentException("grip texel prism needs three nonzero half-axes");
        }
    }

    private final List<Prism> texels;

    public GripContact(List<Prism> texels) {
        Objects.requireNonNull(texels);
        if (texels.isEmpty() || texels.size() > 256)
            throw new IllegalArgumentException("grip contact needs 1..256 opaque texels");
        this.texels = List.copyOf(texels);
    }

    public List<Prism> texels() { return texels; }

    /** SAT contact between any calibrated texel prism and an arm-local axis-aligned hand box. */
    public boolean intersects(Mat3 arm, Mat3 blade, Vec physicalRootShift, Vec socket,
                              Vec minimum, Vec maximum, double interiorMargin) {
        Objects.requireNonNull(arm);
        Objects.requireNonNull(blade);
        Objects.requireNonNull(physicalRootShift);
        Objects.requireNonNull(socket);
        Objects.requireNonNull(minimum);
        Objects.requireNonNull(maximum);
        requireFinite(physicalRootShift);
        requireFinite(socket);
        requireFinite(minimum);
        requireFinite(maximum);
        if (!Double.isFinite(interiorMargin) || interiorMargin < 0
                || minimum.x() >= maximum.x() || minimum.y() >= maximum.y() || minimum.z() >= maximum.z())
            throw new IllegalArgumentException("invalid grip hand bounds");

        Mat3 itemToArm = arm.transpose().mul(blade);
        Vec boxCentre = minimum.add(maximum).scale(.5);
        Vec boxHalf = maximum.subtract(minimum).scale(.5);
        for (Prism source : texels) {
            Vec centre = socket.add(arm.transpose().apply(physicalRootShift.add(blade.apply(source.centre()))));
            Vec hx = itemToArm.apply(source.halfX());
            Vec hy = itemToArm.apply(source.halfY());
            Vec hz = itemToArm.apply(source.halfZ());
            if (overlaps(centre, hx, hy, hz, boxCentre, boxHalf, interiorMargin)) return true;
        }
        return false;
    }

    private static boolean overlaps(Vec centre, Vec hx, Vec hy, Vec hz, Vec boxCentre, Vec boxHalf, double margin) {
        Vec offset = centre.subtract(boxCentre);
        // Cheap world-axis rejection avoids most of the full SAT work for texels outside the palm slab.
        if (!overlapAxis(new Vec(1, 0, 0), offset, hx, hy, hz, boxHalf, margin)
                || !overlapAxis(new Vec(0, 1, 0), offset, hx, hy, hz, boxHalf, margin)
                || !overlapAxis(new Vec(0, 0, 1), offset, hx, hy, hz, boxHalf, margin)) return false;

        Vec[] edges = {hx.scale(2), hy.scale(2), hz.scale(2)};
        Vec[] boxAxes = {new Vec(1, 0, 0), new Vec(0, 1, 0), new Vec(0, 0, 1)};
        if (!overlapAxis(edges[1].cross(edges[2]), offset, hx, hy, hz, boxHalf, margin)
                || !overlapAxis(edges[2].cross(edges[0]), offset, hx, hy, hz, boxHalf, margin)
                || !overlapAxis(edges[0].cross(edges[1]), offset, hx, hy, hz, boxHalf, margin)) return false;
        for (Vec edge : edges) for (Vec boxAxis : boxAxes)
            if (!overlapAxis(edge.cross(boxAxis), offset, hx, hy, hz, boxHalf, margin)) return false;
        return true;
    }

    private static boolean overlapAxis(Vec axis, Vec offset, Vec hx, Vec hy, Vec hz, Vec boxHalf, double margin) {
        double lengthSquared = axis.dot(axis);
        if (lengthSquared < 1e-20) return true;
        double prismRadius = Math.abs(axis.dot(hx)) + Math.abs(axis.dot(hy)) + Math.abs(axis.dot(hz));
        double boxRadius = Math.abs(axis.x()) * boxHalf.x() + Math.abs(axis.y()) * boxHalf.y()
                + Math.abs(axis.z()) * boxHalf.z();
        double permitted = prismRadius + boxRadius - margin * Math.sqrt(lengthSquared);
        return permitted >= 0 && Math.abs(axis.dot(offset)) <= permitted + 1e-10;
    }

    private static void requireFinite(Vec value) {
        if (!Double.isFinite(value.x()) || !Double.isFinite(value.y()) || !Double.isFinite(value.z()))
            throw new IllegalArgumentException("grip geometry must be finite");
    }
}
