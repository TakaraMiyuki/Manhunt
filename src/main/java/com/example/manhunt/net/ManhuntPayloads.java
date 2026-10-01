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
        registrar.playToClient(ManhuntRolePayload.TYPE, ManhuntRolePayload.STREAM_CODEC);
        // 领取抽奖奖励：提交标记索引，发放标记物品并关闭（未标记丢弃）
        registrar.playToServer(ClaimRewardPayload.TYPE, ClaimRewardPayload.STREAM_CODEC,
            (payload, ctx) -> {
                if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                    PendingRewardManager.claimMarked(player, payload.indices());
                }
            });
        // 技能栏选中格（Alt 技能栏滚轮/数字键）
        registrar.playToServer(com.example.manhunt.net.SkillSelectPayload.TYPE,
            com.example.manhunt.net.SkillSelectPayload.STREAM_CODEC,
            (payload, ctx) -> {
                if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                    SkillSlotManager.selectSkill(player, payload.index());
                }
            });
        // 右键释放选中技能
        registrar.playToServer(com.example.manhunt.net.SkillUsePayload.TYPE,
            com.example.manhunt.net.SkillUsePayload.STREAM_CODEC,
            (payload, ctx) -> {
                if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                    SkillSlotManager.useSelected(player);
                }
            });
        // 技能栏调整顺序（对局中）
        registrar.playToServer(com.example.manhunt.net.SkillBarMovePayload.TYPE,
            com.example.manhunt.net.SkillBarMovePayload.STREAM_CODEC,
            (payload, ctx) -> {
                if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                    SkillSlotManager.moveCard(player.getUUID(), payload.from(), payload.to());
                }
            });
        // 技能栏槽位设置（仅调试/未开局，服务器校验）
        registrar.playToServer(com.example.manhunt.net.SkillBarEditPayload.TYPE,
            com.example.manhunt.net.SkillBarEditPayload.STREAM_CODEC,
            (payload, ctx) -> {
                if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player
                        && (!com.example.manhunt.game.ManhuntGame.isRunning() || com.example.manhunt.game.ManhuntGame.soloMode())) {
                    SkillSlotManager.editSlot(player, payload.slot(), payload.stack());
                }
            });
        // 技能三选一确认（赏金模式）
        registrar.playToServer(com.example.manhunt.net.SkillPickPayload.TYPE,
            com.example.manhunt.net.SkillPickPayload.STREAM_CODEC,
            (payload, ctx) -> {
                if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                    com.example.manhunt.loot.SkillDrawManager.pick(player, payload.index());
                }
            });
        // 超级疾跑开关（赏金模式）
        registrar.playToServer(com.example.manhunt.net.SprintTogglePayload.TYPE,
            com.example.manhunt.net.SprintTogglePayload.STREAM_CODEC,
            (payload, ctx) -> {
                if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                    com.example.manhunt.game.SprintManager.set(player, payload.on());
                }
            });

    }
}
