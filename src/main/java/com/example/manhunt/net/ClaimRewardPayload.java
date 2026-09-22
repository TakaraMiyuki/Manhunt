package com.example.manhunt.net;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S：领取抽奖奖励——提交已标记的物品索引（右键）。
 * 服务端发放全部标记物品并关闭该轮待领取，未标记物品丢弃。
 */
public record ClaimRewardPayload(List<Integer> indices) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClaimRewardPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("manhunt", "claim_reward"));

    /** 手写编解码：count + VAR_INT ×n（VAR_INT 与列表的 B 类型不同，无法用 composite）。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimRewardPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            ByteBufCodecs.VAR_INT.encode(buf, payload.indices().size());
            for (int index : payload.indices()) {
                ByteBufCodecs.VAR_INT.encode(buf, index);
            }
        },
        buf -> {
            int count = ByteBufCodecs.VAR_INT.decode(buf);
            List<Integer> indices = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                indices.add(ByteBufCodecs.VAR_INT.decode(buf));
            }
            return new ClaimRewardPayload(indices);
        });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
