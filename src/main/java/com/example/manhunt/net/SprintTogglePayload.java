package com.example.manhunt.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S：超级疾跑开关（赏金模式，左 Alt 切换）。
 */
public record SprintTogglePayload(boolean on) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SprintTogglePayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("manhunt", "sprint_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SprintTogglePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> ByteBufCodecs.BOOL.encode(buf, payload.on()),
        buf -> new SprintTogglePayload(ByteBufCodecs.BOOL.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
