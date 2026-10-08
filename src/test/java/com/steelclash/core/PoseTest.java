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
        assertTrue(PoseClip.EMPTY.sample(0.5, 1.5, true).isEmpty());
    }

    @Test
    void fusedSamplingScalesThenMirrorsOnlyBodyHeadAndTorso() {
        PoseClip clip = new PoseClip(List.of(new PoseClip.Keyframe(0, Map.of(
                "body", new double[]{10, 20, 30}, "head", new double[]{4, 5, 6},
                "torso", new double[]{1, 2, 3}, "leftArm", new double[]{7, 8, 9}))));
        Map<String, double[]> pose = clip.sample(0.5, 2, true);
        assertArrayEquals(new double[]{20, -40, -60}, pose.get("body"), EPS);
        assertArrayEquals(new double[]{8, -10, -12}, pose.get("head"), EPS);
        assertArrayEquals(new double[]{2, -4, -6}, pose.get("torso"), EPS);
        assertArrayEquals(new double[]{14, 16, 18}, pose.get("leftArm"), EPS);
        assertArrayEquals(new double[]{10, 20, 30}, clip.sample(0.5).get("body"), EPS,
                "sampling must not mutate shared keyframes");
    }

    @Test
    void fusedSamplingPreservesInterpolationClampingAndMissingAxes() {
        PoseClip clip = new PoseClip(List.of(
                new PoseClip.Keyframe(1, Map.of("head", new double[]{-20, 30, -40})),
                new PoseClip.Keyframe(0.2, Map.of("body", new double[]{10, 5}, "leftLeg", new double[]{5}))));
        for (double t : new double[]{-1, 0, 0.2, 0.3, 0.6, 0.99, 1, 2}) {
            for (double factor : new double[]{0, 1, 1.5, -2}) {
                for (boolean mirrored : new boolean[]{false, true}) {
                    Map<String, double[]> expected = PoseClip.scale(clip.sample(t), factor);
                    if (mirrored) {
                        for (String part : List.of("body", "head")) {
                            expected.get(part)[1] = -expected.get(part)[1];
                            expected.get(part)[2] = -expected.get(part)[2];
                        }
                    }
                    Map<String, double[]> actual = clip.sample(t, factor, mirrored);
                    assertEquals(expected.keySet(), actual.keySet());
                    expected.forEach((part, v) -> assertArrayEquals(v, actual.get(part), EPS));
                }
            }
        }
    }

    @Test
    void returnedSamplesOwnTheirArrays() {
        PoseClip clip = new PoseClip(List.of(new PoseClip.Keyframe(0, Map.of("body", new double[]{1, 2, 3}))));
        Map<String, double[]> first = clip.sample(0, 2, true);
        first.get("body")[0] = 999;
        assertArrayEquals(new double[]{2, -4, -6}, clip.sample(0, 2, true).get("body"), EPS);
        assertArrayEquals(new double[]{1, 2, 3}, clip.sample(0).get("body"), EPS);
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
