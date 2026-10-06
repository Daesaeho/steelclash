package com.steelclash.client;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.TimingBar;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Timing bar under the stamina bar: how long your current action lasts. Heavy charge (with a tick where it becomes a
 * heavy), windup, the live release, recovery (green while a combo is available), parry duration, guard recovery,
 * stagger, and the riposte window. Reads the locally predicted state machine; no extra networking.
 */
public final class TimingHud {
    private static final int WIDTH = 40;
    private static final int HEIGHT = 2;
    /** Just below the stamina bar (which sits 10px under the crosshair). */
    private static final int OFFSET_BELOW_CROSSHAIR = 15;

    private TimingHud() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(SteelClash.id("stamina"), SteelClash.id("timing"), TimingHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || !Config.Client.TIMING_HUD.get() || !player.hasData(ModAttachments.COMBAT)) {
            return;
        }
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        TimingBar bar = TimingBar.of(player.getData(ModAttachments.COMBAT).machine, partialTick,
                ClientInput.isChargingHeavy(), Combat.HEAVY_HOLD_TICKS, Config.RIPOSTE_WINDOW_TICKS.get());
        if (bar == null) {
            return;
        }
        int x = graphics.guiWidth() / 2 - WIDTH / 2;
        int y = graphics.guiHeight() / 2 + OFFSET_BELOW_CROSSHAIR;
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0x90000000);
        graphics.fill(x, y, x + (int) Math.round(WIDTH * bar.fraction()), y + HEIGHT, color(bar.kind()));
        if (bar.marker() >= 0) {
            int mx = x + (int) Math.round(WIDTH * bar.marker()) - 1;
            graphics.fill(mx, y - 2, mx + 1, y + HEIGHT + 2, 0xFFFFFFFF);
        }
    }

    static int color(TimingBar.Kind kind) {
        return switch (kind) {
            case HEAVY_CHARGE -> 0xFFB0B0B0;
            case WINDUP -> 0xFFF0D040;
            case HEAVY_WINDUP -> 0xFFF08020;
            case RELEASE -> 0xFFE03030;
            case RECOVERY -> 0xFF808080;
            case COMBO -> 0xFF50D050;
            case PARRY -> 0xFF4080FF;
            case GUARD_RECOVERY -> 0xFF304878;
            case STAGGER -> 0xFFD040D0;
            case RIPOSTE -> 0xFFFFFFFF;
        };
    }
}
