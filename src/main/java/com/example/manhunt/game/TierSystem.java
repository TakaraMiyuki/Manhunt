package com.example.manhunt.game;

import com.example.manhunt.ManhuntMod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 动态人数档位：按 猎人:存活逃生者 比值实时调整双方性能。
 * <pre>
 * ≤1:1  猎人 60血 速度2 急迫2 | 逃生者 60(80)血  抗性1    急迫2 (速度2)(跳跃2)
 * ≤2:1  猎人 40血 速度2 急迫2 | 逃生者 60(80)血  抗性1    急迫2 (速度1)(跳跃2)
 * ≤3:1  猎人 40血 速度1 急迫2 | 逃生者 60(100)血 抗性1    急迫2 (速度1)(跳跃2)
 * >3:1  猎人 40血 速度1 急迫2 | 逃生者 80(100)血 抗性1(2) 急迫2 (速度1)(跳跃2)
 * </pre>
 * （括号为进入末地后；增益等级为原版显示等级，内部 amplifier = 等级-1）
 */
public final class TierSystem {
    private TierSystem() {}

    public enum Tier {
        TIER_1, TIER_2, TIER_3, TIER_4;

        public int display() {
            return ordinal() + 1;
        }
    }

    /** 猎人性能（速度/急迫为原版显示等级）。 */
    public record HunterStats(double maxHealth, int speedLevel, int hasteLevel) {}

    /** 逃生者性能（主世界/末地两组）。 */
    public record RunnerStats(double maxHealth, double endMaxHealth, int resistanceLevel,
                              int endResistanceLevel, int hasteLevel,
                              int endSpeedLevel, int endJumpLevel) {}

    private static Tier current = Tier.TIER_1;
    /** 调试覆盖（1~4），null 表示自动。 */
    private static Integer forced;

    // ==================== 查询 ====================

    public static Tier tier() {
        return current;
    }

    public static int displayTier() {
        return current.display();
    }

    public static HunterStats hunter() {
        return switch (current) {
            case TIER_1 -> new HunterStats(60.0, 2, 2);
            case TIER_2 -> new HunterStats(40.0, 2, 2);
            case TIER_3, TIER_4 -> new HunterStats(40.0, 1, 2);
        };
    }

    public static RunnerStats runner() {
        return switch (current) {
            case TIER_1 -> new RunnerStats(60.0, 80.0, 1, 1, 2, 2, 2);
            case TIER_2 -> new RunnerStats(60.0, 80.0, 1, 1, 2, 1, 2);
            case TIER_3 -> new RunnerStats(60.0, 100.0, 1, 1, 2, 1, 2);
            case TIER_4 -> new RunnerStats(80.0, 100.0, 1, 2, 2, 1, 2);
        };
    }

    // ==================== 更新 ====================

    /** 按在线人数重新计算档位（调试覆盖时跳过）。返回档位是否变化。 */
    public static boolean recalculate(MinecraftServer server) {
        if (forced != null) {
            return false;
        }
        int hunters = ManhuntGame.hunters().size();
        int runners = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (TeamUtil.isRunner(p) && !ManhuntGame.isEliminated(p.getUUID())) {
                runners++;
            }
        }
        for (java.util.UUID id : ManhuntGame.runners()) {
            if (!ManhuntGame.isEliminated(id) && server.getPlayerList().getPlayer(id) == null) {
                runners++;
            }
        }
        double ratio = hunters / (double) Math.max(1, runners);
        Tier next = ratio <= 1.0 ? Tier.TIER_1
            : ratio <= 2.0 ? Tier.TIER_2
            : ratio <= 3.0 ? Tier.TIER_3
            : Tier.TIER_4;
        if (next != current) {
            current = next;
            return true;
        }
        return false;
    }

    // ==================== 调试覆盖 ====================

    public static void setForced(Integer tier1to4) {
        forced = tier1to4 == null ? null : Math.max(1, Math.min(4, tier1to4));
        if (forced != null) {
            current = Tier.values()[forced - 1];
            ManhuntMod.LOGGER.info("[Manhunt] 档位已强制为 {}", forced);
        }
    }

    public static Integer forcedTier() {
        return forced;
    }

    public static void reset() {
        forced = null;
        current = Tier.TIER_1;
    }
}
