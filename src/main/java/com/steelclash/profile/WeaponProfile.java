package com.steelclash.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steelclash.core.ArcPath;
import com.steelclash.core.AttackTimings;
import com.steelclash.core.AttackType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * How a class of weapon fights. Loaded from {@code data/<ns>/steelclash/weapon_profile/<name>.json} and synced to
 * clients, so it can be tuned with {@code /reload}.
 *
 * @param referenceAttackSpeed the ATTACK_SPEED attribute value these timings were tuned for
 * @param speedScaling         how strongly the wielder's actual attack speed rescales timings (0 = not at all)
 * @param guard                weapon parry; absent for things that can't parry (claws, beasts)
 * @param riposteWindupMult    windup multiplier for an attack started right after a successful parry
 * @param heavy                how holding the attack input changes it
 * @param hyperArmorOnHeavy    heavy attacks can't be flinched (two-handers, maces, big beasts)
 */
public record WeaponProfile(String archetype, float referenceAttackSpeed, float speedScaling,
                            Map<AttackType, AttackSpec> attacks, Optional<GuardSpec> guard,
                            float riposteWindupMult, HeavySpec heavy, boolean hyperArmorOnHeavy) {

    public static final Codec<AttackType> ATTACK_TYPE_CODEC = Codec.STRING.comapFlatMap(
            name -> Arrays.stream(AttackType.values())
                    .filter(t -> t.serializedName().equals(name))
                    .findFirst()
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown attack type: " + name)),
            AttackType::serializedName);

    public static final Codec<WeaponProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("archetype").forGetter(WeaponProfile::archetype),
            Codec.floatRange(0.01f, 100f).fieldOf("reference_attack_speed").forGetter(WeaponProfile::referenceAttackSpeed),
            Codec.floatRange(0f, 2f).optionalFieldOf("speed_scaling", 0.5f).forGetter(WeaponProfile::speedScaling),
            Codec.unboundedMap(ATTACK_TYPE_CODEC, AttackSpec.CODEC).fieldOf("attacks").forGetter(WeaponProfile::attacks),
            GuardSpec.CODEC.optionalFieldOf("guard").forGetter(WeaponProfile::guard),
            Codec.floatRange(0.1f, 1f).optionalFieldOf("riposte_windup_mult", 0.6f).forGetter(WeaponProfile::riposteWindupMult),
            HeavySpec.CODEC.optionalFieldOf("heavy", HeavySpec.DEFAULT).forGetter(WeaponProfile::heavy),
            Codec.BOOL.optionalFieldOf("hyper_armor_on_heavy", false).forGetter(WeaponProfile::hyperArmorOnHeavy)
    ).apply(i, WeaponProfile::new));

    public Optional<AttackSpec> attack(AttackType type) {
        return Optional.ofNullable(attacks.get(type));
    }

    /** Heavy (held) attack multipliers. */
    public record HeavySpec(float windupMult, float damageMult, float staminaDamageMult) {
        public static final HeavySpec DEFAULT = new HeavySpec(1.6f, 1.5f, 1.6f);
        public static final Codec<HeavySpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(1f, 5f).optionalFieldOf("windup_mult", DEFAULT.windupMult).forGetter(HeavySpec::windupMult),
                Codec.floatRange(0f, 10f).optionalFieldOf("damage_mult", DEFAULT.damageMult).forGetter(HeavySpec::damageMult),
                Codec.floatRange(0f, 10f).optionalFieldOf("stamina_damage_mult", DEFAULT.staminaDamageMult).forGetter(HeavySpec::staminaDamageMult)
        ).apply(i, HeavySpec::new));
    }

    /**
     * Weapon parry.
     *
     * @param parryTicks  how long the parry stays up if held
     * @param recovery    ticks to lower the guard when the parry caught nothing
     * @param cone        full width of the protected arc in front, degrees
     * @param staminaMult multiplier on stamina lost per parried attack (daggers parry poorly, staves well)
     */
    public record GuardSpec(int parryTicks, int recovery, float cone, float staminaMult) {
        public static final Codec<GuardSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, AttackTimings.MAX_TICKS).optionalFieldOf("parry_ticks", 12).forGetter(GuardSpec::parryTicks),
                Codec.intRange(1, AttackTimings.MAX_TICKS).optionalFieldOf("recovery", 6).forGetter(GuardSpec::recovery),
                Codec.floatRange(10f, 360f).optionalFieldOf("cone", 140f).forGetter(GuardSpec::cone),
                Codec.floatRange(0f, 10f).optionalFieldOf("stamina_mult", 1f).forGetter(GuardSpec::staminaMult)
        ).apply(i, GuardSpec::new));
    }

    /**
     * One attack of a profile. Timings are in ticks at {@link #referenceAttackSpeed}.
     *
     * @param staminaDamage stamina the defender loses when this attack is parried or shield-blocked
     * @param staminaCost   stamina the attacker loses when the attack hits nothing (whiff)
     */
    public record AttackSpec(int windup, int release, int recovery, float damage, ArcSpec arc, int maxTargets,
                             float reachBonus, float staminaDamage, float staminaCost, List<ArcSpec> variants) {
        public AttackSpec(int windup, int release, int recovery, float damage, ArcSpec arc, int maxTargets,
                          float reachBonus, float staminaDamage, float staminaCost) {
            this(windup, release, recovery, damage, arc, maxTargets, reachBonus, staminaDamage, staminaCost, List.of());
        }

        /** How many different arcs this attack can be swung along (the main arc plus its variants). */
        public int variantCount() {
            return 1 + variants.size();
        }

        /** Arc for a variant index: 0 is the main arc, out-of-range indices fall back to it. */
        public ArcSpec arc(int variant) {
            return variant > 0 && variant <= variants.size() ? variants.get(variant - 1) : arc;
        }

        public static final Codec<AttackSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, AttackTimings.MAX_TICKS).fieldOf("windup").forGetter(AttackSpec::windup),
                Codec.intRange(1, AttackTimings.MAX_TICKS).fieldOf("release").forGetter(AttackSpec::release),
                Codec.intRange(1, AttackTimings.MAX_TICKS).fieldOf("recovery").forGetter(AttackSpec::recovery),
                Codec.floatRange(0f, 100f).optionalFieldOf("damage", 1f).forGetter(AttackSpec::damage),
                ArcSpec.CODEC.fieldOf("arc").forGetter(AttackSpec::arc),
                Codec.intRange(1, 64).optionalFieldOf("max_targets", 1).forGetter(AttackSpec::maxTargets),
                Codec.floatRange(-3f, 5f).optionalFieldOf("reach_bonus", 0f).forGetter(AttackSpec::reachBonus),
                Codec.floatRange(0f, 1000f).optionalFieldOf("stamina_damage", 15f).forGetter(AttackSpec::staminaDamage),
                Codec.floatRange(0f, 1000f).optionalFieldOf("stamina_cost", 6f).forGetter(AttackSpec::staminaCost),
                ArcSpec.CODEC.listOf().optionalFieldOf("variants", List.of()).forGetter(AttackSpec::variants)
        ).apply(i, AttackSpec::new));

        public AttackTimings timings() {
            return new AttackTimings(windup, release, recovery);
        }
    }

    /**
     * Arc shape: a preset ({@code horizontal} with a width, {@code vertical}, {@code thrust}, {@code kick}) or explicit
     * keyframes {@code [[t, yaw, pitch, extension], ...]} (yaw right-positive, pitch down-positive, degrees, relative to
     * the view), which override the preset.
     */
    public record ArcSpec(Shape shape, float width, Optional<List<List<Float>>> keyframes) {
        public ArcSpec(Shape shape, float width) {
            this(shape, width, Optional.empty());
        }

        public enum Shape {
            HORIZONTAL, VERTICAL, THRUST, KICK;

            static final Codec<Shape> CODEC = Codec.STRING.comapFlatMap(
                    name -> Arrays.stream(values())
                            .filter(s -> s.name().toLowerCase(Locale.ROOT).equals(name))
                            .findFirst()
                            .map(DataResult::success)
                            .orElseGet(() -> DataResult.error(() -> "Unknown arc shape: " + name)),
                    s -> s.name().toLowerCase(Locale.ROOT));
        }

        public static final Codec<ArcSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Shape.CODEC.optionalFieldOf("shape", Shape.HORIZONTAL).forGetter(ArcSpec::shape),
                Codec.floatRange(1f, 360f).optionalFieldOf("width", 140f).forGetter(ArcSpec::width),
                Codec.FLOAT.listOf(4, 4).listOf().optionalFieldOf("keyframes").forGetter(ArcSpec::keyframes)
        ).apply(i, ArcSpec::new));

        public ArcPath toPath() {
            if (keyframes.isPresent() && !keyframes.get().isEmpty()) {
                return new ArcPath(keyframes.get().stream()
                        .map(k -> new ArcPath.Keyframe(k.get(0), k.get(1), k.get(2), k.get(3)))
                        .toList());
            }
            return switch (shape) {
                case HORIZONTAL -> ArcPath.horizontal(width);
                case VERTICAL -> ArcPath.vertical();
                case THRUST -> ArcPath.thrust();
                case KICK -> ArcPath.kick();
            };
        }
    }
}
