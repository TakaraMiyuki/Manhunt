package com.example.manhunt.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S：领取抽奖奖励。index = 待领取列表序号；-1 = 收取全部剩余（右键退出）。
 */
public record ClaimRewardPayload(int index) implements CustomPacketPayload {
    public static final int CLAIM_ALL = -1;

    public static final CustomPacketPayload.Type<ClaimRewardPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("manhunt", "claim_reward"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimRewardPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, ClaimRewardPayload::index, ClaimRewardPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
