package com.steelclash.entity;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Brigand patrols, modelled on vanilla's pillager {@code PatrolSpawner}: every so often, near a random player in the
 * overworld (not in a village), a knight leads footmen and archers toward them using vanilla patrol AI. Respects the
 * {@code doPatrolSpawning} game rule.
 */
@EventBusSubscriber(modid = SteelClash.MOD_ID)
public final class SoldierPatrols {
    private static int nextTick = 2400;

    private SoldierPatrols() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (!Config.SOLDIER_PATROLS.get() || !level.getGameRules().getBoolean(GameRules.RULE_DO_PATROL_SPAWNING)
                || level.getDifficulty().getId() == 0) {
            return;
        }
        if (--nextTick > 0) {
            return;
        }
        RandomSource random = level.random;
        int interval = Config.SOLDIER_PATROL_INTERVAL_TICKS.get();
        nextTick = interval + random.nextInt(Math.max(1, interval / 10));
        if (random.nextDouble() >= Config.SOLDIER_PATROL_CHANCE.get() || level.players().isEmpty()) {
            return;
        }
        Player player = level.players().get(random.nextInt(level.players().size()));
        if (player.isSpectator() || level.isCloseToVillage(player.blockPosition(), 2)) {
            return;
        }
        int dx = (24 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
        int dz = (24 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
        spawnPatrol(level, player.blockPosition().offset(dx, 0, dz), random);
    }

    /** Spawns a patrol near {@code origin}: a knight leader plus footmen and archers (more on harder difficulty). */
    public static List<Soldier> spawnPatrol(ServerLevel level, BlockPos origin, RandomSource random) {
        List<Soldier> spawned = new ArrayList<>();
        BlockPos.MutableBlockPos pos = origin.mutable();
        if (!level.hasChunksAt(pos.getX() - 10, pos.getZ() - 10, pos.getX() + 10, pos.getZ() + 10)
                || level.getBiome(pos).is(BiomeTags.WITHOUT_PATROL_SPAWNS)) {
            return spawned;
        }
        int difficulty = level.getDifficulty().getId();
        List<EntityType<Soldier>> members = new ArrayList<>();
        members.add(ModEntities.KNIGHT.get());
        for (int i = 0; i < 1 + difficulty; i++) {
            members.add(ModEntities.FOOTMAN.get());
        }
        for (int i = 0; i < 1 + difficulty / 2; i++) {
            members.add(ModEntities.ARCHER.get());
        }
        for (int i = 0; i < members.size(); i++) {
            pos.setY(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos).getY());
            Soldier soldier = spawnMember(level, members.get(i), pos, i == 0);
            if (soldier == null && i == 0) {
                return spawned; // no room for the leader: no patrol
            }
            if (soldier != null) {
                spawned.add(soldier);
            }
            pos.setX(pos.getX() + random.nextInt(5) - random.nextInt(5));
            pos.setZ(pos.getZ() + random.nextInt(5) - random.nextInt(5));
        }
        return spawned;
    }

    private static Soldier spawnMember(ServerLevel level, EntityType<Soldier> type, BlockPos pos, boolean leader) {
        BlockState state = level.getBlockState(pos);
        if (!NaturalSpawner.isValidEmptySpawnBlock(level, pos, state, state.getFluidState(), type)) {
            return null;
        }
        Soldier soldier = type.create(level);
        if (soldier == null) {
            return null;
        }
        if (leader) {
            soldier.setPatrolLeader(true);
            soldier.findPatrolTarget();
        }
        soldier.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        soldier.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.PATROL, null);
        level.addFreshEntityWithPassengers(soldier);
        return soldier;
    }
}
