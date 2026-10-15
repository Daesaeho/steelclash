package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeedbackEligibilityTest {
    private static final AttackTimings TIMINGS = AttackTimings.ofTicks(3, 2, 4);

    @Test
    void recipientTokenIsIndependentAndRejectsFeedbackAfterTheirOwnNextAttack() {
        int capturedAttackerSerial = 18;
        int defenderRecipientSerial = 7;

        assertTrue(capturedAttackerSerial != defenderRecipientSerial
                        && FeedbackEligibility.accepts(defenderRecipientSerial, 7, false),
                "a parry packet carrying the attacker's serial still matches the defender's captured action token");
        assertTrue(FeedbackEligibility.accepts(defenderRecipientSerial, 8, true),
                "the defender's immediate predicted riposte remains eligible for parry hit-stop");
        assertFalse(FeedbackEligibility.accepts(defenderRecipientSerial, 8, false),
                "a delayed packet cannot freeze the defender after their next attack starts");
        assertFalse(FeedbackEligibility.accepts(capturedAttackerSerial, 19, false),
                "the attacker does not inherit the defender's predicted-counter exception");
        assertTrue(FeedbackEligibility.accepts(-1, capturedAttackerSerial, false),
                "legacy/manual feedback without a recipient token keeps the previous behavior");
    }

    @Test
    void predictedRiposteKeepsTheParryFeedbackEligibleUntilItsStateArrives() {
        CombatStateMachine client = new CombatStateMachine();
        assertTrue(client.startParry(12, 4, 5));
        client.parrySucceeded(10);
        int capturedDefenderSerial = client.attackSerial();

        // The defender can start a riposte locally before the matching server state snapshot arrives.
        assertTrue(client.startAttack(AttackType.SLASH, TIMINGS));
        assertTrue(client.isFromGuard());
        assertTrue(FeedbackEligibility.accepts(capturedDefenderSerial, client.attackSerial(), client.isFromGuard()),
                "a valid parry packet arriving before state reconciliation still freezes the defender's riposte");

        client.cancel();
        assertTrue(client.startAttack(AttackType.OVERHEAD, TIMINGS));
        assertFalse(client.isFromGuard());
        assertFalse(FeedbackEligibility.accepts(capturedDefenderSerial, client.attackSerial(), client.isFromGuard()),
                "a later neutral action does not inherit the old parry's exception");
    }
}
