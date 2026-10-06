package com.steelclash.ai;

import com.steelclash.core.AttackType;
import com.steelclash.core.OpponentMemory;
import org.jetbrains.annotations.Nullable;

/** A mob's bot-brain state, kept in its combat data. Server only. */
public class BrainState {
    /** How the bot plans to answer the opponent's current windup. */
    public enum Answer {
        NONE, PARRY, LATE_PARRY, COUNTER
    }

    public final OpponentMemory memory = new OpponentMemory();

    /** True while the bot should hold back (no attack token, or catching its breath after attacking). */
    public boolean wantsSpace;
    /** Ticks to wait (idle) before attacking again. */
    public int cooldown;
    /** Circle clockwise (+1) or counter-clockwise (-1). */
    public int strafeDirection = 1;
    public int strafeFlipAt;

    /** Feint the current windup at this tick of it (-1 = no feint planned). */
    public int feintAt = -1;
    /** Morph the current windup at this tick of it into {@link #morphTo} (-1 = no morph planned). */
    public int morphAt = -1;
    @Nullable
    public AttackType morphTo;

    public Answer answer = Answer.NONE;
    public int answeredAttacker = -1;
    public int answeredSerial = -1;
    /** Attack serial whose combo chance was already rolled. */
    public int comboRolledFor = -1;
}
