package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.finish;
import static com.steelclash.gametest.TestSupport.FACING_NEGATIVE_X;
import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.advance;
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
import com.steelclash.net.CombatStatePayload;
import com.steelclash.profile.WeaponProfiles;
import java.util.concurrent.atomic.AtomicBoolean;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
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
    public static void rejectedParryCancelDoesNotFeintForFree(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        CombatData d = data(player);
        check(helper, Combat.startParry(player, d), "guard starts");
        check(helper, Combat.start(player, d, AttackType.SLASH), "attack starts from the guard");
        float stamina = d.stamina.current();
        check(helper, !Combat.startParry(player, d), "cooldown rejects the parry-cancel");
        check(helper, d.machine.phase() == Phase.WINDUP, "rejection preserves the committed attack");
        check(helper, d.stamina.current() == stamina, "a rejected input does not spend stamina");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void statePacketPreservesCaughtParryAndCooldown(GameTestHelper helper) {
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        CombatData d = data(defender);
        d.machine.startParry(20, 8, 5);
        d.machine.parrySucceeded(10);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CombatStatePayload.STREAM_CODEC.encode(buf, CombatStatePayload.of(defender, d, true));
            CombatStatePayload decoded = CombatStatePayload.STREAM_CODEC.decode(buf);
            check(helper, decoded.predictionState().equals(d.machine.predictionState()), "prediction state survives the wire format");
            var client = new com.steelclash.core.CombatStateMachine();
            client.apply(decoded.phase(), decoded.attackType(), decoded.phaseElapsedUs(), decoded.phaseDurationUs(),
                    decoded.timings(), decoded.riposteTicks(), decoded.heavy(), decoded.morphed(), decoded.comboAllowed(),
                    decoded.variant(), decoded.mirrored(), decoded.thwacked(), decoded.recoverFrom());
            client.applyPredictionState(decoded.predictionState());
            client.releaseParry();
            check(helper, client.phase() == Phase.IDLE, "a caught guard releases straight to idle");
            check(helper, client.parryCooldownLeft() == 5, "the guard cooldown is preserved");
        } finally {
            buf.release();
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void oneParryCatchesTwoAttackers(GameTestHelper helper) {
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        // Two attackers 45 degrees to either side of the dummy's guard, both facing it.
        Player left = TestSupport.swordsman(helper, new ItemStack(Items.IRON_SWORD), -45f, 2);
        Player right = TestSupport.swordsman(helper, new ItemStack(Items.IRON_SWORD), -135f, 6);
        Combat.requestParry(defender);
        swing(left, AttackType.SLASH);
        swing(right, AttackType.STAB);
        CombatData d = data(defender);
        check(helper, !isHurt(defender), "the parry should stop both attacks");
        check(helper, data(left).machine.phase() == Phase.STAGGER && data(right).machine.phase() == Phase.STAGGER,
                "both attackers should be parried");
        check(helper, d.machine.parriedHits() == 2, "one parry should have caught 2 hits, caught " + d.machine.parriedHits());
        check(helper, d.machine.phase() == Phase.PARRY && d.machine.isRiposteReady(), "the guard stays up, riposte ready");
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
        // Chivalry 2 ripostes are faster with two-handed weapons (spear: 600 vs 700 ms); one-handers riposte at their
        // normal speed.
        defender.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        Combat.requestParry(defender);
        swing(attacker, AttackType.SLASH);
        CombatData d = data(defender);
        check(helper, Combat.start(defender, d, AttackType.SLASH), "riposte should start");
        int riposteWindup = d.machine.timings().windup();

        TrainingDummy fresh = dummy(helper, 6, 2, FACING_NEGATIVE_X);
        fresh.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        CombatData f = data(fresh);
        Combat.start(fresh, f, AttackType.SLASH);
        check(helper, riposteWindup < f.machine.timings().windup(),
                "riposte windup " + riposteWindup + " should be shorter than " + f.machine.timings().windup());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void heldBlockStaysUpAndDrainsStamina(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        CombatData d = data(player);
        Combat.requestParry(player);
        advance(player, 40);
        check(helper, d.machine.phase() == Phase.PARRY, "a held guard is still up after 2 s, phase " + d.machine.phase());
        float drained = d.stamina.max() - d.stamina.current();
        check(helper, Math.abs(drained - 8) < 0.5, "2 s of holding drains about 8 stamina, drained " + drained);
        Combat.releaseParry(player);
        check(helper, d.machine.phase() != Phase.PARRY, "letting go lowers the guard");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void mobParriesStayTimed(GameTestHelper helper) {
        TrainingDummy dummy = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Combat.requestParry(dummy);
        advance(dummy, 40);
        check(helper, data(dummy).machine.phase() != Phase.PARRY, "a mob's parry drops on its own");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void riposteActiveParryCatchesASecondAttacker(GameTestHelper helper) {
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Player first = TestSupport.swordsman(helper, new ItemStack(Items.IRON_SWORD), -45f, 2);
        Player second = TestSupport.swordsman(helper, new ItemStack(Items.IRON_SWORD), -135f, 6);
        CombatData d = data(defender);
        Combat.requestParry(defender);
        swing(first, AttackType.SLASH);
        check(helper, Combat.start(defender, d, AttackType.SLASH), "riposte should start");
        check(helper, d.machine.isActiveParry(), "a riposte carries an active parry");
        swing(second, AttackType.STAB);
        check(helper, !isHurt(defender), "the active parry stops the second attacker's stab");
        check(helper, data(second).machine.phase() == Phase.STAGGER, "the second attacker is parried");
        check(helper, d.machine.isAttacking(), "the riposte carries on");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void plainAttackHasNoActiveParry(GameTestHelper helper) {
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Combat.start(defender, data(defender), AttackType.OVERHEAD);
        advance(defender, 4); // past the forgiveness window
        swing(attacker, AttackType.STAB);
        check(helper, !data(defender).machine.isActiveParry(), "an ordinary attack has no active parry");
        check(helper, isHurt(defender), "so it gets hit");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void counterActiveParryCatchesASecondAttacker(GameTestHelper helper) {
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        Player first = TestSupport.swordsman(helper, new ItemStack(Items.IRON_SWORD), -45f, 2);
        Player second = TestSupport.swordsman(helper, new ItemStack(Items.IRON_SWORD), -135f, 6);
        CombatData d = data(defender);
        Combat.start(defender, d, AttackType.SLASH);
        swing(first, AttackType.SLASH); // countered: same attack, just started
        check(helper, data(first).machine.phase() == Phase.STAGGER, "the slash is countered");
        check(helper, d.machine.isActiveParry(), "a counter carries an active parry");
        swing(second, AttackType.OVERHEAD);
        check(helper, !isHurt(defender), "the active parry stops the second attacker too");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void wrongCounterRightBeforeImpactIsForgiven(GameTestHelper helper) {
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        CombatData d = data(defender);
        Combat.requestParry(defender);
        check(helper, Combat.start(defender, d, AttackType.OVERHEAD), "attacking from the guard should start");
        swing(attacker, AttackType.SLASH); // the wrong counter, started just before impact
        check(helper, !isHurt(defender), "a wrong counter right before impact falls back to a block");
        check(helper, d.machine.phase() == Phase.PARRY, "the guard is back up, phase " + d.machine.phase());
        check(helper, data(attacker).machine.phase() == Phase.STAGGER, "the attacker is parried");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void lateWrongCounterIsNotForgiven(GameTestHelper helper) {
        Player attacker = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        CombatData d = data(defender);
        Combat.requestParry(defender);
        Combat.start(defender, d, AttackType.OVERHEAD);
        advance(defender, 4); // committed: well past the forgiveness window
        swing(attacker, AttackType.SLASH);
        check(helper, isHurt(defender), "a wrong counter started earlier is just a hit");
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

    // ---- counter-feint (architecture plan section 13.1)

    @GameTest(template = ARENA)
    public static void counterFeintFollowsAFeintedAttack(GameTestHelper helper) {
        // The attacker must be in the level for "an attack is coming" to see it (mock players aren't): a dummy.
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        TrainingDummy attacker = dummy(helper, 1, 4, FACING_POSITIVE_X);
        attacker.setTarget(defender);
        CombatData a = data(attacker);
        CombatData d = data(defender);
        Combat.start(attacker, a, AttackType.SLASH);
        Combat.start(defender, d, AttackType.OVERHEAD);
        check(helper, Combat.morph(defender, d, AttackType.SLASH), "the defender morphs to counter the slash");
        check(helper, Combat.morph(attacker, a, AttackType.STAB), "the attacker feints into a stab");
        check(helper, !Combat.morph(defender, d, AttackType.OVERHEAD), "no counter-feint into an attack nobody is making");
        check(helper, Combat.morph(defender, d, AttackType.STAB), "counter-feint into the stab, although already morphed");
        check(helper, d.machine.isCounterFeinted(), "it was a counter-feint");
        finish(attacker);
        check(helper, a.machine.phase() == Phase.STAGGER, "the stab is countered, attacker phase " + a.machine.phase());
        check(helper, !isHurt(defender), "and the defender isn't hit");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void counterFeintToTheOtherSide(GameTestHelper helper) {
        TrainingDummy defender = dummy(helper, 3, 4, FACING_NEGATIVE_X);
        TrainingDummy attacker = dummy(helper, 1, 4, FACING_POSITIVE_X);
        CombatData d = data(defender);
        Combat.start(attacker, data(attacker), AttackType.SLASH);
        Combat.start(defender, d, AttackType.SLASH, 0, false);
        advance(defender, 3);
        Combat.requestAttack(defender, AttackType.SLASH, 0, true);
        check(helper, d.machine.isMirrored() && d.machine.isCounterFeinted(), "the counter switches sides");
        check(helper, d.machine.phaseTick() == 0, "and its windup starts over: a second chance at the timing");
        helper.succeed();
    }
}
