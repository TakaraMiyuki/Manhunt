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
 * 达到阈值（50/100/150/200/300/400/600，此后每档 +200 无封顶）时每个在线猎人各触发一次超级资源抽奖。
 * 士气增长后 5 秒内临时接管猎人的经验条。
 */
public final class MoraleManager {
    private MoraleManager() {}

    private static int morale = 0;
    private static int rewards = 0;
    private static long lastGainTime = Long.MIN_VALUE;

    /** 第 rewardsIndex 档的阈值；表末之后按固定步长无限外推。 */
    public static int threshold(int rewardsIndex) {
        int[] t = GameConfig.MORALE_THRESHOLDS;
        return rewardsIndex < t.length
            ? t[rewardsIndex]
            : t[t.length - 1] + GameConfig.MORALE_STEP_AFTER_LAST * (rewardsIndex - t.length + 1);
    }

    /** 累计士气并结算跨过的阈值（一次大额伤害可连跨多档）。 */
    public static void addMorale(MinecraftServer server, int amount) {
        if (amount <= 0) {
            return;
        }
        lastGainTime = server.overworld().getGameTime();
        morale += amount;
        int newRewards = 0;
        while (morale >= threshold(rewards)) {
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

    /** 士气量表是否临时接管猎人经验条（士气增长后 5 秒内）。 */
    public static boolean showingOnMeter(MinecraftServer server) {
        return lastGainTime != Long.MIN_VALUE
            && server.overworld().getGameTime() - lastGainTime < GameConfig.MORALE_METER_SHOW_TICKS;
    }

    public static void reset() {
        morale = 0;
        rewards = 0;
        lastGainTime = Long.MIN_VALUE;
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
