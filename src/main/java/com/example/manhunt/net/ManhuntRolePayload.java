package com.example.manhunt.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C：玩家在当前对局中的角色标记（每秒随量表同步下发）。
 * skillReady = 技能库中存在任一冷却完毕的卡（驱动技能槽脉冲边框）。
 * morale/moraleRewards = 猎人全队士气与已达成档数（驱动屏幕上方的士气条 UI）。
 * 客户端据此决定是否显示技能槽边框等本地 UI。
 */
public record ManhuntRolePayload(boolean participant, boolean runner, boolean skillReady,
                                 int morale, int moraleRewards) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ManhuntRolePayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("manhunt", "role"));

    /** 手写编解码：BOOL(ByteBuf) 与目标 B 不一致，无法用 composite。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, ManhuntRolePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            ByteBufCodecs.BOOL.encode(buf, payload.participant());
            ByteBufCodecs.BOOL.encode(buf, payload.runner());
            ByteBufCodecs.BOOL.encode(buf, payload.skillReady());
            ByteBufCodecs.VAR_INT.encode(buf, payload.morale());
            ByteBufCodecs.VAR_INT.encode(buf, payload.moraleRewards());
        },
        buf -> new ManhuntRolePayload(
            ByteBufCodecs.BOOL.decode(buf),
            ByteBufCodecs.BOOL.decode(buf),
            ByteBufCodecs.BOOL.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
