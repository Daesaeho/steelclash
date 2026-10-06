package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BotLogicTest {
    // ---- attack tokens

    @Test
    void tokensLimitAttackersPerTarget() {
        AttackTokens tokens = new AttackTokens();
        assertTrue(tokens.acquire(1, 10, 2, id -> true));
        assertTrue(tokens.acquire(1, 11, 2, id -> true));
        assertFalse(tokens.acquire(1, 12, 2, id -> true), "third attacker must wait");
        assertTrue(tokens.acquire(2, 12, 2, id -> true), "tokens are per target");
        assertTrue(tokens.acquire(1, 10, 2, id -> true), "a holder keeps its token");
    }

    @Test
    void inactiveHoldersLoseTheirToken() {
        AttackTokens tokens = new AttackTokens();
        tokens.acquire(1, 10, 1, id -> true);
        assertFalse(tokens.acquire(1, 11, 1, id -> true));
        assertTrue(tokens.acquire(1, 11, 1, id -> id != 10), "10 stopped attacking: its token frees up");
        assertFalse(tokens.holds(1, 10));
    }

    @Test
    void releaseAllFreesEveryTarget() {
        AttackTokens tokens = new AttackTokens();
        tokens.acquire(1, 10, 1, id -> true);
        tokens.acquire(2, 10, 1, id -> true);
        tokens.releaseAll(10);
        assertEquals(0, tokens.holderCount(1));
        assertEquals(0, tokens.holderCount(2));
    }

    // ---- skill

    @Test
    void harderBotsReactFasterAndCrowdMore() {
        assertTrue(BotSkill.HARD.reactionTicks() < BotSkill.NORMAL.reactionTicks());
        assertTrue(BotSkill.NORMAL.reactionTicks() < BotSkill.EASY.reactionTicks());
        assertTrue(BotSkill.HARD.attackers() > BotSkill.EASY.attackers());
        assertEquals(BotSkill.EASY, BotSkill.forDifficulty(0));
        assertEquals(BotSkill.HARD, BotSkill.forDifficulty(3));
    }

    @Test
    void reactionNeedsEnoughWindup() {
        assertFalse(BotSkill.EASY.canReact(7), "a dagger's 7-tick windup beats an easy bot");
        assertTrue(BotSkill.HARD.canReact(7));
    }

    // ---- opponent memory

    @Test
    void learnsAttackHabits() {
        OpponentMemory memory = new OpponentMemory();
        memory.track(5);
        int serial = 0;
        for (AttackType type : new AttackType[]{AttackType.SLASH, AttackType.STAB, AttackType.SLASH, AttackType.SLASH}) {
            swing(memory, type, ++serial);
        }
        assertEquals(AttackType.SLASH, memory.habit());
    }

    @Test
    void mixedAttacksAreNoHabit() {
        OpponentMemory memory = new OpponentMemory();
        memory.track(5);
        int serial = 0;
        for (AttackType type : new AttackType[]{AttackType.SLASH, AttackType.STAB, AttackType.OVERHEAD, AttackType.SLASH}) {
            swing(memory, type, ++serial);
        }
        assertNull(memory.habit());
    }

    @Test
    void noticesFeintsButNotStaggersOrNewAttacks() {
        OpponentMemory memory = new OpponentMemory();
        memory.track(5);
        // feint: windup → idle, same attack
        memory.observe(Phase.WINDUP, AttackType.SLASH, 1, false);
        memory.observe(Phase.IDLE, AttackType.SLASH, 1, false);
        // parry-cancel: windup → parry
        memory.observe(Phase.WINDUP, AttackType.STAB, 2, false);
        memory.observe(Phase.PARRY, AttackType.STAB, 2, true);
        assertEquals(2, memory.feints());
        assertTrue(memory.isFeinter());
        // flinched out of a windup is not a feint
        memory.observe(Phase.WINDUP, AttackType.SLASH, 3, false);
        memory.observe(Phase.STAGGER, AttackType.SLASH, 3, false);
        assertEquals(2, memory.feints());
    }

    @Test
    void countsGuardTimeAndResetsOnNewOpponent() {
        OpponentMemory memory = new OpponentMemory();
        memory.track(5);
        for (int i = 0; i < 10; i++) {
            memory.observe(Phase.PARRY, AttackType.SLASH, 0, true);
        }
        assertEquals(10, memory.guardTicks());
        memory.observe(Phase.IDLE, AttackType.SLASH, 0, false);
        assertEquals(0, memory.guardTicks());
        memory.observe(Phase.WINDUP, AttackType.SLASH, 1, false);
        memory.observe(Phase.IDLE, AttackType.SLASH, 1, false);
        memory.track(6);
        assertEquals(0, memory.feints(), "new opponent, clean slate");
    }

    private static void swing(OpponentMemory memory, AttackType type, int serial) {
        memory.observe(Phase.WINDUP, type, serial, false);
        memory.observe(Phase.RELEASE, type, serial, false);
        memory.observe(Phase.RECOVERY, type, serial, false);
        memory.observe(Phase.IDLE, type, serial, false);
    }
}
