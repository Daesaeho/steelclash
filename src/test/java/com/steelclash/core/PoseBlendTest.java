package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PoseBlendTest {
    private static boolean continuous(int prevSerial, Phase prev, int serial, Phase now) {
        return PoseBlend.continuous(prevSerial, AttackType.SLASH, false, prev, serial, AttackType.SLASH, false, now);
    }

    @Test
    void anAttacksOwnPhasesNeedNoBlend() {
        assertTrue(continuous(1, Phase.WINDUP, 1, Phase.WINDUP));
        assertTrue(continuous(1, Phase.WINDUP, 1, Phase.RELEASE));
        assertTrue(continuous(1, Phase.RELEASE, 1, Phase.RECOVERY));
        assertTrue(continuous(1, Phase.RECOVERY, 1, Phase.IDLE), "a recovery eases out by itself");
        assertTrue(continuous(1, Phase.PARRY, 1, Phase.GUARD_RECOVERY));
        assertTrue(continuous(1, Phase.STAGGER, 1, Phase.IDLE));
    }

    @Test
    void handOversBlend() {
        assertFalse(continuous(1, Phase.RELEASE, 2, Phase.WINDUP), "a combo pressed in the release");
        assertFalse(continuous(1, Phase.RECOVERY, 2, Phase.WINDUP), "a combo from the recovery");
        assertFalse(continuous(1, Phase.PARRY, 2, Phase.WINDUP), "a riposte");
        assertFalse(continuous(1, Phase.RELEASE, 1, Phase.STAGGER), "an interrupted swing");
        assertFalse(continuous(1, Phase.WINDUP, 1, Phase.PARRY), "a windup cancelled into a parry");
        assertFalse(continuous(1, Phase.WINDUP, 1, Phase.IDLE), "a feint");
        assertFalse(PoseBlend.continuous(1, AttackType.SLASH, false, Phase.WINDUP, 1, AttackType.STAB, false, Phase.WINDUP),
                "a morph to another attack");
        assertFalse(PoseBlend.continuous(1, AttackType.SLASH, false, Phase.WINDUP, 1, AttackType.SLASH, true, Phase.WINDUP),
                "a counter-feint to the other side");
    }

    @Test
    void anglesBlendTheShortWayRound() {
        assertEquals(175 + 2.5, PoseBlend.angle(0.25, 175, -175), 1e-9, "175 to -175 is 10 degrees, not 350");
        assertEquals(-10, PoseBlend.angle(0.5, 0, -20), 1e-9);
        assertEquals(1.0, PoseBlend.ease(2), 1e-9);
        assertEquals(0.5, PoseBlend.ease(0.5), 1e-9);
    }

    @Test
    void offsetsBlendPartByPartWithMissingPartsAtZero() {
        Map<String, double[]> out = PoseBlend.offsets(0.5, Map.of("body", new double[]{10, 20, 30}),
                Map.of("head", new double[]{4, 0, 0}));
        assertArrayEquals(new double[]{5, 10, 15}, out.get("body"), 1e-9);
        assertArrayEquals(new double[]{2, 0, 0}, out.get("head"), 1e-9);
    }
}
