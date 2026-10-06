package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PoseTest {
    private static final double EPS = 1e-6;

    @Test
    void clipHitsKeyframesExactly() {
        PoseClip clip = new PoseClip(List.of(
                new PoseClip.Keyframe(0, Map.of("body", new double[]{0, 20, 0})),
                new PoseClip.Keyframe(1, Map.of("body", new double[]{0, -30, 0}))));
        assertArrayEquals(new double[]{0, 20, 0}, clip.sample(0).get("body"), EPS);
        assertArrayEquals(new double[]{0, -30, 0}, clip.sample(1).get("body"), EPS);
        assertEquals(-5, clip.sample(0.5).get("body")[1], EPS, "smoothstep midpoint is the linear midpoint");
    }

    @Test
    void clipEasesBetweenKeyframes() {
        PoseClip clip = new PoseClip(List.of(
                new PoseClip.Keyframe(0, Map.of("x", new double[]{0, 0, 0})),
                new PoseClip.Keyframe(1, Map.of("x", new double[]{100, 0, 0}))));
        assertTrue(clip.sample(0.1).get("x")[0] < 10, "eases in");
        assertTrue(clip.sample(0.9).get("x")[0] > 90, "eases out");
    }

    @Test
    void missingPartsCountAsZeroAndUnsortedInputIsSorted() {
        PoseClip clip = new PoseClip(List.of(
                new PoseClip.Keyframe(1, Map.of("leftLeg", new double[]{-20, 0, 0})),
                new PoseClip.Keyframe(0, Map.of("head", new double[]{10, 0, 0}))));
        assertArrayEquals(new double[]{0, 0, 0}, clip.sample(0).get("leftLeg"), EPS);
        assertArrayEquals(new double[]{-20, 0, 0}, clip.sample(1).get("leftLeg"), EPS);
        assertArrayEquals(new double[]{0, 0, 0}, clip.sample(1).get("head"), EPS);
    }

    @Test
    void emptyClipSamplesNothing() {
        assertTrue(PoseClip.EMPTY.sample(0.5).isEmpty());
    }

    @Test
    void aimArmMatchesVanillaBowConvention() {
        // Looking straight ahead: arm points forward (-90°); looking down: arm hangs (0°).
        assertArrayEquals(new double[]{-Math.PI / 2, 0, 0}, ArmAim.aimArm(0, 0), EPS);
        assertArrayEquals(new double[]{0, 0, 0}, ArmAim.aimArm(0, 90), EPS);
        assertEquals(Math.toRadians(40), ArmAim.aimArm(40, 0)[1], EPS);
    }

    @Test
    void aimedArmPointsWhereAsked() {
        for (double yaw : new double[]{-60, 0, 45}) {
            for (double pitch : new double[]{-50, 0, 30}) {
                double[] rot = ArmAim.aimArm(yaw, pitch);
                Vec arm = ArmAim.armDirection(rot[0], rot[1]);
                Vec want = ArmAim.modelDirection(yaw, pitch);
                assertEquals(1.0, arm.dot(want), 1e-6, "yaw " + yaw + " pitch " + pitch);
            }
        }
    }

    @Test
    void bladeAimPitchesTheArmBelowTheBlade() {
        // A level blade needs the arm only slightly forward of hanging.
        assertEquals(Math.toRadians(-10), ArmAim.aimArmForBlade(0, 0)[0], EPS);
        // A blade raised 70° needs the arm out in front.
        assertEquals(Math.toRadians(-80), ArmAim.aimArmForBlade(0, -70)[0], EPS);
    }
}
