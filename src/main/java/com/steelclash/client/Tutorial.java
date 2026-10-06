package com.steelclash.client;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * In-game help: {@code /steelclash_help} lists the controls (with the player's actual bindings) and how to practise
 * on the training dummy. The first time a world is joined, a one-line hint points at it.
 */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public final class Tutorial {
    private Tutorial() {
    }

    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("steelclash_help").executes(context -> {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                showHelp(player);
            }
            return 1;
        }));
    }

    /** Once per install: say the mod changes combat and where the help is, then turn the hint off. */
    @SubscribeEvent
    static void onLogIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!Config.Client.TUTORIAL_HINT.get()) {
            return;
        }
        event.getPlayer().displayClientMessage(Component.translatable("tutorial.steelclash.hint",
                key(ClientInput.SLASH_RIGHT_TO_LEFT), key(ClientInput.PARRY),
                Component.literal("/steelclash_help").withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY), false);
        Config.Client.TUTORIAL_HINT.set(false);
        Config.Client.TUTORIAL_HINT.save();
    }

    static void showHelp(Player player) {
        line(player, Component.translatable("tutorial.steelclash.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        line(player, Component.translatable("tutorial.steelclash.attacks", key(ClientInput.SLASH_RIGHT_TO_LEFT),
                key(ClientInput.SLASH_LEFT_TO_RIGHT), key(ClientInput.OVERHEAD), key(ClientInput.STAB)));
        line(player, Component.translatable("tutorial.steelclash.heavy", key(ClientInput.FEINT)));
        line(player, Component.translatable("tutorial.steelclash.parry", key(ClientInput.PARRY)));
        line(player, Component.translatable("tutorial.steelclash.counter"));
        line(player, Component.translatable("tutorial.steelclash.kick", key(ClientInput.KICK), key(ClientInput.JAB),
                key(ClientInput.DODGE), key(ClientInput.SPECIAL), key(ClientInput.THROW)));
        line(player, Component.translatable("tutorial.steelclash.stamina"));
        line(player, Component.translatable("tutorial.steelclash.dummy"));
        line(player, Component.translatable("tutorial.steelclash.settings").withStyle(ChatFormatting.GRAY));
    }

    private static void line(Player player, Component text) {
        player.displayClientMessage(text, false);
    }

    private static MutableComponent key(KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.AQUA);
    }
}
