package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.FACING_POSITIVE_X;
import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.finish;
import static com.steelclash.gametest.TestSupport.isHurt;
import static com.steelclash.gametest.TestSupport.swing;
import static com.steelclash.gametest.TestSupport.swordsman;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** M6c-1: weapon specials, throwing, damage types versus armour. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MechanicsGameTests {
    private static final String ARENA = "arena";

    private MechanicsGameTests() {
    }

    @GameTest(template = ARENA)
    public static void swordLungeOutreachesAStab(GameTestHelper helper) {
        Player stabber = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X, 2);
        Player lunger = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X, 6);
        Zombie farA = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5, 2, 2);
        Zombie farB = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 5, 2, 6);
        swing(stabber, AttackType.STAB);
        swing(lunger, AttackType.SPECIAL);
        check(helper, !isHurt(farA), "a plain stab should fall short");
        check(helper, isHurt(farB), "the sword's lunge special should reach");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void specialsHaveACooldown(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        swing(player, AttackType.SPECIAL);
        check(helper, data(player).machine.phase() == Phase.IDLE, "the first special finishes");
        Combat.requestAttack(player, AttackType.SPECIAL);
        check(helper, data(player).machine.phase() == Phase.IDLE, "a second special right away is refused (cooldown)");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void maceSlamStaggersEveryoneAround(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.MACE), FACING_POSITIVE_X);
        // The slam lands 2 blocks ahead; these two stand either side of the impact.
        Zombie left = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 3);
        Zombie right = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 5);
        swing(player, AttackType.SPECIAL);
        check(helper, data(left).machine.phase() == Phase.STAGGER && data(right).machine.phase() == Phase.STAGGER,
                "the slam should stagger both zombies near the impact");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void thrownSwordHitsAndDrops(GameTestHelper helper) {
        Player player = swordsman(helper, new ItemStack(Items.IRON_SWORD), FACING_POSITIVE_X);
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 6, 2, 4);
        target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // don't burn in daylight
        swing(player, AttackType.THROW);
        check(helper, player.getMainHandItem().isEmpty(), "the sword should leave the hand");
        helper.succeedWhen(() -> {
            check(helper, isHurt(target), "the thrown sword should hit the zombie 4.5 blocks away");
            // Make sure it was the sword, not something stray from a neighbouring test (e.g. an arrow).
            var source = target.getLastDamageSource();
            check(helper, source != null && source.getDirectEntity() instanceof com.steelclash.entity.ThrownWeapon,
                    "the damage should come from the thrown weapon, was " + (source == null ? "none" : source.getMsgId()));
            boolean dropped = !helper.getLevel().getEntitiesOfClass(ItemEntity.class, target.getBoundingBox().inflate(3),
                    e -> e.getItem().is(Items.IRON_SWORD)).isEmpty();
            check(helper, dropped, "the sword should drop where it landed");
        });
    }

    @GameTest(template = ARENA)
    public static void cutsGlanceOffPlate(GameTestHelper helper) {
        Zombie plainTarget = armouredZombie(helper, 2);
        Zombie typedTarget = armouredZombie(helper, 6);
        // Armour attributes only apply once the mob has ticked with the gear on.
        helper.runAfterDelay(3, () -> {
            check(helper, typedTarget.getArmorValue() >= 16, "full diamond should count as heavy armour, was " + typedTarget.getArmorValue());
            boolean original = Config.DAMAGE_TYPES.get();
            try {
                Config.DAMAGE_TYPES.set(false);
                float plain = overheadDamage(helper, plainTarget, 2);
                Config.DAMAGE_TYPES.set(true);
                float typed = overheadDamage(helper, typedTarget, 6);
                check(helper, plain > 0 && typed < plain * 0.85,
                        "a sword (cut) should do clearly less to full diamond with damage types on: " + typed + " vs " + plain);
            } finally {
                Config.DAMAGE_TYPES.set(original);
            }
            helper.succeed();
        });
    }

    private static Zombie armouredZombie(GameTestHelper helper, int row) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, row);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        return zombie;
    }

    private static float overheadDamage(GameTestHelper helper, Zombie zombie, int row) {
        Player player = swordsman(helper, new ItemStack(Items.DIAMOND_SWORD), FACING_POSITIVE_X, row);
        Combat.requestAttack(player, AttackType.OVERHEAD, 0, false);
        finish(player);
        return zombie.getMaxHealth() - zombie.getHealth();
    }
}
