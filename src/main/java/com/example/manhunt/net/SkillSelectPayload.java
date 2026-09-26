package com.example.manhunt.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S：技能轮盘选定某张卡（下标），服务端切换到该卡。
 */
public record SkillSelectPayload(int index) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SkillSelectPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("manhunt", "skill_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillSelectPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> ByteBufCodecs.VAR_INT.encode(buf, payload.index()),
        buf -> new SkillSelectPayload(ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
