package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Contact points, hit locations, flinches and the blade dragging through bodies. */
class HitFeelTest {
    private static final double HEIGHT = 1.8;
    private static final double WIDTH = 0.6;
    private static final double EYES = 1.62;

    @Test
    void bladeEntryIsWhereTheSegmentFirstMeetsTheBox() {
        assertEquals(1 / 3.0, Blade.entry(new Vec(-1, 0.5, 0.5), new Vec(2, 0.5, 0.5), 0, 0, 0, 1, 1, 1), 1e-9);
        assertEquals(0.5, Blade.entry(new Vec(3, 0.5, 0.5), new Vec(-1, 0.5, 0.5), 0, 0, 0, 1, 1, 1), 1e-9,
                "from the other side it enters through the other face");
        assertEquals(0, Blade.entry(new Vec(0.5, 0.5, 0.5), new Vec(3, 0.5, 0.5), 0, 0, 0, 1, 1, 1), 1e-9,
                "a hilt already inside is in from the start");
        assertTrue(Double.isNaN(Blade.entry(new Vec(-1, 2, 0.5), new Vec(2, 2, 0.5), 0, 0, 0, 1, 1, 1)));
        Blade.Segment blade = new Blade.Segment(new Vec(1, 1, 1), new Vec(3, 1, 1));
        assertEquals(new Vec(2.5, 1, 1), blade.along(0.75));
    }

    @Test
    void locationsFollowABipedsProportions() {
        assertEquals(HitLocation.Region.HEAD, at(0, 1.7, 0.3, 0));
        assertEquals(HitLocation.Region.TORSO, at(0, 1.1, 0.3, 0), "chest height, middle of the front");
        assertEquals(HitLocation.Region.LEFT_ARM, at(0.29, 1.1, 0, 0), "a body facing +Z (yaw 0) has its left at +X");
        assertEquals(HitLocation.Region.RIGHT_ARM, at(-0.29, 1.1, 0, 0));
        assertEquals(HitLocation.Region.LEFT_LEG, at(0.1, 0.3, 0.3, 0));
        assertEquals(HitLocation.Region.RIGHT_LEG, at(-0.1, 0.3, 0.3, 0));
        assertEquals(HitLocation.Region.TORSO, at(0.2, 1.1, 0, 0), "inside the arm line it is still the chest");
        assertEquals(HitLocation.Region.LEFT_ARM, at(0, 1.1, 0.29, 90), "facing -X (yaw 90), the left is +Z");
        assertEquals(HitLocation.Region.RIGHT_ARM, at(0, 1.1, -0.29, 90));
        assertEquals(HitLocation.Region.HEAD, HitLocation.classify(0, 1.8, 0, 0, 1.95, WIDTH, 1.74), "a zombie's head");
        assertEquals(HitLocation.Region.TORSO, HitLocation.classify(0, 1.4, 0, 0, 1.95, WIDTH, 1.74));
    }

    @Test
    void bodyFrameComponents() {
        assertEquals(1, HitLocation.forward(0, 1, 0), 1e-9);
        assertEquals(1, HitLocation.left(1, 0, 0), 1e-9);
        assertEquals(1, HitLocation.forward(-1, 0, 90), 1e-9);
        assertEquals(1, HitLocation.left(0, 1, 90), 1e-9);
        assertEquals(0, HitLocation.left(0, 1, 0), 1e-9);
    }

    @Test
    void thePartTheBladeMetIsThrownTheWayItTravelled() {
        HitReaction arm = HitReaction.of(HitLocation.Region.RIGHT_ARM, -1, 0, 0, 1);
        assertTrue(arm.parts().get("rightArm")[0] > 0, "driven back, the arm swings back (xRot +)");
        assertFalse(arm.parts().containsKey("leftArm"));
        HitReaction across = HitReaction.of(HitLocation.Region.LEFT_ARM, 0, -1, 0, 1);
        assertTrue(across.parts().get("leftArm")[2] > 0, "driven to the body's right, the arm swings right (zRot +)");
        HitReaction head = HitReaction.of(HitLocation.Region.HEAD, 0, 1, 0, 1);
        assertTrue(head.parts().get("head")[1] < 0, "a head knocked left turns left (yRot -)");
        HitReaction overhead = HitReaction.of(HitLocation.Region.HEAD, 0, 0, 1, 1);
        assertTrue(overhead.parts().get("head")[0] > 0, "a blow from above nods the head down");
        assertEquals(1, overhead.forward(), 1e-9, "with nothing sideways, the body folds forward");
        HitReaction leg = HitReaction.of(HitLocation.Region.LEFT_LEG, -1, 0, 0, 1);
        assertTrue(leg.parts().get("leftLeg")[0] > 0);
        HitReaction chest = HitReaction.of(HitLocation.Region.TORSO, -0.6, 0.8, 0, 2);
        assertEquals(20, chest.tilt(), 1e-9, "the chest tips the body most, scaled by strength");
        assertEquals(-0.6, chest.forward(), 1e-9);
        assertEquals(0.8, chest.left(), 1e-9);
    }

    @Test
    void theFlinchSnapsThenFades() {
        assertEquals(0, HitReaction.envelope(0), 1e-9);
        assertEquals(1, HitReaction.envelope(HitReaction.RISE), 1e-9);
        assertTrue(HitReaction.envelope(0.2) < HitReaction.envelope(0.1));
        assertEquals(0, HitReaction.envelope(HitReaction.LENGTH));
        assertEquals(0, HitReaction.envelope(-0.1));
    }

    @Test
    void tiltAxisTipsTheTopTowardTheDirection() {
        // Renderer frame after the yaw turn: forward is -Z, the body's left is -X.
        assertTipsToward(HitReaction.tiltAxis(1, 0), new Vec(0, 0, -1));
        assertTipsToward(HitReaction.tiltAxis(-1, 0), new Vec(0, 0, 1));
        assertTipsToward(HitReaction.tiltAxis(0, 1), new Vec(-1, 0, 0));
        assertTipsToward(HitReaction.tiltAxis(0, -1), new Vec(1, 0, 0));
        assertNull(HitReaction.tiltAxis(0, 0));
    }

    @Test
    void aBladeDraggingThroughABodySlowsWithoutJumpingBack() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(2, 8, 4));
        assertFalse(m.slowRelease(50_000), "not during the windup");
        m.tick();
        m.tick();
        m.tick();
        m.tick(); // two ticks into the release
        assertEquals(Phase.RELEASE, m.phase());
        double progress = m.phaseProgress(0);
        long remaining = m.phaseDurationUs() - m.phaseElapsedUs();
        assertTrue(m.slowRelease(50_000));
        assertEquals(progress, m.phaseProgress(0), 1e-6, "the blade stays where it was");
        assertEquals(remaining + 50_000, m.phaseDurationUs() - m.phaseElapsedUs(), 1, "the rest takes 50 ms longer");
        assertTrue(m.slowRelease(10_000_000));
        assertEquals(2 * 8 * AttackTimings.TICK_US, m.phaseDurationUs(), "never past twice the release");
        assertFalse(m.slowRelease(50_000), "already as slow as it gets");
        assertFalse(m.slowRelease(0));
    }

    @Test
    void theOwnersPredictionTakesTheSlowerReleaseWhereItsBladeIs() {
        CombatStateMachine server = new CombatStateMachine();
        CombatStateMachine client = new CombatStateMachine();
        for (CombatStateMachine m : new CombatStateMachine[]{server, client}) {
            m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(2, 8, 4));
        }
        for (int tick = 0; tick < 3; tick++) {
            server.tick();
        }
        for (int tick = 0; tick < 4; tick++) {
            client.tick(); // the owner's prediction runs a tick ahead of the server
        }
        server.slowRelease(50_000);
        double clientProgress = client.phaseProgress(0);
        assertFalse(client.mergeRelease(new CombatStateMachine.AttackIdentity(client.attackSerial() + 1, AttackType.SLASH,
                false, false, false, 0, false), Phase.RELEASE, server.phaseDurationUs()), "another attack's release");
        assertFalse(client.mergeRelease(client.attackIdentity(), Phase.RECOVERY, server.phaseDurationUs()),
                "the server has moved on");
        assertTrue(client.mergeRelease(client.attackIdentity(), Phase.RELEASE, server.phaseDurationUs()));
        assertEquals(server.phaseDurationUs(), client.phaseDurationUs(), "the release is as long as the server's");
        assertEquals(clientProgress, client.phaseProgress(0), 1e-6, "and the blade stays where the owner sees it");
        assertFalse(client.mergeRelease(client.attackIdentity(), Phase.RELEASE, server.phaseDurationUs() - 1),
                "a release never shortens");
        assertTrue(client.mergeRelease(client.attackIdentity(), Phase.RELEASE, 10_000_000));
        assertEquals(2 * 8 * AttackTimings.TICK_US, client.phaseDurationUs(), "nor grows past twice its length");
    }

    @Test
    void aThwackAfterTheReleaseCountsTheSlowerRelease() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(2, 8, 4));
        m.tick();
        m.tick();
        m.tick(); // a tick into the release
        assertTrue(m.slowRelease(100_000));
        long release = m.phaseDurationUs();
        while (m.phase() == Phase.RELEASE) {
            m.tick();
        }
        long intoRecovery = m.phaseElapsedUs();
        assertTrue(m.thwack(1_000_000, 0.9));
        assertEquals(Math.round(0.1 * release) + intoRecovery, m.phaseElapsedUs(),
                "the time since contact is measured on the release as it actually ran");
    }

    private static HitLocation.Region at(double dx, double dy, double dz, double yaw) {
        return HitLocation.classify(dx, dy, dz, yaw, HEIGHT, WIDTH, EYES);
    }

    /** Rodrigues: a small right-handed turn of "up" about {@code axis} moves it toward {@code expected}. */
    private static void assertTipsToward(double[] axis, Vec expected) {
        double angle = 0.1;
        Vec k = new Vec(axis[0], axis[1], axis[2]);
        Vec up = new Vec(0, 1, 0);
        Vec cross = new Vec(k.y() * up.z() - k.z() * up.y(), k.z() * up.x() - k.x() * up.z(), k.x() * up.y() - k.y() * up.x());
        Vec turned = up.scale(Math.cos(angle)).add(cross.scale(Math.sin(angle))).add(k.scale(k.dot(up) * (1 - Math.cos(angle))));
        Vec moved = turned.subtract(up);
        assertTrue(moved.dot(expected) > 0.09, "tipped toward " + expected + ", moved " + moved);
    }
}
