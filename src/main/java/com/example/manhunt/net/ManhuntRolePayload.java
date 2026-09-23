package com.example.manhunt.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C：玩家在当前对局中的角色标记（每秒随量表同步下发）。
 * skillReady = 技能库中存在任一冷却完毕的卡（驱动技能槽脉冲边框）。
 * 客户端据此决定是否显示技能槽边框等本地 UI。
 */
public record ManhuntRolePayload(boolean participant, boolean runner, boolean skillReady)
    implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ManhuntRolePayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("manhunt", "role"));

    /** 手写编解码：BOOL(ByteBuf) 与目标 B 不一致，无法用 composite。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, ManhuntRolePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            ByteBufCodecs.BOOL.encode(buf, payload.participant());
            ByteBufCodecs.BOOL.encode(buf, payload.runner());
            ByteBufCodecs.BOOL.encode(buf, payload.skillReady());
        },
        buf -> new ManhuntRolePayload(
            ByteBufCodecs.BOOL.decode(buf),
            ByteBufCodecs.BOOL.decode(buf),
            ByteBufCodecs.BOOL.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
