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
    public static final ModConfigSpec.IntValue PARRY_COOLDOWN_TICKS = BUILDER
            .comment("After a parry ends (caught an attack, released, or timed out), no new parry for this many ticks.",
                    "Stops parry spamming.")
            .defineInRange("parryCooldownTicks", 5, 0, 100);
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
        BUILDER.pop().push("offense");
    }

    public static final ModConfigSpec.DoubleValue FEINT_STAMINA_COST = BUILDER
            .defineInRange("feintStaminaCost", 10.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue MORPH_STAMINA_COST = BUILDER
            .defineInRange("morphStaminaCost", 8.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue PARRY_CANCEL_STAMINA_COST = BUILDER
            .comment("Cancelling a windup straight into a parry")
            .defineInRange("parryCancelStaminaCost", 10.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue KICK_STAMINA_COST = BUILDER
            .defineInRange("kickStaminaCost", 10.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue KICK_STAMINA_DAMAGE = BUILDER
            .comment("Stamina a guarding target loses when kicked (shield bash: x1.2)")
            .defineInRange("kickStaminaDamage", 25.0, 0.0, 1000.0);
    public static final ModConfigSpec.IntValue KICK_GUARD_BREAK_TICKS = BUILDER
            .comment("Stagger for a target whose parry or shield guard was kicked")
            .defineInRange("kickGuardBreakTicks", 20, 1, 200);
    public static final ModConfigSpec.IntValue KICK_STAGGER_TICKS = BUILDER
            .comment("Stagger for an unguarded target that was kicked")
            .defineInRange("kickStaggerTicks", 12, 1, 200);
    public static final ModConfigSpec.IntValue FLINCH_TICKS = BUILDER
            .comment("Stagger when hit during your own windup (heavy attacks with hyper armor ignore this)")
            .defineInRange("flinchTicks", 8, 0, 100);
    public static final ModConfigSpec.IntValue COUNTER_WINDOW_TICKS = BUILDER
            .comment("Starting the same attack type within this many ticks before an incoming attack lands counters it")
            .defineInRange("counterWindowTicks", 7, 0, 40);
    public static final ModConfigSpec.IntValue COUNTER_RELEASE_TICKS = BUILDER
            .comment("After a counter, your attack releases within this many ticks")
            .defineInRange("counterReleaseTicks", 3, 1, 40);
    public static final ModConfigSpec.BooleanValue ENVIRONMENT_CLANK = BUILDER
            .comment("Blades that hit walls/obstacles (not floors) stop and stagger the attacker")
            .define("environmentClank", true);
    public static final ModConfigSpec.IntValue CLANK_STAGGER_TICKS = BUILDER
            .defineInRange("clankStaggerTicks", 10, 0, 100);
    public static final ModConfigSpec.DoubleValue LUNGE_REACH_BONUS = BUILDER
            .comment("Extra reach for attacks started while sprinting")
            .defineInRange("lungeReachBonus", 0.7, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue LUNGE_DAMAGE_MULT = BUILDER
            .defineInRange("lungeDamageMult", 1.15, 0.0, 10.0);
    public static final ModConfigSpec.BooleanValue BLOOD_PARTICLES = BUILDER
            .comment("Red particles where swings connect")
            .define("bloodParticles", true);
    public static final ModConfigSpec.DoubleValue JUMP_ATTACK_DAMAGE_MULT = BUILDER
            .comment("Damage multiplier for overheads started in mid-air")
            .defineInRange("jumpAttackDamageMult", 1.2, 0.0, 10.0);

    static {
        BUILDER.pop().push("mobs");
    }

    public static final ModConfigSpec.BooleanValue BOT_BRAIN = BUILDER
            .comment("Fighter mobs use the Chivalry 2-style bot brain: spacing, attack turns, feints, counters, kicks")
            .define("botBrain", true);
    public static final ModConfigSpec.IntValue MAX_ATTACKERS_EASY = BUILDER
            .comment("How many mobs may swing at the same target at once (the rest circle and wait)")
            .defineInRange("maxAttackersEasy", 1, 1, 64);
    public static final ModConfigSpec.IntValue MAX_ATTACKERS_NORMAL = BUILDER
            .defineInRange("maxAttackersNormal", 2, 1, 64);
    public static final ModConfigSpec.IntValue MAX_ATTACKERS_HARD = BUILDER
            .defineInRange("maxAttackersHard", 3, 1, 64);
    public static final ModConfigSpec.DoubleValue ARMED_CHANCE_EASY = BUILDER
            .comment("Chance a #steelclash:armable mob (zombies, husks, zombie villagers) spawns with a melee weapon",
                    "from #steelclash:mob_weapons/tier_1..3 (includes Spartan Weaponry weapons when installed)")
            .defineInRange("armedChanceEasy", 0.30, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue ARMED_CHANCE_NORMAL = BUILDER
            .defineInRange("armedChanceNormal", 0.50, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue ARMED_CHANCE_HARD = BUILDER
            .defineInRange("armedChanceHard", 0.70, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue ARMED_SHIELD_CHANCE = BUILDER
            .comment("Chance an armed mob with a one-handed weapon also gets a shield (scaled up with difficulty)")
            .defineInRange("armedShieldChance", 0.20, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue ARMED_HELMET_CHANCE = BUILDER
            .comment("Chance an armed mob also gets a helmet (scaled up with difficulty; also stops daylight burning)")
            .defineInRange("armedHelmetChance", 0.30, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue MOB_HEAVY_CHANCE = BUILDER
            .comment("Chance a mob's telegraphed attack is a heavy")
            .defineInRange("heavyChance", 0.25, 0.0, 1.0);

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

        public static final ModConfigSpec.BooleanValue TIMING_HUD = BUILDER
                .comment("Timing bar under the crosshair: heavy charge, windup/release/recovery, combo window,",
                        "parry duration, guard recovery, stagger and the riposte window")
                .define("timingHud", true);
        public static final ModConfigSpec.BooleanValue ENEMY_TELEGRAPHS = BUILDER
                .comment("Learning aid: show the attack type and remaining windup above enemies' heads.",
                        "Off by default: reading the animation is the core skill.")
                .define("enemyTelegraphs", false);

        public static final ModConfigSpec.BooleanValue TWO_HANDED_SWORDS = BUILDER
                .comment("Hold swords (sword archetype: vanilla swords, longswords, katanas, sabers) with both hands",
                        "when the offhand is empty. Visual only.")
                .define("twoHandedSwords", true);

        public static final ModConfigSpec.DoubleValue CAMERA_MOTION = BUILDER
                .comment("Scale of camera sway during swings and shake on hits/parries. 0 turns it off (motion sensitivity).")
                .defineInRange("cameraMotion", 1.0, 0.0, 2.0);
        public static final ModConfigSpec.IntValue HIT_STOP_MILLIS = BUILDER
                .comment("How long your swing animation freezes when it connects (impact feel). 0 = off.")
                .defineInRange("hitStopMillis", 70, 0, 300);
        public static final ModConfigSpec.DoubleValue WEAPON_GRIP_PITCH = BUILDER
                .comment("Degrees the held weapon is rotated in your hand so its blade follows the swing.",
                        "-80 verified in game (2026-10-06); only change this if a weapon model is held unusually.")
                .defineInRange("weaponGripPitch", -80.0, -180.0, 180.0);

        static final ModConfigSpec SPEC = BUILDER.build();

        private Client() {
        }
    }
}
