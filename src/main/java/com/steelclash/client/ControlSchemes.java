package com.steelclash.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.steelclash.Config;
import com.steelclash.SteelClash;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Applies the client config's control scheme to the key bindings, once each time it changes (including the first launch
 * with this version). Afterwards the keys are the player's to rebind; the scheme isn't re-applied until it changes again.
 * <ul>
 *     <li>CHIVALRY: parry on right click, second slash key unbound.</li>
 *     <li>TWO_SLASH_KEYS: second slash (left to right) on right click, parry on middle click.</li>
 * </ul>
 */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public final class ControlSchemes {
    /** Remembers which scheme the key bindings were last set up for. */
    private static final Path APPLIED = FMLPaths.CONFIGDIR.get().resolve("steelclash-controls-applied.txt");
    private static boolean checked;

    private ControlSchemes() {
    }

    /** Mod-bus listener (registered from {@code SteelClashClient}): the config screen or file changed. */
    public static void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(SteelClash.MOD_ID) && event.getConfig().getType() == ModConfig.Type.CLIENT) {
            checked = false;
        }
    }

    /** Checked on a tick, once the game options (which own the key bindings) are loaded. */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (checked || mc.options == null) {
            return;
        }
        checked = true;
        Config.Client.ControlScheme scheme = Config.Client.CONTROL_SCHEME.get();
        if (scheme.name().equals(readApplied())) {
            return;
        }
        apply(mc, scheme);
        writeApplied(scheme.name());
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("controls.steelclash.scheme_applied." + scheme.name().toLowerCase()), false);
        }
    }

    private static void apply(Minecraft mc, Config.Client.ControlScheme scheme) {
        InputConstants.Key right = InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        InputConstants.Key middle = InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        switch (scheme) {
            case CHIVALRY -> {
                mc.options.setKey(ClientInput.PARRY, right);
                mc.options.setKey(ClientInput.SLASH_LEFT_TO_RIGHT, InputConstants.UNKNOWN);
            }
            case TWO_SLASH_KEYS -> {
                mc.options.setKey(ClientInput.SLASH_LEFT_TO_RIGHT, right);
                mc.options.setKey(ClientInput.PARRY, middle);
            }
        }
        KeyMapping.resetMapping();
        mc.options.save();
        SteelClash.LOGGER.info("Applied the {} control scheme to the key bindings", scheme);
    }

    private static String readApplied() {
        try {
            return Files.exists(APPLIED) ? Files.readString(APPLIED).trim() : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeApplied(String scheme) {
        try {
            Files.writeString(APPLIED, scheme);
        } catch (IOException e) {
            SteelClash.LOGGER.warn("Couldn't record the applied control scheme", e);
        }
    }
}
