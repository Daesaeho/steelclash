package com.steelclash.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steelclash.core.ArcPath;
import com.steelclash.core.AttackTimings;
import com.steelclash.core.AttackType;
import java.util.Arrays;
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
 */
public record WeaponProfile(String archetype, float referenceAttackSpeed, float speedScaling,
                            Map<AttackType, AttackSpec> attacks, Optional<GuardSpec> guard,
                            float riposteWindupMult) {

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
            Codec.floatRange(0.1f, 1f).optionalFieldOf("riposte_windup_mult", 0.6f).forGetter(WeaponProfile::riposteWindupMult)
    ).apply(i, WeaponProfile::new));

    public Optional<AttackSpec> attack(AttackType type) {
        return Optional.ofNullable(attacks.get(type));
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
                             float reachBonus, float staminaDamage, float staminaCost) {
        public static final Codec<AttackSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, AttackTimings.MAX_TICKS).fieldOf("windup").forGetter(AttackSpec::windup),
                Codec.intRange(1, AttackTimings.MAX_TICKS).fieldOf("release").forGetter(AttackSpec::release),
                Codec.intRange(1, AttackTimings.MAX_TICKS).fieldOf("recovery").forGetter(AttackSpec::recovery),
                Codec.floatRange(0f, 100f).optionalFieldOf("damage", 1f).forGetter(AttackSpec::damage),
                ArcSpec.CODEC.fieldOf("arc").forGetter(AttackSpec::arc),
                Codec.intRange(1, 64).optionalFieldOf("max_targets", 1).forGetter(AttackSpec::maxTargets),
                Codec.floatRange(-3f, 5f).optionalFieldOf("reach_bonus", 0f).forGetter(AttackSpec::reachBonus),
                Codec.floatRange(0f, 1000f).optionalFieldOf("stamina_damage", 15f).forGetter(AttackSpec::staminaDamage),
                Codec.floatRange(0f, 1000f).optionalFieldOf("stamina_cost", 6f).forGetter(AttackSpec::staminaCost)
        ).apply(i, AttackSpec::new));

        public AttackTimings timings() {
            return new AttackTimings(windup, release, recovery);
        }
    }

    /** Arc shape. Presets for now; M4 replaces these with keyframes extracted from the attack animations. */
    public record ArcSpec(Shape shape, float width) {
        public enum Shape {
            HORIZONTAL, VERTICAL, THRUST;

            static final Codec<Shape> CODEC = Codec.STRING.comapFlatMap(
                    name -> Arrays.stream(values())
                            .filter(s -> s.name().toLowerCase(Locale.ROOT).equals(name))
                            .findFirst()
                            .map(DataResult::success)
                            .orElseGet(() -> DataResult.error(() -> "Unknown arc shape: " + name)),
                    s -> s.name().toLowerCase(Locale.ROOT));
        }

        public static final Codec<ArcSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Shape.CODEC.fieldOf("shape").forGetter(ArcSpec::shape),
                Codec.floatRange(1f, 360f).optionalFieldOf("width", 140f).forGetter(ArcSpec::width)
        ).apply(i, ArcSpec::new));

        public ArcPath toPath() {
            return switch (shape) {
                case HORIZONTAL -> ArcPath.horizontal(width);
                case VERTICAL -> ArcPath.vertical();
                case THRUST -> ArcPath.thrust();
            };
        }
    }
}
