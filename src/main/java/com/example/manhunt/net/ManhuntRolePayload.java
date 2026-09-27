package com.example.manhunt.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C：玩家在当前对局中的角色标记（每秒随量表同步下发）。
 * skillReady = 技能库中存在任一冷却完毕的卡（驱动技能槽脉冲边框）。
 * morale/moraleRewards = 猎人全队士气与已达成档数（驱动屏幕上方的士气条 UI）。
 * skillIds/skillActive = 技能库全部卡牌物品 id 与激活下标（驱动技能轮盘）。
 * escapePhase = 逃跑倒计时期间（士气条让位倒计时 bossbar，不渲染）。
 * 客户端据此决定是否显示技能槽边框、技能轮盘、士气条等本地 UI。
 */
public record ManhuntRolePayload(boolean participant, boolean runner, boolean skillReady,
                                 int morale, int moraleRewards,
                                 List<String> skillIds, int skillActive,
                                 boolean escapePhase) implements CustomPacketPayload {
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
            ByteBufCodecs.VAR_INT.encode(buf, payload.skillIds().size());
            for (String id : payload.skillIds()) {
                ByteBufCodecs.STRING_UTF8.encode(buf, id);
            }
            ByteBufCodecs.VAR_INT.encode(buf, payload.skillActive());
            ByteBufCodecs.BOOL.encode(buf, payload.escapePhase());
        },
        buf -> {
            boolean participant = ByteBufCodecs.BOOL.decode(buf);
            boolean runner = ByteBufCodecs.BOOL.decode(buf);
            boolean skillReady = ByteBufCodecs.BOOL.decode(buf);
            int morale = ByteBufCodecs.VAR_INT.decode(buf);
            int moraleRewards = ByteBufCodecs.VAR_INT.decode(buf);
            int size = ByteBufCodecs.VAR_INT.decode(buf);
            List<String> skillIds = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                skillIds.add(ByteBufCodecs.STRING_UTF8.decode(buf));
            }
            int skillActive = ByteBufCodecs.VAR_INT.decode(buf);
            boolean escapePhase = ByteBufCodecs.BOOL.decode(buf);
            return new ManhuntRolePayload(participant, runner, skillReady,
                morale, moraleRewards, skillIds, skillActive, escapePhase);
        });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
