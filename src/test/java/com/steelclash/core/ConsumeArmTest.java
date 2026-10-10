package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ConsumeArmTest {
    @Test void foodHandRaisesBelowTheEyeLineAndMirrors() {
        double[] left = WeaponRig.consumeArm(0, 0, new double[3], true, 0);
        double[] right = WeaponRig.consumeArm(0, 0, new double[3], false, 0);
        Vec l = WeaponRig.LEFT_SHOULDER.add(Mat3.zyx(left[0], left[1], left[2]).apply(new Vec(0, 10, 0)));
        Vec r = WeaponRig.RIGHT_SHOULDER.add(Mat3.zyx(right[0], right[1], right[2]).apply(new Vec(0, 10, 0)));
        double[] carried = WeaponRig.carryArm(0, 0, new double[3], true);
        Vec rest = WeaponRig.LEFT_SHOULDER.add(Mat3.zyx(carried[0], carried[1], carried[2]).apply(new Vec(0, 10, 0)));
        assertTrue(l.x() > 0 && l.x() < WeaponRig.LEFT_SHOULDER.x() && l.y() > -4
                && l.y() < rest.y() - 1 && l.z() < -WeaponRig.HAND_DISTANCE / 2, "raised grip: " + l);
        assertEquals(-l.x(), r.x(), 1e-9); assertEquals(l.y(), r.y(), 1e-9); assertEquals(l.z(), r.z(), 1e-9);
    }

    @Test void bodyCompensationPreservesTheViewRelativeHand() {
        double[] body = {12, -25, 8};
        double[] actual = WeaponRig.consumeArm(18, -20, body, true, 7.5);
        double[] base = WeaponRig.consumeArm(0, 0, new double[3], true, 7.5);
        Mat3 expected = Mat3.rotY(Math.toRadians(18)).mul(Mat3.rotX(Math.toRadians(-20)))
                .mul(Mat3.zyx(base[0], base[1], base[2]));
        Mat3 world = WeaponRig.bodyRotation(body).mul(Mat3.zyx(actual[0], actual[1], actual[2]));
        assertEquals(0, expected.distance(world), 1e-8);
    }
}
