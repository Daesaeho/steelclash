package com.steelclash;

import com.mojang.logging.LogUtils;
import com.steelclash.ai.BotStyles;
import com.steelclash.combat.ModAttachments;
import com.steelclash.compat.Compat;
import com.steelclash.compat.SpartanWeaponryCompat;
import com.steelclash.entity.ModEntities;
import com.steelclash.net.ModNetwork;
import com.steelclash.profile.WeaponProfiles;
import com.steelclash.sound.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(SteelClash.MOD_ID)
public class SteelClash {
    public static final String MOD_ID = "steelclash";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SteelClash(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(WeaponProfiles::registerRegistry);
        modEventBus.addListener(WeaponProfiles::registerDataMap);
        modEventBus.addListener(BotStyles::registerDataMap);
        modEventBus.addListener(ModNetwork::register);
        ModAttachments.register(modEventBus);
        ModEntities.register(modEventBus);
        ModSounds.register(modEventBus);

        // Combat prediction must use the server's rules; retain the existing global config file.
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC, "steelclash-common.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.Client.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        if (Compat.SPARTAN_WEAPONRY.isLoaded()) {
            SpartanWeaponryCompat.init();
        }
        LOGGER.info("Steel Clash loaded. Spartan Weaponry: {}, Spartan Shields: {}, Shoulder Surfing: {}",
                Compat.SPARTAN_WEAPONRY.isLoaded(), Compat.SPARTAN_SHIELDS.isLoaded(), Compat.SHOULDER_SURFING.isLoaded());
    }
}
