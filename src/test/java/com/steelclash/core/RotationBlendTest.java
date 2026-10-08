package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class RotationBlendTest {
    @Test
    void yawWrapTakesTheTwoDegreePath() {
        double[] target = {0, Math.toRadians(-179), 0};
        double[] middle = RotationBlend.blend(0, Math.toRadians(179), 0, target, 0.5);
        assertEquals(0, rotation(middle).distance(Mat3.rotY(Math.PI)), 1e-9);
    }

    @Test
    void equivalentEulerBranchesProduceTheSamePartialRotation() {
        double[] a = {0.3, 0.8, -0.4};
        double[] equivalent = {a[0] + Math.PI, Math.PI - a[1], a[2] + Math.PI};
        assertEquals(0, rotation(a).distance(rotation(equivalent)), 1e-9);
        for (double weight : new double[]{0.1, 0.3, 0.5, 0.8}) {
            assertEquals(0, rotation(RotationBlend.blend(-0.2, 0.1, 0.2, a, weight))
                    .distance(rotation(RotationBlend.blend(-0.2, 0.1, 0.2, equivalent, weight))), 1e-9);
        }
    }

    @Test
    void randomBlendsPreserveEndpointsAndFollowTheShortestRotation() {
        Random random = new Random(31);
        for (int i = 0; i < 500; i++) {
            double[] from = angles(random), to = angles(random);
            Mat3 start = rotation(from), end = rotation(to);
            assertEquals(0, rotation(RotationBlend.blend(from[0], from[1], from[2], to, 0)).distance(start), 1e-9);
            assertEquals(0, rotation(RotationBlend.blend(from[0], from[1], from[2], to, 1)).distance(end), 1e-9);
            double full = angle(start, end);
            Mat3 middle = rotation(RotationBlend.blend(from[0], from[1], from[2], to, 0.5));
            assertEquals(full / 2, angle(start, middle), 1e-6);
            assertEquals(full / 2, angle(middle, end), 1e-6);
            assertTrue(Double.isFinite(middle.m00()));
        }
    }

    private static double[] angles(Random random) {
        return new double[]{random.nextDouble() * 6 - 3, random.nextDouble() * 6 - 3, random.nextDouble() * 6 - 3};
    }

    private static Mat3 rotation(double[] angles) { return Mat3.zyx(angles[0], angles[1], angles[2]); }

    private static double angle(Mat3 from, Mat3 to) {
        Mat3 relative = from.transpose().mul(to);
        return Math.acos(Math.max(-1, Math.min(1, (relative.m00() + relative.m11() + relative.m22() - 1) / 2)));
    }
}
