package com.example.manhunt.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S：对局中调整技能栏顺序（from → to 移动/交换，不可取出）。
 */
public record SkillBarMovePayload(int from, int to) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SkillBarMovePayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("manhunt", "skill_bar_move"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillBarMovePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            ByteBufCodecs.VAR_INT.encode(buf, payload.from());
            ByteBufCodecs.VAR_INT.encode(buf, payload.to());
        },
        buf -> new SkillBarMovePayload(
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
