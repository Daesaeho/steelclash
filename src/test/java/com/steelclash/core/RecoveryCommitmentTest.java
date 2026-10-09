package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RecoveryCommitmentTest {
    private static final AttackTimings TIMINGS = AttackTimings.ofTicks(2, 2, 8);

    @Test
    void landedNonweaponActionsMustCompleteRecovery() {
        for (AttackType type : new AttackType[]{AttackType.JAB, AttackType.KICK, AttackType.SPECIAL, AttackType.THROW}) {
            CombatStateMachine machine = new CombatStateMachine();
            assertTrue(machine.startAttack(type, TIMINGS));
            machine.tick();
            machine.tick();
            assertEquals(Phase.RELEASE, machine.phase());
            machine.allowCombo();
            machine.tick();
            machine.tick();
            assertEquals(Phase.RECOVERY, machine.phase());
            machine.allowCombo();
            assertFalse(machine.isComboAllowed(), type + " must retain its recovery after landing");
            assertFalse(machine.startAttack(AttackType.SLASH, TIMINGS), type + " must not chain a slash early");
            for (int tick = 0; tick < 8; tick++) {
                machine.tick();
            }
            assertTrue(machine.startAttack(AttackType.SLASH, TIMINGS), type + " can attack after recovery ends");
        }
    }

    @Test
    void weaponThwackStillAllowsACombo() {
        CombatStateMachine machine = new CombatStateMachine();
        assertTrue(machine.startAttack(AttackType.SLASH, TIMINGS));
        machine.tick();
        machine.tick();
        assertTrue(machine.thwack(8 * (int) AttackTimings.TICK_US, 0.25));
        machine.allowCombo();
        assertTrue(machine.startAttack(AttackType.OVERHEAD, TIMINGS));
    }
}
