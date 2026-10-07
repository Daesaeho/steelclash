package com.steelclash.ai;

import com.steelclash.core.AttackType;
import com.steelclash.core.BotStyle;
import com.steelclash.core.OpponentMemory;
import com.steelclash.core.SwingTurn;
import org.jetbrains.annotations.Nullable;

/** A mob's bot-brain state, kept in its combat data. Server only. */
public class BrainState {
    /** How the bot plans to answer the opponent's current windup. */
    public enum Answer {
        NONE, PARRY, LATE_PARRY, COUNTER,
        /** Interrupt a slow heavy at close range with a jab. */
        JAB
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

    /** Current footwork while holding back, and when to pick a new one. */
    public BotStyle.Footwork footwork = BotStyle.Footwork.CIRCLE;
    public int footworkUntil;
    /** Backing off from an attack it decided not to parry, until this tick. */
    public int evadeUntil;
    /** Shield held up until this tick (mobs with a shield block instead of parrying). */
    public int shieldDownAt;
    /** Out of breath: holds back until stamina recovers. */
    public boolean lowStamina;

    public Answer answer = Answer.NONE;
    public int answeredAttacker = -1;
    public int answeredSerial = -1;
    /** The attacker this bot is countering (its counter-feints follow that attacker's morphs), or -1. */
    public int counterTarget = -1;
    /** The bot's own attack that is the counter (its attack serial). */
    public int counterSerial = -1;
    /** Attack serial whose combo chance was already rolled. */
    public int comboRolledFor = -1;
    /** Accel or drag planned for the current attack (turning the head during the release). */
    public SwingTurn.Trick trick = SwingTurn.Trick.NONE;
}
