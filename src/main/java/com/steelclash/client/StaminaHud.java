package com.steelclash.client;

import com.steelclash.SteelClash;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * A thin stamina bar under the crosshair, shown while stamina isn't full. Turns red when low and flashes white while
 * a riposte is ready. Placeholder art; proper HUD sprites later.
 */
public final class StaminaHud {
    private static final int WIDTH = 40;
    private static final int HEIGHT = 2;
    private static final int OFFSET_BELOW_CROSSHAIR = 10;

    private StaminaHud() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, SteelClash.id("stamina"), StaminaHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || !player.hasData(ModAttachments.COMBAT)) {
            return;
        }
        CombatData data = player.getData(ModAttachments.COMBAT);
        float fraction = data.stamina.current() / data.stamina.max();
        boolean riposte = data.machine.isRiposteReady();
        if (fraction >= 0.999f && !riposte) {
            return;
        }
        int x = graphics.guiWidth() / 2 - WIDTH / 2;
        int y = graphics.guiHeight() / 2 + OFFSET_BELOW_CROSSHAIR;
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0x90000000);
        int color = riposte ? 0xFFFFFFFF : fraction < 0.25f ? 0xFFD04040 : 0xFFE8C860;
        graphics.fill(x, y, x + Math.round(WIDTH * fraction), y + HEIGHT, color);
    }
}
