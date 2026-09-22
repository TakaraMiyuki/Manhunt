package com.example.manhunt.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S：切换技能（左键切卡，客户端左键挥空时发送；打方块/打实体的左键由服务端事件直接处理）。
 */
public record SkillSwitchPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SkillSwitchPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("manhunt", "skill_switch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillSwitchPayload> STREAM_CODEC =
        StreamCodec.unit(new SkillSwitchPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
