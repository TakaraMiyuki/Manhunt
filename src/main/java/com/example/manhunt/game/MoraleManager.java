package com.example.manhunt.game;

import java.util.ArrayList;
import java.util.List;

import com.example.manhunt.GameConfig;
import com.example.manhunt.loot.LootRoller;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 猎人士气量表：全队共享。猎人对逃生者每造成 1 点实际伤害积 1 点士气，
 * 达到阈值（50/100/200/300/500/700/1000，封顶）时每个在线猎人各触发一次超级资源抽奖。
 * 士气量表占用猎人的经验条。
 */
public final class MoraleManager {
    private MoraleManager() {}

    private static int morale = 0;
    private static int rewards = 0;

    /** 累计士气并结算跨过的阈值（一次大额伤害可连跨多档）。 */
    public static void addMorale(MinecraftServer server, int amount) {
        if (amount <= 0 || morale >= GameConfig.MORALE_CAP) {
            return;
        }
        morale = Math.min(GameConfig.MORALE_CAP, morale + amount);
        int newRewards = 0;
        while (rewards < GameConfig.MORALE_THRESHOLDS.length
                && morale >= GameConfig.MORALE_THRESHOLDS[rewards]) {
            rewards++;
            newRewards++;
        }
        if (newRewards > 0) {
            ManhuntGame.broadcast(server, "§6[猎人游戏] §c猎人士气高涨（" + morale + "）！全体猎人获得超级抽奖！");
            for (int i = 0; i < newRewards; i++) {
                for (ServerPlayer hunter : server.getPlayerList().getPlayers()) {
                    if (TeamUtil.isHunter(hunter)) {
                        LootRoller.superRoll(hunter);
                    }
                }
            }
        }
    }

    public static int morale() {
        return morale;
    }

    public static int rewards() {
        return rewards;
    }

    /** 猎人经验条 = 士气量表（进度 = 距下一档，等级 = 已触发档数）。 */
    public static void syncMeter(ServerPlayer p) {
        if (!TeamUtil.isHunter(p)) {
            return;
        }
        p.experienceLevel = rewards;
        if (rewards >= GameConfig.MORALE_THRESHOLDS.length) {
            p.experienceProgress = 1.0F;
            return;
        }
        int next = GameConfig.MORALE_THRESHOLDS[rewards];
        int prev = rewards == 0 ? 0 : GameConfig.MORALE_THRESHOLDS[rewards - 1];
        p.experienceProgress = (float) (morale - prev) / Math.max(1, next - prev);
    }

    public static void reset() {
        morale = 0;
        rewards = 0;
    }

    // ==================== 持久化 ====================

    public static List<Integer> snapshot() {
        List<Integer> list = new ArrayList<>(2);
        list.add(morale);
        list.add(rewards);
        return list;
    }

    public static void restore(int savedMorale, int savedRewards) {
        morale = savedMorale;
        rewards = savedRewards;
    }
}
