package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ItemRotationConventionTest {
    @Test void eachBackendConventionRendersTheSameCrossAxisPhysicalRotation() {
        double[] physical = {.61, -.83, 1.17};
        Mat3 expected = Mat3.zyx(physical[0], physical[1], physical[2]);

        for (ItemRotationConvention convention : ItemRotationConvention.values()) {
            double[] channels = convention.encode(physical[0], physical[1], physical[2]);
            assertEquals(0, backendRotation(convention, channels).distance(expected), 1e-9,
                    convention + " backend applies the canonical physical orientation");
        }
    }

    @Test void rigBlendKeepsTheSamePhysicalQuaternionPathForBothBackends() {
        double[] start = {-.72, 1.04, -.39};
        double[] end = {1.13, -.46, .91};
        double[] midpointAngles = RotationBlend.blend(start[0], start[1], start[2], end, .5);
        Mat3 expectedMidpoint = Mat3.zyx(midpointAngles[0], midpointAngles[1], midpointAngles[2]);

        for (ItemRotationConvention convention : ItemRotationConvention.values()) {
            RigPoseBlend blend = new RigPoseBlend(convention);
            RigPoseBlend.Bone a = bone(convention.encode(start[0], start[1], start[2]));
            RigPoseBlend.Bone b = bone(convention.encode(end[0], end[1], end[2]));
            blend.begin(1, 0, false, false, "sword");
            blend.apply("right_item", a);
            blend.begin(2, 50_000_000, true, false, "sword");
            blend.apply("right_item", b);
            blend.begin(3, 150_000_000, true, false, "sword");
            RigPoseBlend.Bone midpoint = blend.apply("right_item", b);
            assertEquals(0, backendRotation(convention,
                    new double[]{midpoint.rx(), midpoint.ry(), midpoint.rz()}).distance(expectedMidpoint), 1e-8,
                    convention + " rig blend follows the physical shortest-path rotation");
        }
    }

    @Test void standardBackendPreservesZeroAxisAndIdentityRotations() {
        for (double[] physical : new double[][]{{.61, -.83, 0}, {.61, 0, 1.17}, {0, 0, 0}}) {
            double[] channels = ItemRotationConvention.STANDARD.encode(physical[0], physical[1], physical[2]);
            assertEquals(0, backendRotation(ItemRotationConvention.STANDARD, channels)
                    .distance(Mat3.zyx(physical[0], physical[1], physical[2])), 1e-9,
                    "corrected backend applies the physical rotation even when one axis is zero");
        }
    }

    private static RigPoseBlend.Bone bone(double[] channels) {
        return new RigPoseBlend.Bone(channels[0], channels[1], channels[2], 0, 0, 0, 0);
    }

    /** Reproduce the item pose stack's axis/order operations, independently of encode/decode. */
    private static Mat3 backendRotation(ItemRotationConvention convention, double[] channels) {
        if (convention == ItemRotationConvention.PAL_SWAPPED) {
            return Mat3.rotZ(-channels[1]).mul(Mat3.rotY(-channels[2])).mul(Mat3.rotX(-channels[0]));
        }
        return Mat3.rotZ(-channels[2]).mul(Mat3.rotY(-channels[1])).mul(Mat3.rotX(-channels[0]));
    }
}
