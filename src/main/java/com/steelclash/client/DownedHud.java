package com.steelclash.client;

import com.steelclash.SteelClash;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.Downed;
import com.steelclash.combat.ModAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

/** Downed state text and the revive bar: for the downed player, and for the ally reviving them. */
public final class DownedHud {
    private static final int BAR_WIDTH = 80;

    private DownedHud() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, SteelClash.id("downed"), DownedHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.level == null) {
            return;
        }
        int centerX = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 - 40;
        CombatData own = player.getData(ModAttachments.COMBAT);
        if (own.isDowned()) {
            int seconds = (own.downedTicksLeft + 19) / 20;
            graphics.drawCenteredString(mc.font, Component.translatable("steelclash.downed.you", seconds), centerX, y, 0xFFE05050);
            Player reviver = own.reviverId >= 0 && mc.level.getEntity(own.reviverId) instanceof Player p ? p : null;
            if (reviver != null) {
                graphics.drawCenteredString(mc.font, Component.translatable("steelclash.downed.being_revived",
                        reviver.getDisplayName()), centerX, y + 12, 0xFFFFFFFF);
                bar(graphics, centerX, y + 24, own.reviveTicks);
            } else {
                graphics.drawCenteredString(mc.font, Component.translatable("steelclash.downed.hint"), centerX, y + 12, 0xFFB0B0B0);
            }
            return;
        }
        Player patient = patient(mc, player);
        if (patient != null) {
            CombatData data = patient.getData(ModAttachments.COMBAT);
            graphics.drawCenteredString(mc.font, Component.translatable("steelclash.downed.reviving", patient.getDisplayName()),
                    centerX, y, 0xFFFFFFFF);
            bar(graphics, centerX, y + 12, data.reviveTicks);
        }
    }

    /** The downed player the local player is reviving, if any. */
    @Nullable
    private static Player patient(Minecraft mc, LocalPlayer me) {
        for (Player other : mc.level.players()) {
            if (other != me && other.hasData(ModAttachments.COMBAT)) {
                CombatData data = other.getData(ModAttachments.COMBAT);
                if (data.isDowned() && data.reviverId == me.getId()) {
                    return other;
                }
            }
        }
        return null;
    }

    private static void bar(GuiGraphics graphics, int centerX, int y, int reviveTicks) {
        float fraction = Math.min(1f, reviveTicks / (float) Downed.reviveTicksNeeded());
        int x = centerX - BAR_WIDTH / 2;
        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + 4, 0x90000000);
        graphics.fill(x, y, x + Math.round(BAR_WIDTH * fraction), y + 3, 0xFF60D060);
    }
}
