package com.steelclash.core;

/** Which server-side collision surface produced a contact point. This never describes a rendered mesh point. */
public enum CollisionProvenance {
    /** No blade entry surface is available (for example, a legacy or manual combat hit). */
    UNKNOWN_OR_MANUAL,
    /** The raw segment point is inside or on the ordinary body AABB. */
    BODY_AABB_CONTACT,
    /** The expanded AABB supplied the entry normal; the reported point is clamped to the ordinary body AABB. */
    INFLATED_AABB_ENTRY_BODY_CLAMPED,
    /** A block collider supplied the clank point (and its face normal when the clip has a unique face). */
    BLOCK_SHAPE;

    public static CollisionProvenance at(Vec point, Vec bodyMin, Vec bodyMax) {
        return point.x() >= bodyMin.x() && point.x() <= bodyMax.x()
                && point.y() >= bodyMin.y() && point.y() <= bodyMax.y()
                && point.z() >= bodyMin.z() && point.z() <= bodyMax.z()
                ? BODY_AABB_CONTACT : INFLATED_AABB_ENTRY_BODY_CLAMPED;
    }
}
