package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ActiveParrySyncTest {
    private static final AttackTimings TIMINGS = AttackTimings.ofTicks(4, 4, 4);

    @Test
    void matchingConfirmationOnlyRaisesTimerAndPreservesPredictedAction() {
        CombatStateMachine machine = attacking();
        Phase phase = machine.phase();
        long elapsed = machine.phaseElapsedUs();
        CombatStateMachine.PredictionState before = machine.predictionState();

        assertTrue(machine.mergeActiveParry(machine.attackIdentity(), Phase.WINDUP, 9, 2));

        CombatStateMachine.PredictionState after = machine.predictionState();
        assertEquals(7, after.activeParryTicks());
        assertEquals(phase, machine.phase());
        assertEquals(elapsed, machine.phaseElapsedUs());
        assertEquals(before.guardRecovery(), after.guardRecovery());
        assertEquals(before.parryCooldown(), after.parryCooldown());
        assertEquals(before.parryCooldownLeft(), after.parryCooldownLeft());
        assertEquals(before.parriedHits(), after.parriedHits());
        assertEquals(before.staggerAllowsParry(), after.staggerAllowsParry());
        assertEquals(before.fromGuard(), after.fromGuard());
        assertEquals(before.counterFeinted(), after.counterFeinted());
        assertEquals(before.countered(), after.countered());
        assertEquals(before.attackSerial(), after.attackSerial());
    }

    @Test
    void staleConfirmationForAnyAttackIdentityFieldIsIgnored() {
        CombatStateMachine machine = attacking();
        CombatStateMachine.AttackIdentity id = machine.attackIdentity();
        List<CombatStateMachine.AttackIdentity> stale = List.of(
                new CombatStateMachine.AttackIdentity(id.serial() + 1, id.type(), id.heavy(), id.morphed(),
                        id.counterFeinted(), id.variant(), id.mirrored()),
                new CombatStateMachine.AttackIdentity(id.serial(), AttackType.STAB, id.heavy(), id.morphed(),
                        id.counterFeinted(), id.variant(), id.mirrored()),
                new CombatStateMachine.AttackIdentity(id.serial(), id.type(), !id.heavy(), id.morphed(),
                        id.counterFeinted(), id.variant(), id.mirrored()),
                new CombatStateMachine.AttackIdentity(id.serial(), id.type(), id.heavy(), !id.morphed(),
                        id.counterFeinted(), id.variant(), id.mirrored()),
                new CombatStateMachine.AttackIdentity(id.serial(), id.type(), id.heavy(), id.morphed(),
                        !id.counterFeinted(), id.variant(), id.mirrored()),
                new CombatStateMachine.AttackIdentity(id.serial(), id.type(), id.heavy(), id.morphed(),
                        id.counterFeinted(), id.variant() + 1, id.mirrored()),
                new CombatStateMachine.AttackIdentity(id.serial(), id.type(), id.heavy(), id.morphed(),
                        id.counterFeinted(), id.variant(), !id.mirrored()));

        for (CombatStateMachine.AttackIdentity oldAction : stale) {
            assertFalse(machine.mergeActiveParry(oldAction, Phase.WINDUP, 8, 0), oldAction.toString());
        }
    }

    @Test
    void expiredConfirmationAndLongerLocalTimerAreIgnored() {
        CombatStateMachine machine = attacking();
        CombatStateMachine.AttackIdentity id = machine.attackIdentity();
        int current = machine.predictionState().activeParryTicks();

        assertFalse(machine.mergeActiveParry(id, Phase.WINDUP, 2, 2), "delay consumes the confirmed window");
        assertEquals(current, machine.predictionState().activeParryTicks());
        assertFalse(machine.mergeActiveParry(id, Phase.WINDUP, current - 1, 0),
                "a stale shorter timer never shortens local prediction");
        assertEquals(current, machine.predictionState().activeParryTicks());
    }

    @Test
    void crossWindupReleaseConfirmationIsAcceptedButOtherPhasesAreNot() {
        CombatStateMachine release = new CombatStateMachine();
        release.startAttack(AttackType.SLASH, AttackTimings.ofTicks(1, 4, 4));
        release.startActiveParry(5);
        release.tick();
        assertEquals(Phase.RELEASE, release.phase());
        assertTrue(release.mergeActiveParry(release.attackIdentity(), Phase.WINDUP, 7, 1),
                "a delayed windup confirmation can arrive after local release begins");

        CombatStateMachine recovery = new CombatStateMachine();
        recovery.startAttack(AttackType.SLASH, AttackTimings.ofTicks(1, 1, 4));
        recovery.startActiveParry(5);
        recovery.tick();
        recovery.tick();
        assertEquals(Phase.RECOVERY, recovery.phase());
        assertFalse(recovery.mergeActiveParry(recovery.attackIdentity(), Phase.RELEASE, 7, 0));
        assertFalse(recovery.mergeActiveParry(recovery.attackIdentity(), Phase.RECOVERY, 7, 0));

        CombatStateMachine stagger = attacking();
        stagger.stagger(5, false);
        assertFalse(stagger.mergeActiveParry(stagger.attackIdentity(), Phase.WINDUP, 7, 0));
    }

    private static CombatStateMachine attacking() {
        CombatStateMachine machine = new CombatStateMachine();
        machine.startAttack(AttackType.SLASH, TIMINGS);
        machine.startActiveParry(3);
        return machine;
    }
}
