package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_NEGATIVE_X;
import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.advance;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.face;

import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.combat.RangedDefense;
import com.steelclash.combat.RangedStamina;
import com.steelclash.compat.Compat;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chivalry 2 projectile rules: counter, weapon block, headshots, interrupted draws ({@link RangedDefense}). */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RangedGameTests {
    private static final String ARENA = "arena";
    private static final float ARROW_DAMAGE = 8f;

    private RangedGameTests() {
    }

    /** A swordsman zombie at (3, 2, z) facing -x, standing still. */
    private static Zombie swordsman(GameTestHelper helper, int z) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, z);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        face(zombie, FACING_NEGATIVE_X);
        return zombie;
    }

    /**
     * Hurts {@code target} with an arrow flying along -x... toward it from {@code fromFront} (else from behind), at
     * {@code height} above its feet; returns the health it lost.
     */
    private static float shoot(GameTestHelper helper, LivingEntity target, boolean fromFront, double height) {
        double side = fromFront ? -1.5 : 1.5; // the zombies face -x, so the front is at lower x
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setPos(target.getX() + side, target.getY() + height, target.getZ());
        arrow.setDeltaMovement(new Vec3(-side, 0, 0));
        float before = target.getHealth();
        target.invulnerableTime = 0;
        target.hurt(helper.getLevel().damageSources().arrow(arrow, null), ARROW_DAMAGE);
        return before - target.getHealth();
    }

    @GameTest(template = ARENA)
    public static void attackStartedJustBeforeDeflectsAnArrow(GameTestHelper helper) {
        Zombie zombie = swordsman(helper, 2);
        CombatData d = data(zombie);
        Combat.requestAttack(zombie, AttackType.SLASH);
        check(helper, d.machine.phase() == Phase.WINDUP, "winding up");
        check(helper, shoot(helper, zombie, true, 1.0) == 0, "an arrow met at the start of a windup is deflected");
        check(helper, d.machine.phase() == Phase.WINDUP, "the attack carries on");

        Zombie behind = swordsman(helper, 4);
        Combat.requestAttack(behind, AttackType.SLASH);
        check(helper, shoot(helper, behind, false, 1.0) > 0, "an arrow from behind isn't countered");

        Zombie late = swordsman(helper, 6);
        CombatData l = data(late);
        Combat.requestAttack(late, AttackType.SLASH);
        for (int tick = 0; tick < 6; tick++) {
            Combat.tickServer(late, l); // 300 ms in: past the 250 ms window
        }
        check(helper, l.machine.phase() == Phase.WINDUP, "still winding up");
        check(helper, shoot(helper, late, true, 1.0) > 0, "too late into the windup: hit");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void weaponGuardBluntsAnArrowButNeverBreaks(GameTestHelper helper) {
        Zombie open = swordsman(helper, 2);
        float unguarded = shoot(helper, open, true, 1.0);

        Zombie guarded = swordsman(helper, 4);
        CombatData d = data(guarded);
        check(helper, Combat.startParry(guarded, d), "guard up");
        d.stamina.spend(d.stamina.current() - 1); // one stamina left: a melee block would break this guard
        float blocked = shoot(helper, guarded, true, 1.0);
        check(helper, blocked > 0 && blocked < unguarded * 0.85,
                "the guard takes about 30% off: " + blocked + " vs " + unguarded + " unguarded");
        check(helper, guarded.getMainHandItem().is(Items.IRON_SWORD) && d.machine.phase() == Phase.PARRY,
                "arrows never disarm or break the guard: " + d.machine.phase());

        Zombie turned = swordsman(helper, 6);
        CombatData t = data(turned);
        Combat.startParry(turned, t);
        check(helper, shoot(helper, turned, false, 1.0) == unguarded, "a guard doesn't cover the back");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void headshotsHitHarder(GameTestHelper helper) {
        Villager body = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 3, 2, 2);
        Villager head = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 3, 2, 5);
        float toBody = shoot(helper, body, true, body.getBbHeight() * 0.5);
        float toHead = shoot(helper, head, true, head.getEyeHeight());
        check(helper, Math.abs(toBody - ARROW_DAMAGE) < 0.01, "a body shot does its damage: " + toBody);
        check(helper, Math.abs(toHead - ARROW_DAMAGE * 1.25f) < 0.01, "a headshot does 25% more: " + toHead);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void gettingHurtDropsABowDraw(GameTestHelper helper) {
        Skeleton skeleton = helper.spawnWithNoFreeWill(EntityType.SKELETON, 3, 2, 3);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.startUsingItem(InteractionHand.MAIN_HAND);
        check(helper, skeleton.isUsingItem(), "drawing");
        skeleton.hurt(helper.getLevel().damageSources().generic(), 1f);
        check(helper, !skeleton.isUsingItem(), "the hit should drop the draw");
        helper.succeed();
    }

    // ------------------------------------------------------------------ archery costs stamina (RangedStamina)

    private static float spent(CombatData d) {
        return d.stamina.max() - d.stamina.current();
    }

    @GameTest(template = ARENA)
    public static void drawingABowDrainsStamina(GameTestHelper helper) {
        Player archer = TestSupport.swordsman(helper, new ItemStack(Items.BOW), FACING_POSITIVE_X);
        CombatData d = data(archer);
        archer.startUsingItem(InteractionHand.MAIN_HAND);
        check(helper, archer.isUsingItem(), "premise: drawing");
        advance(archer, 20);
        check(helper, Math.abs(spent(d) - 12) < 0.01, "a second at the draw costs 12 stamina, cost " + spent(d));
        advance(archer, 40);
        check(helper, Math.abs(spent(d) - 36) < 0.01, "held drawn it keeps draining and never regenerates, cost " + spent(d));
        archer.stopUsingItem();
        advance(archer, 60);
        check(helper, spent(d) == 0, "lowered, the stamina comes back");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void anExhaustedArcherLowersTheBowAndCannotDrawAgain(GameTestHelper helper) {
        Player archer = TestSupport.swordsman(helper, new ItemStack(Items.BOW), FACING_POSITIVE_X);
        CombatData d = data(archer);
        d.stamina.set(1); // two ticks of draw
        archer.startUsingItem(InteractionHand.MAIN_HAND);
        advance(archer, 1);
        check(helper, archer.isUsingItem(), "still drawing while there is stamina");
        advance(archer, 1);
        check(helper, !archer.isUsingItem() && d.stamina.isExhausted(), "out of stamina the bow comes down");
        archer.startUsingItem(InteractionHand.MAIN_HAND);
        check(helper, !archer.isUsingItem(), "no drawing while exhausted");
        d.stamina.set(d.stamina.max());
        archer.startUsingItem(InteractionHand.MAIN_HAND);
        check(helper, archer.isUsingItem(), "recovered: the bow can be drawn again");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void loadingACrossbowAndReadyingAThrowDrainStamina(GameTestHelper helper) {
        Player crossbowman = TestSupport.swordsman(helper, new ItemStack(Items.CROSSBOW), FACING_POSITIVE_X, 2);
        crossbowman.startUsingItem(InteractionHand.MAIN_HAND);
        advance(crossbowman, 10);
        check(helper, Math.abs(spent(data(crossbowman)) - 6) < 0.01,
                "half a second of loading costs 6 stamina, cost " + spent(data(crossbowman)));
        crossbowman.stopUsingItem(); // loaded or not, a crossbow that isn't being loaded costs nothing
        float carrying = spent(data(crossbowman));
        advance(crossbowman, 10);
        check(helper, spent(data(crossbowman)) == carrying, "a crossbow in hand costs nothing");

        Player thrower = TestSupport.swordsman(helper, new ItemStack(Items.TRIDENT), FACING_POSITIVE_X, 6);
        thrower.startUsingItem(InteractionHand.MAIN_HAND);
        advance(thrower, 20);
        check(helper, Math.abs(spent(data(thrower)) - 12) < 0.01,
                "a second with the trident held back costs 12 stamina, cost " + spent(data(thrower)));
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void otherItemUseCostsNoStamina(GameTestHelper helper) {
        Player eater = TestSupport.swordsman(helper, new ItemStack(Items.BREAD), FACING_POSITIVE_X, 2);
        eater.startUsingItem(InteractionHand.MAIN_HAND);
        Player shielded = TestSupport.swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X, 6);
        shielded.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        shielded.startUsingItem(InteractionHand.OFF_HAND);
        check(helper, eater.isUsingItem() && shielded.isUsingItem(), "premise: eating, and holding a shield up");
        advance(eater, 20);
        advance(shielded, 20);
        check(helper, spent(data(eater)) == 0 && spent(data(shielded)) == 0, "only readying a ranged weapon drains stamina");
        check(helper, RangedStamina.of(new ItemStack(Items.BOW)) == RangedStamina.Draw.BOW
                        && RangedStamina.of(new ItemStack(Items.CROSSBOW)) == RangedStamina.Draw.CROSSBOW
                        && RangedStamina.of(new ItemStack(Items.TRIDENT)) == RangedStamina.Draw.THROW
                        && RangedStamina.of(new ItemStack(Items.SHIELD)) == null
                        && RangedStamina.of(new ItemStack(Items.SPYGLASS)) == null,
                "bows, crossbows and tridents are told apart by their use animation");
        helper.succeed();
    }

    /** Lowered, not released: a crossbow whose loading runs out of stamina after the full loading time is still empty. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void runningOutOfStaminaLosesACrossbowLoad(GameTestHelper helper) {
        Zombie loader = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        loader.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in daylight
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        loader.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        data(loader).stamina.set(15.5f); // gone on the 26th tick, one past the 25 a crossbow needs
        loader.startUsingItem(InteractionHand.MAIN_HAND);
        AtomicInteger longest = new AtomicInteger();
        helper.onEachTick(() -> longest.set(Math.max(longest.get(), loader.getTicksUsingItem())));
        helper.runAfterDelay(35, () -> {
            check(helper, longest.get() >= 25, "premise: it loaded for long enough to finish, " + longest.get() + " ticks");
            check(helper, !loader.isUsingItem() && data(loader).stamina.isExhausted(), "out of stamina the crossbow comes down");
            check(helper, !CrossbowItem.isCharged(crossbow), "and it is not loaded: the load was lost, not released");
            helper.succeed();
        });
    }

    /** Mobs fight by the same rules, but only fighters have stamina: a mob outside the roster gets no combat state from it. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void fighterMobsPayForTheirDrawToo(GameTestHelper helper) {
        Skeleton skeleton = helper.spawnWithNoFreeWill(EntityType.SKELETON, 3, 2, 2);
        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        Pillager pillager = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 3, 2, 6);
        pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        helper.runAfterDelay(2, () -> { // after their first tick: the skeleton has its combat state
            skeleton.startUsingItem(InteractionHand.MAIN_HAND);
            pillager.startUsingItem(InteractionHand.MAIN_HAND);
            float before = spent(data(skeleton));
            helper.runAfterDelay(20, () -> {
                float cost = spent(data(skeleton)) - before;
                check(helper, cost > 10 && cost < 14, "a skeleton's second at the draw costs it about 12 stamina, cost " + cost);
                check(helper, !pillager.hasData(ModAttachments.COMBAT),
                        "a mob that isn't a fighter gets no combat state from using a crossbow");
                helper.succeed();
            });
        });
    }

    /** Spartan Weaponry's ranged weapons report vanilla use animations, so they follow the same rule. */
    @GameTest(template = ARENA)
    public static void spartanRangedWeaponsFollowTheSameRule(GameTestHelper helper) {
        if (!Compat.SPARTAN_WEAPONRY.isLoaded()) {
            helper.succeed();
            return;
        }
        int bows = 0;
        int crossbows = 0;
        int thrown = 0;
        for (var id : BuiltInRegistries.ITEM.keySet()) {
            if (!id.getNamespace().equals(Compat.SPARTAN_WEAPONRY.modId())) {
                continue;
            }
            RangedStamina.Draw draw = RangedStamina.of(new ItemStack(BuiltInRegistries.ITEM.get(id)));
            String path = id.getPath();
            if (path.contains("heavy_crossbow")) {
                check(helper, draw == RangedStamina.Draw.CROSSBOW, id + " should load like a crossbow, is " + draw);
                crossbows++;
            } else if (path.contains("longbow")) {
                check(helper, draw == RangedStamina.Draw.BOW, id + " should draw like a bow, is " + draw);
                bows++;
            } else if (path.contains("javelin") || path.contains("throwing_knife") || path.contains("tomahawk")
                    || path.contains("boomerang")) {
                check(helper, draw == RangedStamina.Draw.THROW, id + " should charge like a thrown weapon, is " + draw);
                thrown++;
            }
        }
        check(helper, bows > 0 && crossbows > 0 && thrown > 0,
                "found " + bows + " longbows, " + crossbows + " heavy crossbows, " + thrown + " throwing weapons");
        helper.succeed();
    }
}
