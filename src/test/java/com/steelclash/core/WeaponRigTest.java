package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class WeaponRigTest {
    private static final double[] NO_BODY = {0, 0, 0};

    @Test
    void leftHandedRigReflectsArmItemGripAndShoulderWithoutChangingTheirLengths() {
        Random random = new Random(43);
        for (int i = 0; i < 300; i++) {
            WeaponRig right = WeaponRig.solve(random.nextDouble() * 300 - 150, random.nextDouble() * 160 - 80,
                    -80, 25, WeaponRig.TwistAxis.Z, new double[]{5, 20, 3}, 0.45, 7);
            WeaponRig left = right.mirrored();
            for (double[] angles : new double[][]{right.arm(), right.item(), right.offArm()}) {
                Vec v = Mat3.zyx(angles[0], angles[1], angles[2]).apply(new Vec(0, 1, 0));
                double[] reflected = WeaponRig.mirrorAngles(angles);
                Vec mirrored = Mat3.zyx(reflected[0], reflected[1], reflected[2]).apply(new Vec(0, 1, 0));
                assertEquals(-v.x(), mirrored.x(), 1e-9);
                assertEquals(v.y(), mirrored.y(), 1e-9);
                assertEquals(v.z(), mirrored.z(), 1e-9);
            }
            assertEquals(-right.offShoulder().x(), left.offShoulder().x(), 1e-9);
            assertEquals(right.offShoulder().y(), left.offShoulder().y(), 1e-9);
            assertEquals(right.offShoulder().z(), left.offShoulder().z(), 1e-9);
            assertArrayEquals(right.arm(), left.mirrored().arm(), 1e-9);
            assertArrayEquals(right.item(), left.mirrored().item(), 1e-9);
            assertArrayEquals(right.offArm(), left.mirrored().offArm(), 1e-9);
        }
    }

    @Test
    void zyxRoundTrips() {
        Random random = new Random(7);
        for (int i = 0; i < 500; i++) {
            double x = (random.nextDouble() - 0.5) * 6, y = (random.nextDouble() - 0.5) * 3, z = (random.nextDouble() - 0.5) * 6;
            Mat3 m = Mat3.zyx(x, y, z);
            double[] back = m.toZyx();
            assertEquals(0, m.distance(Mat3.zyx(back[0], back[1], back[2])), 1e-9);
        }
    }

    @Test
    void unrelaxedWithoutBodyIsTheOldStraightArm() {
        WeaponRig rig = WeaponRig.solve(60, -10, -80, 35, WeaponRig.TwistAxis.Z, NO_BODY, 0);
        double[] straight = ArmAim.aimArm(60, -10);
        assertArrayEquals(straight, rig.arm(), 1e-9);
        assertArrayEquals(new double[]{Math.toRadians(-80), 0, Math.toRadians(35)}, rig.item(), 1e-9);
    }

    /** The point of the rig: however the arm relaxes and the body turns, the blade stays where the arc puts it. */
    @Test
    void bladeStaysOnTheArcWhateverTheArmAndBodyDo() {
        Random random = new Random(11);
        for (int i = 0; i < 2000; i++) {
            double yaw = (random.nextDouble() - 0.5) * 300;
            double pitch = (random.nextDouble() - 0.5) * 170;
            double twist = (random.nextDouble() - 0.5) * 180;
            double[] body = {(random.nextDouble() - 0.5) * 60, (random.nextDouble() - 0.5) * 100, (random.nextDouble() - 0.5) * 40};
            double relax = random.nextDouble();
            WeaponRig.TwistAxis axis = WeaponRig.TwistAxis.values()[random.nextInt(3)];
            Mat3 expected = WeaponRig.solve(yaw, pitch, -80, twist, axis, NO_BODY, 0).bladeOrientation(NO_BODY);
            Mat3 actual = WeaponRig.solve(yaw, pitch, -80, twist, axis, body, relax).bladeOrientation(body);
            assertEquals(0, expected.distance(actual), 1e-6, "yaw " + yaw + " pitch " + pitch + " body " + body[1] + " relax " + relax);
        }
    }

    @Test
    void relaxingBringsTheHandInTowardTheChest() {
        // Weapon cocked far to the right: the arm points less far round than the blade does.
        WeaponRig rig = WeaponRig.solve(90, -10, -80, 0, WeaponRig.TwistAxis.Z, NO_BODY, 0.3);
        Vec arm = ArmAim.armDirection(rig.arm()[0], rig.arm()[1]);
        Vec blade = ArmAim.modelDirection(90, -10);
        double between = Math.toDegrees(Math.acos(arm.dot(blade)));
        assertTrue(between > 20 && between < 40, "wrist bend " + between);
        assertTrue(arm.dot(ArmAim.modelDirection(0, 25)) > blade.dot(ArmAim.modelDirection(0, 25)), "arm nearer the home direction");
    }

    @Test
    void armCountersTheBodyTwist() {
        // The body turns 30° one way; the arm turns 30° the other way relative to it, so it still points the same way.
        double[] body = {0, 30, 0};
        WeaponRig turned = WeaponRig.solve(40, 0, -80, 0, WeaponRig.TwistAxis.Z, body, 0);
        Mat3 armWorld = WeaponRig.bodyRotation(body).mul(Mat3.zyx(turned.arm()[0], turned.arm()[1], turned.arm()[2]));
        Vec dir = armWorld.apply(new Vec(0, 1, 0));
        Vec want = ArmAim.modelDirection(40, 0);
        assertEquals(1, dir.dot(want), 1e-9);
    }

    @Test
    void oppositeDirectionsDoNotBlowUp() {
        Vec from = new Vec(0, 0, -1);
        Vec result = WeaponRig.towards(from, new Vec(0, 0, 1), 0.5);
        assertEquals(1, result.length(), 1e-9);
    }

    @Test
    void reachPointsTheArmAtTheTarget() {
        Random random = new Random(3);
        for (int i = 0; i < 500; i++) {
            Vec target = new Vec((random.nextDouble() - 0.5) * 30, (random.nextDouble() - 0.5) * 30, (random.nextDouble() - 0.5) * 30);
            double[] arm = WeaponRig.reach(WeaponRig.LEFT_SHOULDER, target);
            Vec want = target.subtract(WeaponRig.LEFT_SHOULDER);
            want = want.scale(1 / want.length());
            assertEquals(1, ArmAim.armDirection(arm[0], arm[1]).dot(want), 1e-9, "target " + target);
        }
    }

    /** With the weapon in front, the off hand lands on the grip next to the weapon hand (the gap, 2.5 px, plus a little). */
    @Test
    void offHandHoldsTheGripWhenItCanReach() {
        for (double yaw : new double[]{-40, -10, 0, 20}) {
            WeaponRig rig = WeaponRig.solve(yaw, 10, -80, 0, WeaponRig.TwistAxis.Z, NO_BODY, 0.45);
            Vec weaponHand = WeaponRig.RIGHT_SHOULDER.add(Mat3.zyx(rig.arm()[0], rig.arm()[1], rig.arm()[2]).apply(new Vec(0, 10, 0)));
            Vec offHand = WeaponRig.LEFT_SHOULDER.add(rig.offShoulder())
                    .add(ArmAim.armDirection(rig.offArm()[0], rig.offArm()[1]).scale(10));
            double gap = offHand.subtract(weaponHand).length();
            assertTrue(gap < 3.5, "yaw " + yaw + ": hands " + gap + " px apart");
            double behind = offHand.subtract(weaponHand).dot(ArmAim.modelDirection(yaw, 10));
            assertTrue(behind < -1.5, "yaw " + yaw + ": off hand behind the weapon hand, toward the pommel: " + behind);
        }
    }

    /** An overhead windup aims past straight up: the weapon leans back over the head instead of stopping upright. */
    @Test
    void theArmCanLeanBackOverTheHead() {
        WeaponRig rig = WeaponRig.solve(0, -115, -80, 0, WeaponRig.TwistAxis.Z, NO_BODY, 0);
        Vec arm = Mat3.zyx(rig.arm()[0], rig.arm()[1], rig.arm()[2]).apply(new Vec(0, 1, 0));
        assertTrue(arm.y() < -0.8, "up (model +Y is down): " + arm);
        assertTrue(arm.z() > 0.3, "and behind (model -Z is forward): " + arm);
        // The blade turns on past upright too, smoothly: 25 degrees further back is a real turn, one degree a small one.
        Mat3 upright = WeaponRig.solve(0, -90, -80, 0, WeaponRig.TwistAxis.Z, NO_BODY, 0).bladeOrientation(NO_BODY);
        Mat3 back = WeaponRig.solve(0, -115, -80, 0, WeaponRig.TwistAxis.Z, NO_BODY, 0).bladeOrientation(NO_BODY);
        Mat3 nearBack = WeaponRig.solve(0, -114, -80, 0, WeaponRig.TwistAxis.Z, NO_BODY, 0).bladeOrientation(NO_BODY);
        assertTrue(upright.distance(back) > 0.3, "blade leans back: " + upright.distance(back));
        assertTrue(back.distance(nearBack) < 0.05, "continuously: " + back.distance(nearBack));
    }
}
