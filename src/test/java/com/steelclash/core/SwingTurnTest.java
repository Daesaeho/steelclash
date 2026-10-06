package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SwingTurnTest {
    @Test
    void approachIsCappedAndTakesTheShortWay() {
        assertEquals(18f, SwingTurn.approachYaw(0f, 90f, 18f), 1e-6);
        assertEquals(-18f, SwingTurn.approachYaw(0f, -90f, 18f), 1e-6);
        assertEquals(10f, SwingTurn.approachYaw(0f, 10f, 18f), 1e-6, "small turns pass untouched");
        assertEquals(188f, SwingTurn.approachYaw(170f, -170f, 18f), 1e-6, "across the ±180 seam: the short way");
        assertEquals(90f, SwingTurn.approachYaw(0f, 90f, 0f), 1e-6, "0 = no cap");
        assertEquals(-12f, SwingTurn.approachPitch(0f, -40f, 12f), 1e-6);
    }

    @Test
    void travelFollowsTheArc() {
        assertEquals(-1, SwingTurn.travel(ArcPath.horizontal(140)), "right to left: yaw falls");
        assertEquals(1, SwingTurn.travel(ArcPath.horizontal(140).mirrored()));
        assertEquals(0, SwingTurn.travel(ArcPath.vertical()));
        assertEquals(0, SwingTurn.travel(ArcPath.thrust()));
    }

    @Test
    void accelTurnsWithTheSwingAndDragAgainstIt() {
        int rightToLeft = -1;
        assertEquals(-30, SwingTurn.trickYaw(SwingTurn.Trick.ACCEL, rightToLeft, 0.5, 60), 1e-9);
        assertEquals(30, SwingTurn.trickYaw(SwingTurn.Trick.DRAG, rightToLeft, 0.5, 60), 1e-9);
        assertEquals(0, SwingTurn.trickYaw(SwingTurn.Trick.DRAG, 0, 0.5, 60), 1e-9, "no trick on overheads");
        assertEquals(0, SwingTurn.trickYaw(SwingTurn.Trick.NONE, 1, 0.5, 60), 1e-9);
    }
}
