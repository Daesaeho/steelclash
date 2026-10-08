package com.steelclash;

import com.steelclash.core.Gesture;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public enum DisarmMode {
        /** Weapon is knocked out of the hand and dropped in front of the defender (Chivalry 2 behavior). */
        DROP,
        /** Weapon stays in the inventory but can't be used for a few seconds. */
        HOLSTER
    }

    public enum BlockMode {
        /** Chivalry 2: the guard stays up while the parry key is held, draining stamina slowly. */
        HELD,
        /** The guard stays up for the weapon's parry_ticks at most, then drops (timing-based parrying). */
        TIMED
    }

    public static final ModConfigSpec.EnumValue<DisarmMode> DISARM_MODE = BUILDER
            .comment("What happens when a parry is made with no stamina left")
            .defineEnum("disarmMode", DisarmMode.DROP);

    static {
        BUILDER.push("stamina");
    }

    public static final ModConfigSpec.DoubleValue MAX_STAMINA = BUILDER
            .comment("Maximum stamina for players and mobs")
            .defineInRange("max", 100.0, 1.0, 10_000.0);
    public static final ModConfigSpec.DoubleValue STAMINA_REGEN_PER_SECOND = BUILDER
            .comment("Stamina regained per second once regeneration starts")
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
    public static final ModConfigSpec.EnumValue<BlockMode> BLOCK_MODE = BUILDER
            .comment("How a player's weapon parry works. HELD (Chivalry 2): the guard stays up while the key is held and",
                    "drains stamina slowly. TIMED: it drops after the weapon's parry_ticks. Mobs always use timed parries.")
            .defineEnum("blockMode", BlockMode.HELD);
    public static final ModConfigSpec.DoubleValue HELD_BLOCK_DRAIN_PER_SECOND = BUILDER
            .comment("Stamina drained per second while holding a weapon guard (HELD mode); stamina doesn't regenerate meanwhile")
            .defineInRange("heldBlockDrainPerSecond", 4.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue RIPOSTE_ACTIVE_PARRY_TICKS = BUILDER
            .comment("Active parry: while a riposte winds up and swings, attacks from the front are parried automatically",
                    "for this many ticks (Chivalry 2 lets you riposte through a second attacker)")
            .defineInRange("riposteActiveParryTicks", 9, 0, 100);
    public static final ModConfigSpec.IntValue COUNTER_ACTIVE_PARRY_TICKS = BUILDER
            .comment("Active parry ticks after a successful counter")
            .defineInRange("counterActiveParryTicks", 15, 0, 100);
    public static final ModConfigSpec.IntValue ACTIVE_PARRY_EXTEND_TICKS = BUILDER
            .comment("Each hit an active parry catches extends it by this many ticks")
            .defineInRange("activeParryExtendTicks", 2, 0, 100);
    public static final ModConfigSpec.IntValue PARRY_FORGIVENESS_TICKS = BUILDER
            .comment("Parry forgiveness: an attack started from the guard (a counter attempt of the wrong type, or too",
                    "late) this many ticks or fewer before a hit lands falls back to a block instead of getting you hit")
            .defineInRange("parryForgivenessTicks", 2, 0, 20);
    public static final ModConfigSpec.IntValue PARRIED_STAGGER_TICKS = BUILDER
            .comment("How long an attacker is staggered after being parried (they may still parry the riposte)")
            .defineInRange("parriedStaggerTicks", 14, 1, 100);
    public static final ModConfigSpec.IntValue SPECIAL_BLOCK_STAGGER_TICKS = BUILDER
            .comment("Blocking a special attack with a weapon guard staggers the blocker this long instead of opening a",
                    "riposte; the attacker keeps the initiative (Chivalry 2)")
            .defineInRange("specialBlockStaggerTicks", 10, 1, 100);
    public static final ModConfigSpec.IntValue SPECIAL_HIT_STAGGER_TICKS = BUILDER
            .comment("A special attack that hits staggers its target this long (Chivalry 2)")
            .defineInRange("specialHitStaggerTicks", 8, 1, 100);
    public static final ModConfigSpec.IntValue SHIELD_BOUNCE_STAGGER_TICKS = BUILDER
            .comment("How long an attacker is staggered after their swing hits a shield")
            .defineInRange("shieldBounceStaggerTicks", 8, 0, 100);
    public static final ModConfigSpec.IntValue GUARD_BREAK_STAGGER_TICKS = BUILDER
            .comment("How long a defender is staggered (unable to act) when their stamina breaks")
            .defineInRange("guardBreakStaggerTicks", 24, 1, 200);
    public static final ModConfigSpec.IntValue SHIELD_BREAK_COOLDOWN_TICKS = BUILDER
            .comment("A player whose shield guard breaks cannot raise it again for this long")
            .defineInRange("shieldBreakCooldownTicks", 40, 0, 400);
    public static final ModConfigSpec.IntValue HOLSTER_TICKS = BUILDER
            .comment("Ticks the weapon is unusable in HOLSTER disarm mode")
            .defineInRange("holsterTicks", 60, 1, 1200);
    public static final ModConfigSpec.DoubleValue BASIC_SHIELD_CONE = BUILDER
            .comment("Full width in degrees protected by basic shields (vanilla and Spartan basic)")
            .defineInRange("basicShieldCone", 150.0, 10.0, 360.0);
    public static final ModConfigSpec.DoubleValue TOWER_SHIELD_CONE = BUILDER
            .comment("Full width in degrees protected by tower shields (Spartan Shields)")
            .defineInRange("towerShieldCone", 180.0, 10.0, 360.0);
    public static final ModConfigSpec.DoubleValue BASIC_SHIELD_STAMINA_MULT = BUILDER
            .comment("Stamina a basic shield block costs, as a fraction of the hit's stamina damage")
            .defineInRange("basicShieldStaminaMult", 0.7, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue TOWER_SHIELD_STAMINA_MULT = BUILDER
            .comment("Stamina a tower shield block costs, as a fraction of the hit's stamina damage")
            .defineInRange("towerShieldStaminaMult", 0.5, 0.0, 10.0);

    static {
        BUILDER.pop().push("offense");
    }

    public static final ModConfigSpec.DoubleValue FEINT_STAMINA_COST = BUILDER
            .comment("Stamina spent cancelling an attack during its windup")
            .defineInRange("feintStaminaCost", 10.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue MORPH_STAMINA_COST = BUILDER
            .comment("Stamina spent changing an attack into another type during its windup")
            .defineInRange("morphStaminaCost", 8.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue PARRY_CANCEL_STAMINA_COST = BUILDER
            .comment("Cancelling a windup straight into a parry")
            .defineInRange("parryCancelStaminaCost", 10.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue DODGE_STAMINA_COST = BUILDER
            .comment("Stamina a dodge costs (Chivalry 2: 12); you can't dodge with less")
            .defineInRange("dodgeStaminaCost", 12.0, 0.0, 1000.0);
    public static final ModConfigSpec.IntValue DODGE_COOLDOWN_TICKS = BUILDER
            .comment("Ticks between dodges")
            .defineInRange("dodgeCooldownTicks", 20, 0, 400);
    public static final ModConfigSpec.DoubleValue DODGE_SPEED = BUILDER
            .comment("Dodge burst speed in blocks per tick (about 2.2 blocks travelled per 1.0 on flat ground)")
            .defineInRange("dodgeSpeed", 1.1, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue JAB_DAMAGE_MULT = BUILDER
            .comment("Jab damage as a fraction of the weapon's attack damage (Chivalry 2: about a tenth of a life bar)")
            .defineInRange("jabDamageMult", 0.25, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue JAB_STAMINA_COST = BUILDER
            .comment("Stamina spent on a jab")
            .defineInRange("jabStaminaCost", 6.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue JAB_STAMINA_DAMAGE = BUILDER
            .comment("Stamina a parried jab costs the defender")
            .defineInRange("jabStaminaDamage", 8.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue JUMP_STAMINA_COST = BUILDER
            .comment("Stamina a jump costs while fighting (Chivalry 2: 12). Only counts within a few seconds of attacking,",
                    "guarding or being hit, so jumping around outside a fight stays free. 0 = off")
            .defineInRange("jumpStaminaCost", 12.0, 0.0, 1000.0);
    public static final ModConfigSpec.BooleanValue CROUCH_PAUSES_STAMINA_REGEN = BUILDER
            .comment("Crouching pauses stamina regeneration (Chivalry 2)")
            .define("crouchPausesStaminaRegen", true);
    public static final ModConfigSpec.DoubleValue KICK_STAMINA_COST = BUILDER
            .comment("Stamina spent on a kick or shield bash")
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
    public static final ModConfigSpec.BooleanValue DOWNED = BUILDER
            .comment("Chivalry 2 downed state: a lethal blow puts a player on the ground instead of killing them, if an ally",
                    "is nearby to revive them. Downed players crawl, can't fight, and bleed out unless revived; another",
                    "lethal blow finishes them. /kill, the void and logging out still kill")
            .define("downed", true);
    public static final ModConfigSpec.DoubleValue DOWNED_ALLY_RANGE = BUILDER
            .comment("A player only goes down if an ally who could revive them is within this many blocks (0 = always)")
            .defineInRange("downedAllyRange", 48.0, 0.0, 1024.0);
    public static final ModConfigSpec.DoubleValue DOWNED_HEALTH = BUILDER
            .comment("Health a downed player has left: the finishing blow has to take this")
            .defineInRange("downedHealth", 6.0, 1.0, 1024.0);
    public static final ModConfigSpec.IntValue BLEED_OUT_SECONDS = BUILDER
            .comment("Seconds a downed player lasts before bleeding out")
            .defineInRange("bleedOutSeconds", 30, 1, 3600);
    public static final ModConfigSpec.DoubleValue REVIVE_SECONDS = BUILDER
            .comment("Seconds an ally has to crouch next to a downed player to revive them (taking damage restarts it)")
            .defineInRange("reviveSeconds", 3.0, 0.05, 60.0);
    public static final ModConfigSpec.DoubleValue REVIVE_RANGE = BUILDER
            .comment("How close (blocks) the crouching ally has to be")
            .defineInRange("reviveRange", 2.5, 0.5, 16.0);
    public static final ModConfigSpec.DoubleValue REVIVE_HEALTH = BUILDER
            .comment("Share of max health a revived player gets back")
            .defineInRange("reviveHealth", 0.3, 0.01, 1.0);
    public static final ModConfigSpec.BooleanValue MOBS_IGNORE_DOWNED = BUILDER
            .comment("Mobs don't go after downed players (so allies have a chance to revive); stray swings still land")
            .define("mobsIgnoreDowned", true);
    public static final ModConfigSpec.BooleanValue PLAYERS_ARE_ALLIES = BUILDER
            .comment("All players fight on one side (co-op). Off: only players on the same scoreboard team are allies")
            .define("playersAreAllies", true);
    public static final ModConfigSpec.BooleanValue FRIENDLY_COLLISION = BUILDER
            .comment("Allies' bodies are in the way of your swings (Chivalry 2): slashes and overheads carry on through them,",
                    "stabs stop in them. Off: swings pass through allies untouched")
            .define("friendlyCollision", true);
    public static final ModConfigSpec.DoubleValue FRIENDLY_DAMAGE_SCALE = BUILDER
            .comment("Share of a swing's damage an ally takes (0 = no friendly fire). Allies' guards never parry you")
            .defineInRange("friendlyDamageScale", 0.25, 0.0, 1.0);
    public static final ModConfigSpec.IntValue PROJECTILE_COUNTER_MILLIS = BUILDER
            .comment("An arrow, bolt or thrown weapon arriving from the front within this many milliseconds of starting a",
                    "slash, overhead or stab is deflected (Chivalry 2's projectile counter, 0.25 s). 0 = off")
            .defineInRange("projectileCounterMillis", 250, 0, 2000);
    public static final ModConfigSpec.DoubleValue PROJECTILE_WEAPON_BLOCK_REDUCTION = BUILDER
            .comment("A projectile caught on a held weapon guard (not a shield) deals this much less damage (Chivalry 2: 30%).",
                    "It costs projectileStaminaDamage stamina but never breaks the guard")
            .defineInRange("projectileWeaponBlockReduction", 0.3, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue HEADSHOT_MULTIPLIER = BUILDER
            .comment("Damage multiplier for arrows, bolts and thrown weapons that hit the head (Chivalry 2: +25%). 1 = off")
            .defineInRange("headshotMultiplier", 1.25, 1.0, 10.0);
    public static final ModConfigSpec.BooleanValue INTERRUPT_DRAW_ON_HIT = BUILDER
            .comment("Taking damage while drawing a bow or loading a crossbow interrupts it (Chivalry 2)")
            .define("interruptDrawOnHit", true);
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
            .comment("How long the attacker is staggered when their blade hits a wall")
            .defineInRange("clankStaggerTicks", 10, 0, 100);
    public static final ModConfigSpec.DoubleValue TURN_CAP = BUILDER
            .comment("Turn speed limit (degrees per second) while winding up and releasing an attack, as in Chivalry 2.",
                    "Accels and drags still work; 180° flicks don't. 0 = no limit.")
            .defineInRange("turnCapDegreesPerSecond", 360.0, 0.0, 3600.0);
    public static final ModConfigSpec.DoubleValue LUNGE_REACH_BONUS = BUILDER
            .comment("Extra reach for attacks started while sprinting")
            .defineInRange("lungeReachBonus", 0.7, 0.0, 5.0);
    public static final ModConfigSpec.DoubleValue LUNGE_DAMAGE_MULT = BUILDER
            .comment("Damage multiplier for attacks started while sprinting")
            .defineInRange("lungeDamageMult", 1.15, 0.0, 10.0);
    public static final ModConfigSpec.IntValue LUNGE_WHIFF_RECOVERY_MS = BUILDER
            .comment("Extra recovery after an attack started while sprinting hits nothing (Chivalry 2 2.10: missed",
                    "sprint attacks can be punished)")
            .defineInRange("lungeWhiffRecoveryMs", 300, 0, 5000);
    public static final ModConfigSpec.BooleanValue DAMAGE_TYPES = BUILDER
            .comment("Cut/chop/blunt/pierce damage versus armour weight (blunt beats plate, cuts beat cloth)")
            .define("damageTypes", true);
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
            .comment("maxAttackers on Normal difficulty")
            .defineInRange("maxAttackersNormal", 2, 1, 64);
    public static final ModConfigSpec.IntValue MAX_ATTACKERS_HARD = BUILDER
            .comment("maxAttackers on Hard difficulty")
            .defineInRange("maxAttackersHard", 3, 1, 64);
    public static final ModConfigSpec.DoubleValue ARMED_CHANCE_EASY = BUILDER
            .comment("Chance a #steelclash:armable mob (zombies, husks, zombie villagers) spawns with a melee weapon",
                    "from #steelclash:mob_weapons/tier_1..3 (includes Spartan Weaponry weapons when installed)")
            .defineInRange("armedChanceEasy", 0.30, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue ARMED_CHANCE_NORMAL = BUILDER
            .comment("armedChance on Normal difficulty")
            .defineInRange("armedChanceNormal", 0.50, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue ARMED_CHANCE_HARD = BUILDER
            .comment("armedChance on Hard difficulty")
            .defineInRange("armedChanceHard", 0.70, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue ARMED_SHIELD_CHANCE = BUILDER
            .comment("Chance an armed mob with a one-handed weapon also gets a shield (scaled up with difficulty)")
            .defineInRange("armedShieldChance", 0.20, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue ARMED_HELMET_CHANCE = BUILDER
            .comment("Chance an armed mob also gets a helmet (scaled up with difficulty; also stops daylight burning)")
            .defineInRange("armedHelmetChance", 0.30, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SIDEARM_CHANCE = BUILDER
            .comment("Chance a #steelclash:sidearm_users mob (skeletons, strays, bogged) spawns with a melee sidearm from",
                    "#steelclash:mob_sidearms/tier_1..3 (Spartan Weaponry daggers: vanilla has none) and draws it when",
                    "its target comes close, like a Chivalry 2 archer")
            .defineInRange("sidearmChance", 1.0, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue SOLDIER_PATROLS = BUILDER
            .comment("Brigand patrols (a knight leading footmen and archers) roam toward players, like pillager patrols")
            .define("soldierPatrols", true);
    public static final ModConfigSpec.IntValue SOLDIER_PATROL_INTERVAL_TICKS = BUILDER
            .comment("Ticks between patrol spawn attempts (12000 = half a day, like vanilla patrols)")
            .defineInRange("soldierPatrolIntervalTicks", 12000, 200, 1_000_000);
    public static final ModConfigSpec.DoubleValue SOLDIER_PATROL_CHANCE = BUILDER
            .comment("Chance a patrol spawns at each attempt")
            .defineInRange("soldierPatrolChance", 0.3, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue MOB_HEAVY_CHANCE = BUILDER
            .comment("Chance a mob's telegraphed attack is a heavy")
            .defineInRange("heavyChance", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue TELEGRAPH_MOB_ATTACKS = BUILDER
            .comment("Mobs in #steelclash:fighters wind up their melee attacks instead of hitting instantly")
            .define("telegraphAttacks", true);
    public static final ModConfigSpec.DoubleValue MOB_PARRY_CHANCE_EASY = BUILDER
            .comment("Chance a mob without the bot brain parries an attack it sees coming, on Easy")
            .defineInRange("parryChanceEasy", 0.15, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue MOB_PARRY_CHANCE_NORMAL = BUILDER
            .comment("Mob parry chance on Normal difficulty")
            .defineInRange("parryChanceNormal", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue MOB_PARRY_CHANCE_HARD = BUILDER
            .comment("Mob parry chance on Hard difficulty")
            .defineInRange("parryChanceHard", 0.55, 0.0, 1.0);

    static {
        BUILDER.pop().push("network");
    }

    public static final ModConfigSpec.BooleanValue LAG_COMPENSATION = BUILDER
            .comment("Compensate for player latency: rewind targets for lagged attackers, give lagged defenders time to parry")
            .define("lagCompensation", true);
    public static final ModConfigSpec.IntValue MAX_REWIND_MS = BUILDER
            .comment("Furthest back (ms) hit detection rewinds targets for a lagged attacker")
            .defineInRange("maxRewindMs", 300, 0, 1000);
    public static final ModConfigSpec.IntValue INTERPOLATION_TICKS = BUILDER
            .comment("How far behind the server clients display other entities (vanilla interpolation), in ticks")
            .defineInRange("interpolationTicks", 2, 0, 10);
    public static final ModConfigSpec.IntValue MAX_PARRY_GRACE_MS = BUILDER
            .comment("Longest (ms) a hit on a lagged player is held so their parry, block or counter can still arrive")
            .defineInRange("maxParryGraceMs", 250, 0, 1000);

    static {
        BUILDER.pop().push("movement");
    }

    public static final ModConfigSpec.DoubleValue ATTACK_MOVE_SPEED = BUILDER
            .comment("Movement speed (fraction of normal) while winding up and releasing an attack, as in Chivalry 2")
            .defineInRange("attackMoveSpeed", 0.65, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue RECOVERY_MOVE_SPEED = BUILDER
            .comment("Movement speed while recovering from an attack")
            .defineInRange("recoveryMoveSpeed", 0.85, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue GUARD_MOVE_SPEED = BUILDER
            .comment("Movement speed while a weapon parry is up or being lowered (shields use vanilla's item-use slowdown)")
            .defineInRange("guardMoveSpeed", 0.75, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue STAGGER_MOVE_SPEED = BUILDER
            .comment("Movement speed while staggered (parried, kicked, guard broken)")
            .defineInRange("staggerMoveSpeed", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BACKPEDAL_SPEED = BUILDER
            .comment("Players walking backwards with a weapon move at this fraction of normal speed")
            .defineInRange("backpedalSpeed", 0.8, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DUCK_HEIGHT = BUILDER
            .comment("Ducking: a crouching fighter only counts this many blocks tall for blade hits, so level slashes pass",
                    "over (overheads, kicks and slashes aimed downward still hit). 0 = use the normal crouching hitbox.")
            .defineInRange("duckHeight", 1.0, 0.0, 2.0);

    static {
        BUILDER.pop().push("health");
    }

    public static final ModConfigSpec.BooleanValue HEALTH_REGEN = BUILDER
            .comment("Players regenerate health a few seconds out of combat (Chivalry 2), independent of food")
            .define("healthRegen", true);
    public static final ModConfigSpec.IntValue HEALTH_REGEN_DELAY_TICKS = BUILDER
            .comment("Ticks out of combat (not hurt, attacking or guarding) before health starts coming back")
            .defineInRange("healthRegenDelayTicks", 120, 0, 6000);
    public static final ModConfigSpec.DoubleValue HEALTH_REGEN_CAP = BUILDER
            .comment("Regeneration stops at this fraction of max health (Chivalry 2: about 0.4; 1 = all the way)")
            .defineInRange("healthRegenCap", 0.4, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue HEALTH_REGEN_PER_SECOND = BUILDER
            .comment("Health regained per second once it starts (20 = a full vanilla health bar)")
            .defineInRange("healthRegenPerSecond", 1.0, 0.0, 100.0);

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

        /** How slashes and right click work. Changing it rebinds the parry and second slash keys once. */
        public enum ControlScheme {
            /** One slash key whose side alternates (turning picks it); right click parries. */
            CHIVALRY,
            /** Left click slashes right to left, right click slashes left to right; parry on middle click. */
            TWO_SLASH_KEYS
        }

        public static final ModConfigSpec.EnumValue<ControlScheme> CONTROL_SCHEME = BUILDER
                .comment("CHIVALRY (as in Chivalry 2): left click slashes and the side alternates every slash, or follows",
                        "your turn if you're turning; right click parries. TWO_SLASH_KEYS: left click slashes right to left,",
                        "right click slashes left to right, parry moves to middle click. Switching rebinds those keys once;",
                        "you can rebind them freely afterwards in Controls.")
                .defineEnum("controlScheme", ControlScheme.CHIVALRY);

        /** Which slash key(s) read mouse gestures. */
        public enum GestureKeys {
            SLASH, SECOND_SLASH, BOTH
        }

        /** Picking the swing side from strafing (A / D). */
        public enum MovementSide {
            OFF, FROM_STRAFE_SIDE, TOWARD_STRAFE
        }

        public static final ModConfigSpec.EnumValue<MovementSide> SIDE_FROM_MOVEMENT = BUILDER
                .comment("Attack direction from movement: while strafing, swings whose side isn't fixed by their key (the",
                        "CHIVALRY slash, overheads, stabs, plain gesture clicks) take their side from it.",
                        "FROM_STRAFE_SIDE: strafing left swings from the left. TOWARD_STRAFE: strafing left swings toward the",
                        "left (right to left). OFF: sides alternate / follow your turn, as in Chivalry 2.")
                .defineEnum("sideFromMovement", MovementSide.OFF);

        public static final ModConfigSpec.BooleanValue GESTURE_ATTACKS = BUILDER
                .comment("EXPERIMENTAL. Hold a slash key and drag the mouse to choose the attack (see gestureLeft/Right/Up/Down).",
                        "A click without a drag does the key's own slash. Keep holding after the gesture for a heavy.")
                .define("gestureAttacks", false);
        public static final ModConfigSpec.EnumValue<GestureKeys> GESTURE_KEYS = BUILDER
                .comment("Which key reads gestures: the slash key (left click), the second slash key (left to right,",
                        "right click in the TWO_SLASH_KEYS scheme), or both")
                .defineEnum("gestureKeys", GestureKeys.SLASH);
        public static final ModConfigSpec.BooleanValue GESTURE_LOCK_VIEW = BUILDER
                .comment("Freeze your view while a gesture is being read: the mouse movement only picks the attack and",
                        "doesn't turn you. Off = the gesture also turns your view (and the swing follows it).")
                .define("gestureLockView", false);
        public static final ModConfigSpec.EnumValue<Gesture.Action> GESTURE_LEFT = BUILDER
                .comment("Attack for dragging left. Defaults read the drag as where the attack comes from (as in Mordhau);",
                        "NONE ignores the direction. Options: SLASH_FROM_LEFT, SLASH_FROM_RIGHT, OVERHEAD, STAB, KICK, NONE")
                .defineEnum("gestureLeft", Gesture.Action.SLASH_FROM_LEFT);
        public static final ModConfigSpec.EnumValue<Gesture.Action> GESTURE_RIGHT = BUILDER
                .comment("Attack for dragging right")
                .defineEnum("gestureRight", Gesture.Action.SLASH_FROM_RIGHT);
        public static final ModConfigSpec.EnumValue<Gesture.Action> GESTURE_UP = BUILDER
                .comment("Attack for dragging up")
                .defineEnum("gestureUp", Gesture.Action.OVERHEAD);
        public static final ModConfigSpec.EnumValue<Gesture.Action> GESTURE_DOWN = BUILDER
                .comment("Attack for dragging down")
                .defineEnum("gestureDown", Gesture.Action.STAB);

        public static Gesture.Mapping gestureMapping() {
            return new Gesture.Mapping(GESTURE_LEFT.get(), GESTURE_RIGHT.get(), GESTURE_UP.get(), GESTURE_DOWN.get());
        }
        public static final ModConfigSpec.DoubleValue GESTURE_THRESHOLD = BUILDER
                .comment("How far (degrees of view movement) a drag must go to count as a gesture")
                .defineInRange("gestureThreshold", 5.0, 1.0, 45.0);
        public static final ModConfigSpec.IntValue GESTURE_WINDOW_TICKS = BUILDER
                .comment("How long (ticks) a held button waits for a gesture before slashing anyway")
                .defineInRange("gestureWindowTicks", 4, 1, 20);

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

        /** Hand-frame axis the held weapon is rolled around for blade twist (its length, after the grip pitch). */
        public enum TwistAxis {
            X, Y, Z
        }

        public static final ModConfigSpec.DoubleValue BLADE_TWIST = BUILDER
                .comment("Blade twist: the held weapon rolls around its length so the edge leads the cut (flat for slashes,",
                        "edge down for overheads). 1 = full, 0 = off, -1 = the other way round (if the edge trails).")
                .defineInRange("bladeTwist", 1.0, -1.0, 1.0);
        public static final ModConfigSpec.EnumValue<TwistAxis> BLADE_TWIST_AXIS = BUILDER
                .comment("Axis the twist turns around. Z should be the weapon's length; only change it if the weapon",
                        "visibly tumbles instead of rolling.")
                .defineEnum("bladeTwistAxis", TwistAxis.Z);

        public static final ModConfigSpec.BooleanValue HELP_ON_JOIN = BUILDER
                .comment("Show the controls help (/steelclash_help) in chat every time you join a world")
                .define("helpOnJoin", true);

        static final ModConfigSpec SPEC = BUILDER.build();

        private Client() {
        }
    }
}
