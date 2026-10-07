package com.steelclash.net;

import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: block (weapon parry) pressed or released. */
public record BlockInputPayload(boolean down) implements CustomPacketPayload {
    public static final Type<BlockInputPayload> TYPE = new Type<>(SteelClash.id("block_input"));

    public static final StreamCodec<ByteBuf, BlockInputPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(BlockInputPayload::new, BlockInputPayload::down);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(BlockInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!player.isAlive() || player.isSpectator()) {
                return;
            }
            if (payload.down()) {
                if (!ModNetwork.allowInput(player)) {
                    return; // releasing a guard always goes through, so it can't get stuck up
                }
                if (player.isUsingItem()) {
                    player.stopUsingItem();
                }
                Combat.requestParry(player);
            } else {
                Combat.releaseParry(player);
            }
        });
    }
}
