package com.steelclash.gametest;

import static com.steelclash.gametest.TestSupport.check;

import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.ConfigSync;

@GameTestHolder(SteelClash.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConfigSyncGameTests {
    private ConfigSyncGameTests() {}

    @GameTest(template = "arena")
    public static void combatRulesUseTheExistingFileInLoginSync(GameTestHelper helper) throws IOException {
        var config = ModConfigs.getFileMap().get("steelclash-common.toml");
        check(helper, config != null && config.getModId().equals(SteelClash.MOD_ID),
                "keep the existing combat config filename");
        check(helper, config.getLoadedConfig() != null, "combat rules load before the world starts");

        // Exercise NeoForge's actual login selection/serialization, not a duplicate implementation.
        // ConfigSync is an internal API used only by this integration test, never by combat code.
        var payloads = ConfigSync.syncConfigs();
        var payload = payloads.stream().filter(p -> p.fileName().equals(config.getFileName())).findFirst();
        check(helper, payload.isPresent(), "login sync includes the combat rules");
        check(helper, payloads.stream().noneMatch(p -> p.fileName().equals("steelclash-client.toml")),
                "controls and camera settings stay local");
        check(helper, Arrays.equals(payload.get().contents(), Files.readAllBytes(config.getFullPath())),
                "send the active global file or world override unchanged");
        var sent = new TomlParser().parse(new String(payload.get().contents(), StandardCharsets.UTF_8));
        check(helper, ((Number) sent.get("stamina.max")).doubleValue() == Config.MAX_STAMINA.get(),
                "the file's stamina setting is the loaded gameplay setting");
        check(helper, sent.get("defense.blockMode").equals(Config.BLOCK_MODE.get().name()),
                "the file's guard setting is the loaded gameplay setting");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void receivedCombatRulesReplaceCachedValuesWithoutSavingThemLocally(GameTestHelper helper)
            throws IOException {
        var config = ModConfigs.getFileMap().get("steelclash-common.toml");
        check(helper, config != null && config.getType() == ModConfig.Type.SERVER, "combat rules are server scoped");
        var originalPath = config.getFullPath();
        byte[] originalBytes = Files.readAllBytes(originalPath);
        double originalMax = Config.MAX_STAMINA.get();
        var originalMode = Config.BLOCK_MODE.get(); // Prime the getter caches before receiving different rules.
        var mode = originalMode == Config.BlockMode.HELD ? Config.BlockMode.TIMED : Config.BlockMode.HELD;
        var received = new TomlParser().parse(new String(originalBytes, StandardCharsets.UTF_8));
        double max = originalMax == 173.0 ? 174.0 : 173.0;
        received.set("stamina.max", max);
        received.set("defense.blockMode", mode.name());
        received.set("defense.parryCooldownTicks", 17);
        received.set("offense.dodgeStaminaCost", 27.0);
        received.set("offense.turnCapDegreesPerSecond", 137.0);
        try {
            // Public receiver used by NeoForge's config packet handler; no socket is negotiated here.
            ConfigTracker.acceptSyncedConfig(config,
                    new TomlWriter().writeToString(received).getBytes(StandardCharsets.UTF_8));
            check(helper, Config.MAX_STAMINA.get() == max && Config.BLOCK_MODE.get() == mode,
                    "received values replace already-cached local values");
            check(helper, Config.PARRY_COOLDOWN_TICKS.get() == 17 && Config.DODGE_STAMINA_COST.get() == 27.0
                    && Config.TURN_CAP.get() == 137.0, "prediction reads the received combat rules");
            check(helper, new CombatData().stamina.max() == (float) max, "new combatants use the received stamina pool");
            check(helper, Combat.heldBlock(helper.makeMockPlayer(GameType.SURVIVAL)) == (mode == Config.BlockMode.HELD),
                    "guard behavior uses the received mode");
            config.getLoadedConfig().save();
            check(helper, Arrays.equals(originalBytes, Files.readAllBytes(originalPath)),
                    "receiving and saving a session config does not overwrite the local file");
        } finally {
            // This test is synchronous: no world tick runs under the temporary rules. Reload through FML so
            // the backing paths and file watchers, as well as the specs, are restored for subsequent tests.
            ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, FMLPaths.CONFIGDIR.get(),
                    helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("serverconfig"));
        }
        check(helper, Files.isSameFile(originalPath, config.getFullPath()) && Config.MAX_STAMINA.get() == originalMax
                && Config.BLOCK_MODE.get() == originalMode, "restore the original file and gameplay rules");
        helper.succeed();
    }
}
