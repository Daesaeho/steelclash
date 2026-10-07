package com.steelclash.core;

/**
 * How well a mob fights, by difficulty. Modelled on Chivalry 2's bots: better bots react faster, mix up their attacks
 * and punish habits, but every bot can be read and beaten.
 *
 * @param reactionTicks  an attack's windup must have been visible this long before the bot can parry it
 * @param parryChance    chance to parry an attack it can react to
 * @param feintChance    chance an attack is a feint
 * @param morphChance    chance an attack morphs into another type mid-windup
 * @param heavyChance    chance an attack is a heavy
 * @param comboChance    chance to combo after a landed hit
 * @param counterChance  chance to counter (instead of parry) an attack type the player keeps repeating
 * @param kickAfterTicks kick a target that has been guarding this long (0 = never)
 * @param attackers      how many mobs may attack the same target at once (attack tokens)
 * @param swingTrickChance chance a slash is accelerated or dragged by turning during the release
 */
public record BotSkill(int reactionTicks, double parryChance, double feintChance, double morphChance, double heavyChance,
                       double comboChance, double counterChance, int kickAfterTicks, int attackers, double swingTrickChance) {

    public static final BotSkill EASY = new BotSkill(8, 0.15, 0.0, 0.0, 0.15, 0.10, 0.0, 0, 1, 0.0);
    public static final BotSkill NORMAL = new BotSkill(6, 0.35, 0.10, 0.05, 0.25, 0.30, 0.25, 30, 2, 0.15);
    public static final BotSkill HARD = new BotSkill(4, 0.55, 0.20, 0.10, 0.30, 0.50, 0.50, 18, 3, 0.35);

    /** @param difficulty 0 = peaceful, 1 = easy, 2 = normal, 3 = hard */
    public static BotSkill forDifficulty(int difficulty) {
        return switch (difficulty) {
            case 0, 1 -> EASY;
            case 2 -> NORMAL;
            default -> HARD;
        };
    }

    public BotSkill withParryChance(double chance) {
        if (Double.compare(chance, parryChance) == 0) {
            return this;
        }
        return new BotSkill(reactionTicks, chance, feintChance, morphChance, heavyChance, comboChance, counterChance,
                kickAfterTicks, attackers, swingTrickChance);
    }

    public BotSkill withAttackers(int count) {
        count = Math.max(1, count);
        if (count == attackers) {
            return this;
        }
        return new BotSkill(reactionTicks, parryChance, feintChance, morphChance, heavyChance, comboChance, counterChance,
                kickAfterTicks, count, swingTrickChance);
    }

    /** Has the bot seen enough of this windup to react to it? */
    public boolean canReact(int windupTicksSeen) {
        return windupTicksSeen >= reactionTicks;
    }
}
