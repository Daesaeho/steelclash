package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeometryTest {
    private static final double EPS = 1e-9;

    @Test
    void directionMatchesMinecraftLookVector() {
        // yaw 0 faces south (+z), yaw 90 faces west (-x), pitch 90 looks straight down
        assertVec(new Vec(0, 0, 1), Blade.direction(0, 0));
        assertVec(new Vec(-1, 0, 0), Blade.direction(90, 0));
        assertVec(new Vec(0, -1, 0), Blade.direction(0, 90));
    }

    @Test
    void segmentThroughBoxIntersects() {
        assertTrue(Blade.intersectsBox(new Vec(-2, 0.5, 0.5), new Vec(2, 0.5, 0.5), Vec.ZERO, new Vec(1, 1, 1)));
    }

    @Test
    void segmentEndingBeforeBoxMisses() {
        assertFalse(Blade.intersectsBox(new Vec(-2, 0.5, 0.5), new Vec(-0.1, 0.5, 0.5), Vec.ZERO, new Vec(1, 1, 1)));
    }

    @Test
    void parallelSegmentOutsideSlabMisses() {
        assertFalse(Blade.intersectsBox(new Vec(-2, 2, 0.5), new Vec(2, 2, 0.5), Vec.ZERO, new Vec(1, 1, 1)));
    }

    @Test
    void segmentInsideBoxIntersects() {
        assertTrue(Blade.intersectsBox(new Vec(0.2, 0.2, 0.2), new Vec(0.8, 0.8, 0.8), Vec.ZERO, new Vec(1, 1, 1)));
    }

    @Test
    void arcSampleInterpolatesBetweenKeyframes() {
        ArcPath path = ArcPath.horizontal(100);
        assertEquals(50, path.sample(0).yaw(), EPS);
        assertEquals(0, path.sample(0.5).yaw(), EPS);
        assertEquals(-50, path.sample(1).yaw(), EPS);
        assertEquals(25, path.sample(0.25).yaw(), EPS);
        // clamped outside [0, 1]
        assertEquals(-50, path.sample(7).yaw(), EPS);
    }

    @Test
    void thrustExtends() {
        ArcPath path = ArcPath.thrust();
        assertTrue(path.sample(0).extension() < path.sample(0.6).extension());
        assertEquals(1.0, path.sample(1).extension(), EPS);
    }

    @Test
    void horizontalSlashSweepsAcrossTargetInFront() {
        // Wielder at origin facing south; a 0.6-wide target 2 blocks in front. Blade at the start of the slash points
        // to the right of the target, at the end to the left, and passes through it in the middle.
        Vec pivot = new Vec(0, 1.5, 0);
        Vec min = new Vec(-0.3, 0, 1.7);
        Vec max = new Vec(0.3, 1.8, 2.3);
        ArcPath path = ArcPath.horizontal(140);
        assertFalse(hits(pivot, path, 0.0, min, max));
        assertTrue(hits(pivot, path, 0.5, min, max));
        assertFalse(hits(pivot, path, 1.0, min, max));
    }

    @Test
    void bladeTooShortMisses() {
        Vec pivot = new Vec(0, 1.5, 0);
        Blade.Segment s = Blade.at(pivot, 0, 0, ArcPath.thrust(), 1.0, 1.0);
        assertFalse(Blade.intersectsBox(s.hilt(), s.tip(), new Vec(-0.3, 0, 1.7), new Vec(0.3, 1.8, 2.3)));
    }

    @Test
    void speedScalingShortensForFasterWielders() {
        AttackTimings base = new AttackTimings(10, 4, 8);
        AttackTimings faster = base.scaledForSpeed(3.2, 1.6, 1.0);
        assertEquals(new AttackTimings(5, 2, 4), faster);
        AttackTimings slower = base.scaledForSpeed(0.8, 1.6, 1.0);
        assertEquals(new AttackTimings(20, 8, 16), slower);
        // factor is clamped to [0.5, 2]
        assertEquals(new AttackTimings(5, 2, 4), base.scaledForSpeed(100, 1.6, 1.0));
        // exponent 0 ignores attack speed
        assertEquals(base, base.scaledForSpeed(3.2, 1.6, 0));
    }

    @Test
    void timingsNeverDropBelowOneTick() {
        assertEquals(new AttackTimings(1, 1, 1), new AttackTimings(0, -5, 0));
    }

    @Test
    void mirroredArcRunsTheOtherWay() {
        ArcPath right = ArcPath.horizontal(100);
        ArcPath left = right.mirrored();
        assertEquals(-50, left.sample(0).yaw(), EPS);
        assertEquals(50, left.sample(1).yaw(), EPS);
        assertEquals(right.sample(0.3).pitch(), left.sample(0.3).pitch(), EPS, "only the side flips");
    }

    private static boolean hits(Vec pivot, ArcPath path, double t, Vec min, Vec max) {
        Blade.Segment s = Blade.at(pivot, 0, 0, path, t, 3.0);
        return Blade.intersectsBox(s.hilt(), s.tip(), min, max);
    }

    private static void assertVec(Vec expected, Vec actual) {
        assertEquals(expected.x(), actual.x(), 1e-6);
        assertEquals(expected.y(), actual.y(), 1e-6);
        assertEquals(expected.z(), actual.z(), 1e-6);
    }
}
