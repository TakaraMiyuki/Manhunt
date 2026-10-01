package com.example.manhunt.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * C2S：右键释放当前选中的技能（Alt 技能栏模式）。
 */
public record SkillUsePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SkillUsePayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("manhunt", "skill_use"));

    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, SkillUsePayload> STREAM_CODEC =
        StreamCodec.unit(new SkillUsePayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
