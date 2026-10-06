package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.check;
import static com.steelclash.gametest.TestSupport.data;
import static com.steelclash.gametest.TestSupport.dummy;

import com.steelclash.SteelClash;
import com.steelclash.ai.ClashSpacingGoal;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.Phase;
import com.steelclash.entity.ModEntities;
import com.steelclash.entity.Soldier;
import com.steelclash.entity.SoldierPatrols;
import com.steelclash.entity.TrainingDummy;
import com.steelclash.profile.WeaponProfiles;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** M6c-2: brigand soldiers, patrols, camp worldgen data. */
@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SoldierGameTests {
    private static final String ARENA = "arena";

    private SoldierGameTests() {
    }

    @GameTest(template = ARENA)
    public static void soldiersSpawnWithRankGear(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        Soldier footman = spawn(helper, ModEntities.FOOTMAN.get().spawn(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), MobSpawnType.SPAWN_EGG));
        Soldier knight = spawn(helper, ModEntities.KNIGHT.get().spawn(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 2)), MobSpawnType.SPAWN_EGG));
        Soldier archer = spawn(helper, ModEntities.ARCHER.get().spawn(helper.getLevel(), helper.absolutePos(new BlockPos(6, 2, 2)), MobSpawnType.SPAWN_EGG));
        check(helper, WeaponProfiles.resolve(footman.getMainHandItem(), access).isPresent(), "footman should carry a melee weapon, has " + footman.getMainHandItem());
        check(helper, !footman.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "footman should wear a helmet");
        check(helper, WeaponProfiles.resolve(knight.getMainHandItem(), access).isPresent(), "knight should carry a melee weapon, has " + knight.getMainHandItem());
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            check(helper, !knight.getItemBySlot(slot).isEmpty(), "knight should wear full armour, missing " + slot);
        }
        check(helper, archer.getMainHandItem().is(Items.BOW), "archer should carry a bow");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void footmanFightsThroughTheBotBrain(GameTestHelper helper) {
        TrainingDummy target = dummy(helper, 6, 4, TestSupport.FACING_NEGATIVE_X);
        Soldier footman = (Soldier) helper.spawn(ModEntities.FOOTMAN.get(), 2, 2, 4);
        footman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        footman.setTarget(target);
        check(helper, footman.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof ClashSpacingGoal),
                "footmen should get the bot spacing goal");
        AtomicBoolean woundUp = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (footman.hasData(ModAttachments.COMBAT) && data(footman).machine.phase() == Phase.WINDUP) {
                woundUp.set(true);
            }
        });
        helper.succeedWhen(() -> check(helper, woundUp.get(), "the footman should attack with telegraphed Steel Clash swings"));
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void archerShoots(GameTestHelper helper) {
        TrainingDummy target = dummy(helper, 7, 4, TestSupport.FACING_NEGATIVE_X);
        Soldier archer = (Soldier) helper.spawn(ModEntities.ARCHER.get(), 1, 2, 4);
        archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        archer.setTarget(target);
        java.util.Set<String> seen = new java.util.TreeSet<>();
        AtomicBoolean arrowHit = new AtomicBoolean();
        int[] drawTicks = {0};
        helper.onEachTick(() -> {
            if (archer.isUsingItem()) {
                drawTicks[0]++;
            }
            var source = target.getLastDamageSource();
            if (source != null && target.hurtTime > 0) {
                seen.add(source.getMsgId() + " by " + (source.getDirectEntity() == null ? "?" : source.getDirectEntity().getType().toShortString()));
                if (source.getDirectEntity() instanceof AbstractArrow) {
                    arrowHit.set(true);
                }
            }
        });
        helper.succeedWhen(() -> check(helper, arrowHit.get(), "the archer should hit the dummy with an arrow [damage seen: "
                + seen + ", bow drawn " + drawTicks[0] + " ticks, archer target " + archer.getTarget()
                + ", distance " + String.format("%.1f", archer.distanceTo(target)) + "]"));
    }

    @GameTest(template = ARENA)
    public static void patrolIsLedByAKnight(GameTestHelper helper) {
        List<Soldier> patrol = SoldierPatrols.spawnPatrol(helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 4)),
                helper.getLevel().getRandom());
        try {
            check(helper, patrol.size() >= 3, "a patrol should have several members, spawned " + patrol.size());
            Soldier leader = patrol.get(0);
            check(helper, leader.rank() == Soldier.Rank.KNIGHT && leader.isPatrolLeader() && leader.hasPatrolTarget(),
                    "the patrol should be led by a knight with a patrol target");
            check(helper, patrol.stream().anyMatch(s -> s.rank() == Soldier.Rank.FOOTMAN), "a patrol should include footmen");
            check(helper, !(leader.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof net.minecraft.world.item.BannerItem),
                    "brigand leaders wear a helmet, not the illager banner");
        } finally {
            patrol.forEach(Soldier::discard);
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void campWorldgenDataLoads(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        check(helper, access.registryOrThrow(Registries.STRUCTURE).containsKey(SteelClash.id("brigand_camp")), "camp structure registered");
        check(helper, access.registryOrThrow(Registries.STRUCTURE_SET).containsKey(SteelClash.id("brigand_camps")), "camp structure set registered");
        check(helper, access.registryOrThrow(Registries.TEMPLATE_POOL).containsKey(SteelClash.id("brigand_camp")), "camp template pool registered");
        check(helper, access.registryOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).containsKey(SteelClash.id("soldier_spawns")),
                "soldier spawn biome modifier registered");
        StructureTemplate template = helper.getLevel().getStructureManager().get(SteelClash.id("brigand_camp")).orElse(null);
        check(helper, template != null && template.getSize().getX() == 13 && template.getSize().getZ() == 13, "camp template loads");
        helper.succeed();
    }

    private static Soldier spawn(GameTestHelper helper, Soldier soldier) {
        if (soldier == null) {
            helper.fail("soldier failed to spawn");
        }
        soldier.setNoAi(true);
        return soldier;
    }
}
