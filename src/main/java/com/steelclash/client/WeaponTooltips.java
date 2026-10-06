package com.steelclash.client;

import com.steelclash.SteelClash;
import com.steelclash.profile.WeaponProfile;
import com.steelclash.profile.WeaponProfiles;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Shows how a weapon fights: archetype, damage type and special, e.g. "Sword · Cut · Special: Lunge (R)". */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public final class WeaponTooltips {
    private WeaponTooltips() {
    }

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        WeaponProfiles.resolve(event.getItemStack(), mc.level.registryAccess()).ifPresent(resolved -> {
            WeaponProfile profile = resolved.profile();
            Component line = Component.translatable("steelclash.tooltip.archetype." + profile.archetype())
                    .append(" \u00B7 ")
                    .append(Component.translatable("steelclash.tooltip.damage." + profile.damageType().serializedName()));
            if (profile.special().isPresent()) {
                line = line.copy().append(" \u00B7 ").append(Component.translatable("steelclash.tooltip.special",
                        Component.translatable("steelclash.tooltip.special." + profile.special().get().kind().name().toLowerCase(Locale.ROOT))));
            }
            event.getToolTip().add(Math.min(1, event.getToolTip().size()), line.copy().withStyle(ChatFormatting.GOLD));
        });
    }
}
