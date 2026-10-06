package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class GestureTest {
    private static final float T = 5f;
    private static final Gesture.Mapping DEFAULT = Gesture.Mapping.DEFAULT;

    @Test
    void smallMovementIsNotAGestureYet() {
        assertNull(Gesture.classify(3f, -4f, T, DEFAULT));
    }

    @Test
    void horizontalDragsSlash() {
        assertEquals(new Gesture.Result(AttackType.SLASH, true), Gesture.classify(-8f, 2f, T, DEFAULT), "drag left: from the left");
        assertEquals(new Gesture.Result(AttackType.SLASH, false), Gesture.classify(8f, -2f, T, DEFAULT), "drag right: from the right");
    }

    @Test
    void verticalDragsOverheadOrStab() {
        assertEquals(AttackType.OVERHEAD, Gesture.classify(1f, -7f, T, DEFAULT).type(), "drag up: overhead");
        assertEquals(AttackType.STAB, Gesture.classify(-1f, 7f, T, DEFAULT).type(), "drag down: stab");
    }

    @Test
    void theDominantAxisWins() {
        assertEquals(Gesture.Direction.RIGHT, Gesture.direction(9f, 6f, T));
        assertEquals(Gesture.Direction.UP, Gesture.direction(6f, -9f, T));
    }

    @Test
    void mappingIsCustomizable() {
        // "Follow the blade": drag left swings right to left, drag down chops an overhead, drag up stabs, right kicks.
        Gesture.Mapping custom = new Gesture.Mapping(Gesture.Action.SLASH_FROM_RIGHT, Gesture.Action.KICK,
                Gesture.Action.STAB, Gesture.Action.OVERHEAD);
        assertEquals(new Gesture.Result(AttackType.SLASH, false), Gesture.classify(-8f, 0f, T, custom));
        assertEquals(AttackType.KICK, Gesture.classify(8f, 0f, T, custom).type());
        assertEquals(AttackType.STAB, Gesture.classify(0f, -8f, T, custom).type());
        assertEquals(AttackType.OVERHEAD, Gesture.classify(0f, 8f, T, custom).type());
    }

    @Test
    void aDisabledDirectionIsIgnored() {
        Gesture.Mapping noUp = new Gesture.Mapping(Gesture.Action.SLASH_FROM_LEFT, Gesture.Action.SLASH_FROM_RIGHT,
                Gesture.Action.NONE, Gesture.Action.STAB);
        assertNull(Gesture.classify(0f, -8f, T, noUp));
    }
}
