package com.example.manhunt.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S：领取抽奖奖励。
 * action = 0：领取 index 对应的一项，其余保留在待领取列表（中键/回车）。
 * action = 1：领取 index 对应的一项并退出选择阶段，丢弃未选中的物品（右键）。
 */
public record ClaimRewardPayload(int index, int action) implements CustomPacketPayload {
    public static final int ACTION_CLAIM_ONE = 0;
    public static final int ACTION_CLAIM_AND_EXIT = 1;

    public static final CustomPacketPayload.Type<ClaimRewardPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("manhunt", "claim_reward"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimRewardPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ClaimRewardPayload::index,
        ByteBufCodecs.VAR_INT, ClaimRewardPayload::action,
        ClaimRewardPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
