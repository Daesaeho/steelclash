package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WeaponPosePolicyTest {
    @Test void replacedWeaponsCannotKeepAnAttackOrGuardPose() {
        for (Phase phase : new Phase[]{Phase.WINDUP,Phase.RELEASE,Phase.RECOVERY,Phase.PARRY,Phase.GUARD_RECOVERY})
            assertTrue(WeaponPosePolicy.stale(phase,AttackType.SLASH,true,false,false),phase.toString());
        assertFalse(WeaponPosePolicy.stale(Phase.STAGGER,AttackType.SLASH,true,false,true),"a disarmed fighter still recoils");
        assertFalse(WeaponPosePolicy.stale(Phase.IDLE,AttackType.SLASH,true,false,false));
    }
    @Test void unknownSnapshotsAndTheSameItemRemainValid() {
        assertFalse(WeaponPosePolicy.stale(Phase.WINDUP,AttackType.SLASH,false,false,false));
        assertFalse(WeaponPosePolicy.stale(Phase.RELEASE,AttackType.SLASH,true,true,false));
    }
    @Test void aReleasedThrowKeepsItsEmptyHandFollowThrough() {
        assertFalse(WeaponPosePolicy.stale(Phase.RELEASE,AttackType.THROW,true,false,true));
        assertFalse(WeaponPosePolicy.stale(Phase.RECOVERY,AttackType.THROW,true,false,true));
        assertTrue(WeaponPosePolicy.stale(Phase.WINDUP,AttackType.THROW,true,false,true),"removing it before release is a swap");
        assertTrue(WeaponPosePolicy.stale(Phase.RECOVERY,AttackType.THROW,true,false,false),"a new weapon must not inherit the throw");
    }
}
