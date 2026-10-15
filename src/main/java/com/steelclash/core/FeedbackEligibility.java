package com.steelclash.core;

/** Pure action-token gate for local presentation feedback; a recipient's token is distinct from the contact attacker. */
public final class FeedbackEligibility {
    private FeedbackEligibility() {
    }

    /** Unknown legacy tokens (-1) retain their old behavior; captured feedback is current only for the same action. */
    public static boolean accepts(int capturedRecipientSerial, int currentRecipientSerial,
                                  boolean defenderParryFollowup) {
        if (capturedRecipientSerial == -1 || capturedRecipientSerial == currentRecipientSerial) {
            return true;
        }
        // A defender can predict the one attack that immediately follows their parry before its feedback packet arrives.
        return defenderParryFollowup && (long) currentRecipientSerial - capturedRecipientSerial == 1L;
    }
}
