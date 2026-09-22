package com.example.manhunt.net;

import com.example.manhunt.cards.SkillSlotManager;
import com.example.manhunt.loot.PendingRewardManager;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册。S2C 动画包的 handler 在客户端类（ManhuntClient）中注册；
 * C2S 请求在此处直接处理（自动在主线程执行）。
 */
public final class ManhuntPayloads {
    private ManhuntPayloads() {}

    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // 仅注册编解码；客户端 handler 由 RegisterClientPayloadHandlersEvent 接管
        registrar.playToClient(LootRollPayload.TYPE, LootRollPayload.STREAM_CODEC);
        // 领取抽奖奖励：提交标记索引，发放标记物品并关闭（未标记丢弃）
        registrar.playToServer(ClaimRewardPayload.TYPE, ClaimRewardPayload.STREAM_CODEC,
            (payload, ctx) -> {
                if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                    PendingRewardManager.claimMarked(player, payload.indices());
                }
            });
        // 切换技能
        registrar.playToServer(SkillSwitchPayload.TYPE, SkillSwitchPayload.STREAM_CODEC,
            (payload, ctx) -> {
                if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                    SkillSlotManager.switchSkill(player);
                }
            });
    }
}
