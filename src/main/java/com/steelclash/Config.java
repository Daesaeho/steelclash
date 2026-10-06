package com.steelclash;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public enum DisarmMode {
        /** Weapon is knocked out of the hand and dropped in front of the defender (Chivalry 2 behavior). */
        DROP,
        /** Weapon stays in the inventory but can't be used for a few seconds. */
        HOLSTER
    }

    public static final ModConfigSpec.EnumValue<DisarmMode> DISARM_MODE = BUILDER
            .comment("What happens when a parry is made with no stamina left")
            .defineEnum("disarmMode", DisarmMode.DROP);

    static {
        BUILDER.push("stamina");
    }

    public static final ModConfigSpec.DoubleValue MAX_STAMINA = BUILDER
            .defineInRange("max", 100.0, 1.0, 10_000.0);
    public static final ModConfigSpec.DoubleValue STAMINA_REGEN_PER_SECOND = BUILDER
            .defineInRange("regenPerSecond", 30.0, 0.0, 10_000.0);
    public static final ModConfigSpec.IntValue STAMINA_REGEN_DELAY_TICKS = BUILDER
            .comment("Ticks after spending stamina before it starts regenerating")
            .defineInRange("regenDelayTicks", 25, 0, 1200);
    public static final ModConfigSpec.DoubleValue VANILLA_MELEE_STAMINA_DAMAGE = BUILDER
            .comment("Stamina lost when parrying or shield-blocking an attack that isn't a Steel Clash swing (vanilla mob melee)")
            .defineInRange("vanillaMeleeStaminaDamage", 15.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue PROJECTILE_STAMINA_DAMAGE = BUILDER
            .comment("Stamina lost when a shield blocks a projectile")
            .defineInRange("projectileStaminaDamage", 5.0, 0.0, 1000.0);

    static {
        BUILDER.pop().push("defense");
    }

    public static final ModConfigSpec.IntValue RIPOSTE_WINDOW_TICKS = BUILDER
            .comment("After a successful parry, an attack started within this many ticks is a faster riposte")
            .defineInRange("riposteWindowTicks", 10, 0, 100);
    public static final ModConfigSpec.IntValue PARRIED_STAGGER_TICKS = BUILDER
            .comment("How long an attacker is staggered after being parried (they may still parry the riposte)")
            .defineInRange("parriedStaggerTicks", 14, 1, 100);
    public static final ModConfigSpec.IntValue SHIELD_BOUNCE_STAGGER_TICKS = BUILDER
            .comment("How long an attacker is staggered after their swing hits a shield")
            .defineInRange("shieldBounceStaggerTicks", 8, 0, 100);
    public static final ModConfigSpec.IntValue GUARD_BREAK_STAGGER_TICKS = BUILDER
            .comment("How long a defender is staggered (unable to act) when their stamina breaks")
            .defineInRange("guardBreakStaggerTicks", 24, 1, 200);
    public static final ModConfigSpec.IntValue SHIELD_BREAK_COOLDOWN_TICKS = BUILDER
            .defineInRange("shieldBreakCooldownTicks", 40, 0, 400);
    public static final ModConfigSpec.IntValue HOLSTER_TICKS = BUILDER
            .comment("Ticks the weapon is unusable in HOLSTER disarm mode")
            .defineInRange("holsterTicks", 60, 1, 1200);
    public static final ModConfigSpec.DoubleValue BASIC_SHIELD_CONE = BUILDER
            .comment("Full width in degrees protected by basic shields (vanilla and Spartan basic)")
            .defineInRange("basicShieldCone", 150.0, 10.0, 360.0);
    public static final ModConfigSpec.DoubleValue TOWER_SHIELD_CONE = BUILDER
            .defineInRange("towerShieldCone", 180.0, 10.0, 360.0);
    public static final ModConfigSpec.DoubleValue BASIC_SHIELD_STAMINA_MULT = BUILDER
            .defineInRange("basicShieldStaminaMult", 0.7, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue TOWER_SHIELD_STAMINA_MULT = BUILDER
            .defineInRange("towerShieldStaminaMult", 0.5, 0.0, 10.0);

    static {
        BUILDER.pop().push("mobs");
    }

    public static final ModConfigSpec.BooleanValue TELEGRAPH_MOB_ATTACKS = BUILDER
            .comment("Mobs in #steelclash:fighters wind up their melee attacks instead of hitting instantly")
            .define("telegraphAttacks", true);
    public static final ModConfigSpec.DoubleValue MOB_PARRY_CHANCE_EASY = BUILDER
            .defineInRange("parryChanceEasy", 0.15, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue MOB_PARRY_CHANCE_NORMAL = BUILDER
            .defineInRange("parryChanceNormal", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue MOB_PARRY_CHANCE_HARD = BUILDER
            .defineInRange("parryChanceHard", 0.55, 0.0, 1.0);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    public static final class Client {
        private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

        public static final ModConfigSpec.BooleanValue SCROLL_ATTACKS = BUILDER
                .comment("While holding a weapon, scroll up = overhead and scroll down = stab (as in Chivalry 2).",
                        "Number keys still switch hotbar slots. Turn off to keep vanilla scroll-to-switch.")
                .define("scrollAttacks", true);

        static final ModConfigSpec SPEC = BUILDER.build();

        private Client() {
        }
    }
}
