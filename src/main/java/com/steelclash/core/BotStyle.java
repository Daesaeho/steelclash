package com.steelclash.core;

import java.util.Locale;

/**
 * How a kind of mob moves and fights, so a horde doesn't move in lockstep. Assigned per entity type through the
 * {@code steelclash:bot_style} data map.
 *
 * @param cooldownMult  scales the pause between attacks (lower = more relentless)
 * @param holdExtra     how far outside its reach it hangs back while waiting, blocks
 * @param strafeSpeed   how fast it circles
 * @param lungeRange    how far beyond reach it will lunge in to start an attack (0 = never)
 * @param evadeChance   chance to back off from an attack it won't parry (instead of eating it)
 * @param footwork      relative weights for {@link Footwork} moves while holding back
 */
public record BotStyle(double cooldownMult, double holdExtra, double strafeSpeed, double lungeRange, double evadeChance,
                       double[] footwork) {
    /** Footwork while holding back. */
    public enum Footwork {
        CIRCLE, HOLD, BACKPEDAL, FEINT_STEP
    }

    /** Zombies: shuffle straight in, rarely give ground. */
    public static final BotStyle SHAMBLER = new BotStyle(0.6, 0.4, 0.25, 0.0, 0.0, new double[]{0.2, 0.5, 0.1, 0.2});
    /** Skeletons, piglins: measured, keep distance, circle. The default. */
    public static final BotStyle DUELIST = new BotStyle(1.0, 1.2, 0.45, 0.6, 0.25, new double[]{0.45, 0.2, 0.15, 0.2});
    /** Vindicators, piglin brutes: aggressive, lunge in, bait with steps. */
    public static final BotStyle RUSHER = new BotStyle(0.7, 0.8, 0.5, 1.4, 0.15, new double[]{0.3, 0.1, 0.1, 0.5});
    /** Ravagers, hoglins, golems: plant and smash. */
    public static final BotStyle BRUTE = new BotStyle(1.3, 1.0, 0.2, 0.8, 0.0, new double[]{0.2, 0.7, 0.1, 0.0});
    /** Spiders: fast, hit and run. */
    public static final BotStyle SKIRMISHER = new BotStyle(0.8, 1.6, 0.6, 1.8, 0.4, new double[]{0.5, 0.0, 0.3, 0.2});

    public static BotStyle byName(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "shambler" -> SHAMBLER;
            case "rusher" -> RUSHER;
            case "brute" -> BRUTE;
            case "skirmisher" -> SKIRMISHER;
            default -> DUELIST;
        };
    }

    /** Picks a footwork move from the style's weights, given a uniform roll in [0, 1). */
    public Footwork chooseFootwork(double roll) {
        double total = 0;
        for (double w : footwork) {
            total += w;
        }
        double target = roll * total;
        double acc = 0;
        Footwork[] moves = Footwork.values();
        for (int i = 0; i < moves.length && i < footwork.length; i++) {
            acc += footwork[i];
            if (target < acc) {
                return moves[i];
            }
        }
        return Footwork.CIRCLE;
    }

    /**
     * Small per-mob variation so a group doesn't move in lockstep: a deterministic offset in [-1, 1] from a seed
     * (e.g. the mob's UUID hash).
     */
    public static double jitter(long seed) {
        long mixed = seed * 0x9E3779B97F4A7C15L;
        mixed ^= (mixed >>> 31);
        return ((mixed & 0xFFFF) / 65535.0) * 2 - 1;
    }
}
