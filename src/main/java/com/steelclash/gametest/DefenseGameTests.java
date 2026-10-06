package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_NEGATIVE_X;
import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.dummy;
import static com.steelclash.gametest.TestSupport.face;
import static com.steelclash.gametest.TestSupport.isHurt;
import static com.steelclash.gametest.TestSupport.swing;
import static com.steelclash.gametest.TestSupport.swordsman;

import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.Disarm;
import com.steelclash.combat.ModAttachments;
import com.steelclash.compat.Compat;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.entity.TrainingDummy;
import com.steelclash.profile.WeaponProfiles;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** M2: parries, ripostes, stamina, disarms, shields and telegraphed mob attacks. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DefenseGameTests {
    private static final String ARENA = "arena";

    private DefenseGameTests() {
    }

    @GameTest(template = ARENA)
    public static void parryBlocksFrontalSlashAndStaggersAttacker(GameTestHelper helper) {
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Combat.requestParry(defender);
        check(helper, data(defender).machine.phase() == Phase.PARRY, "dummy should be parrying");
        swing(attacker, AttackType.SLASH);
        check(helper, !isHurt(defender), "a parried slash must not hurt");
        check(helper, data(attacker).machine.phase() == Phase.STAGGER, "the parried attacker should be staggered");
        check(helper, data(defender).machine.isRiposteReady(), "the defender should have a riposte ready");
        check(helper, data(defender).stamina.current() < data(defender).stamina.max(), "parrying costs stamina");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void parryDoesNotCoverTheBack(GameTestHelper helper) {
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_POSITIVE_X); // facing away
        Combat.requestParry(defender);
        swing(attacker, AttackType.SLASH);
        check(helper, isHurt(defender), "a parry facing away must not stop the hit");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void riposteHasAShorterWindup(GameTestHelper helper) {
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Combat.requestParry(defender);
        swing(attacker, AttackType.SLASH);
        CombatData d = data(defender);
        check(helper, Combat.start(defender, d, AttackType.SLASH), "riposte should start");
        int riposteWindup = d.machine.timings().windup();

        TrainingDummy fresh = dummy(helper, 6, 2, FACING_NEGATIVE_X);
        CombatData f = data(fresh);
        Combat.start(fresh, f, AttackType.SLASH);
        check(helper, riposteWindup < f.machine.timings().windup(),
                "riposte windup " + riposteWindup + " should be shorter than " + f.machine.timings().windup());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void parryingWithNoStaminaDisarms(GameTestHelper helper) {
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        data(defender).stamina.set(1);
        Combat.requestParry(defender);
        swing(attacker, AttackType.SLASH);
        check(helper, !isHurt(defender), "the exhausted parry still stops this hit");
        check(helper, defender.getMainHandItem().isEmpty(), "the defender should be disarmed");
        check(helper, data(defender).machine.phase() == Phase.STAGGER, "a broken guard staggers");
        boolean dropped = !helper.getLevel().getEntitiesOfClass(ItemEntity.class, defender.getBoundingBox().inflate(3),
                e -> e.getItem().is(Items.IRON_SWORD) && e.getPersistentData().getBoolean(Disarm.DISARMED_TAG)).isEmpty();
        check(helper, dropped, "the sword should drop as a disarmed item");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void shieldBlocksFromTheFrontOnly(GameTestHelper helper) {
        TrainingDummy front = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        front.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        front.startUsingItem(InteractionHand.OFF_HAND);
        // Vanilla shields only count as raised after 5 ticks of use.
        helper.runAfterDelay(8, () -> {
            Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
            check(helper, front.isBlocking(), "dummy should be blocking");
            swing(attacker, AttackType.SLASH);
            check(helper, !isHurt(front), "a raised shield should stop a frontal slash");
            check(helper, data(attacker).machine.phase() == Phase.STAGGER, "hitting a shield staggers the attacker");

            face(front, FACING_POSITIVE_X);
            Player second = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
            swing(second, AttackType.SLASH);
            check(helper, isHurt(front), "a shield facing away must not block");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void towerShieldStopsArrowsFromTheFrontOnly(GameTestHelper helper) {
        ItemStack shield = Compat.SPARTAN_SHIELDS.isLoaded()
                ? TestSupport.spartanShield("iron_tower_shield")
                : new ItemStack(Items.SHIELD);
        TrainingDummy front = dummy(helper, 4, 2, FACING_NEGATIVE_X);
        TrainingDummy back = dummy(helper, 4, 6, FACING_POSITIVE_X);
        for (TrainingDummy d : new TrainingDummy[]{front, back}) {
            d.setItemSlot(EquipmentSlot.OFFHAND, shield.copy());
            d.startUsingItem(InteractionHand.OFF_HAND);
        }
        helper.runAfterDelay(8, () -> {
            shoot(helper, new Vec3(1.5, 3.5, 2.5));
            shoot(helper, new Vec3(1.5, 3.5, 6.5));
        });
        helper.runAfterDelay(40, () -> {
            check(helper, !isHurt(front), "an arrow from the front should be blocked");
            check(helper, isHurt(back), "an arrow into the back should hit");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void zombieAttacksAreTelegraphed(GameTestHelper helper) {
        TrainingDummy target = dummy(helper, 5, 4, FACING_NEGATIVE_X);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 4);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in daylight
        zombie.setTarget(target);
        AtomicBoolean sawWindup = new AtomicBoolean();
        StringBuilder firstHurt = new StringBuilder();
        helper.onEachTick(() -> {
            if (zombie.hasData(ModAttachments.COMBAT) && data(zombie).machine.phase() == Phase.WINDUP) {
                sawWindup.set(true);
            }
            if (isHurt(target) && firstHurt.isEmpty()) {
                firstHurt.append(target.getLastDamageSource() == null ? "unknown" : target.getLastDamageSource().getMsgId())
                        .append(" after windup=").append(sawWindup.get());
            }
        });
        helper.succeedWhen(() -> {
            check(helper, !firstHurt.isEmpty(), "the zombie's attack should land (target set: " + (zombie.getTarget() == target)
                    + ", distance " + String.format("%.1f", zombie.distanceTo(target)) + ")");
            check(helper, firstHurt.toString().endsWith("windup=true"), "first hit must come after a windup; got: " + firstHurt);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void parryingDummyParriesAZombie(GameTestHelper helper) {
        TrainingDummy target = dummy(helper, 5, 4, FACING_NEGATIVE_X);
        target.setMode(TrainingDummy.Mode.PARRY);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 4);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setTarget(target);
        AtomicBoolean zombieStaggered = new AtomicBoolean();
        helper.onEachTick(() -> {
            // Bots circle (M5), so the defender turns to keep the zombie in front, like a player would.
            face(target, (float) (Math.toDegrees(Math.atan2(zombie.getZ() - target.getZ(), zombie.getX() - target.getX())) - 90));
            if (zombie.hasData(ModAttachments.COMBAT) && data(zombie).machine.phase() == Phase.STAGGER) {
                zombieStaggered.set(true);
            }
        });
        helper.succeedWhen(() -> {
            check(helper, zombieStaggered.get(), "the dummy should parry the zombie and stagger it");
            check(helper, !isHurt(target), "a parried zombie must not hurt the dummy");
        });
    }

    @GameTest(template = ARENA)
    public static void mobsGetNaturalWeaponProfiles(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        check(helper, WeaponProfiles.resolveFor(zombie).map(r -> r.key().location().getPath()).orElse("").equals("claw"),
                "unarmed zombie should fight with claws");
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        check(helper, WeaponProfiles.resolveFor(zombie).map(r -> r.key().location().getPath()).orElse("").equals("sword"),
                "armed zombie should use its weapon's profile");
        var spider = helper.spawnWithNoFreeWill(EntityType.SPIDER, 5, 2, 4);
        check(helper, WeaponProfiles.resolveFor(spider).map(r -> r.key().location().getPath()).orElse("").equals("beast"),
                "spider should fight as a beast");
        helper.succeed();
    }

    private static void shoot(GameTestHelper helper, Vec3 relativeFrom) {
        Vec3 from = helper.absoluteVec(relativeFrom);
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setPos(from);
        arrow.shoot(1, 0, 0, 2.0f, 0);
        helper.getLevel().addFreshEntity(arrow);
    }
}
