package com.example.manhunt.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S：技能三选一确认（赏金模式），服务端发放选中下标的卡。
 */
public record SkillPickPayload(int index) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SkillPickPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("manhunt", "skill_pick"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillPickPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> ByteBufCodecs.VAR_INT.encode(buf, payload.index()),
        buf -> new SkillPickPayload(ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
