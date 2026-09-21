package com.example.manhunt.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.manhunt.GameConfig;
import com.example.manhunt.loot.LootRoller;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 逃生者个人里程：每水平移动 1 格积 1 点，每满 {@code MILEAGE_PER_ROLL} 触发一次资源抽奖。
 * 里程量表占用逃生者的经验条（进度 = 里程/抽奖间隔，等级 = 已触发抽奖次数）。
 */
public final class MileageManager {
    private MileageManager() {}

    private static final Map<UUID, Double> ACCUM = new HashMap<>();
    private static final Map<UUID, Long> MILEAGE = new HashMap<>();
    private static final Map<UUID, Integer> ROLL_COUNT = new HashMap<>();
    private static final Map<UUID, Double> LAST_X = new HashMap<>();
    private static final Map<UUID, Double> LAST_Z = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> LAST_DIM = new HashMap<>();

    /** 每刻累计在线逃生者的水平位移并触发抽奖。 */
    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : ManhuntGame.onlineAliveRunners(server)) {
            tickPlayer(p);
        }
    }

    private static void tickPlayer(ServerPlayer p) {
        UUID id = p.getUUID();
        ResourceKey<Level> dim = p.level().dimension();
        ResourceKey<Level> lastDim = LAST_DIM.put(id, dim);
        Double lastX = LAST_X.put(id, p.getX());
        Double lastZ = LAST_Z.put(id, p.getZ());
        if (lastDim != dim || lastX == null || lastZ == null) {
            return; // 切维度/首次记录不累计
        }
        double dist = Math.hypot(p.getX() - lastX, p.getZ() - lastZ);
        if (dist <= 0 || dist > GameConfig.MILEAGE_MAX_TICK_DIST) {
            return; // 静止或瞬移（传送/坠落）
        }
        double accum = ACCUM.merge(id, dist, Double::sum);
        long mileage = MILEAGE.getOrDefault(id, 0L);
        int rolls = ROLL_COUNT.getOrDefault(id, 0);
        while (accum >= 1.0) {
            accum -= 1.0;
            mileage++;
        }
        ACCUM.put(id, accum);
        MILEAGE.put(id, mileage);
        int shouldRolls = (int) (mileage / GameConfig.MILEAGE_PER_ROLL);
        if (shouldRolls > rolls) {
            ROLL_COUNT.put(id, shouldRolls);
            int newRolls = shouldRolls - rolls;
            for (int i = 0; i < newRolls; i++) {
                LootRoller.resourceRoll(p, mileage);
            }
        }
    }

    // ==================== 查询与调试 ====================

    public static long mileage(UUID id) {
        return MILEAGE.getOrDefault(id, 0L);
    }

    public static int rollCount(UUID id) {
        return ROLL_COUNT.getOrDefault(id, 0);
    }

    /** 调试/测试：直接增加里程（会按新增量补触发抽奖）。 */
    public static void addMileage(ServerPlayer p, long amount) {
        UUID id = p.getUUID();
        long old = MILEAGE.getOrDefault(id, 0L);
        long updated = Math.max(0, old + amount);
        MILEAGE.put(id, updated);
        int oldRolls = ROLL_COUNT.getOrDefault(id, 0);
        int shouldRolls = (int) (updated / GameConfig.MILEAGE_PER_ROLL);
        if (shouldRolls > oldRolls) {
            ROLL_COUNT.put(id, shouldRolls);
            for (int i = 0; i < shouldRolls - oldRolls; i++) {
                LootRoller.resourceRoll(p, updated);
            }
        }
        syncMeter(p);
    }

    public static void setMileage(ServerPlayer p, long value) {
        UUID id = p.getUUID();
        MILEAGE.put(id, Math.max(0, value));
        ROLL_COUNT.put(id, (int) (Math.max(0, value) / GameConfig.MILEAGE_PER_ROLL));
        ACCUM.put(id, 0.0);
        syncMeter(p);
    }

    // ==================== 经验条接管 ====================

    /** 逃生者经验条 = 里程量表。 */
    public static void syncMeter(ServerPlayer p) {
        if (!TeamUtil.isRunner(p)) {
            return;
        }
        long mileage = MILEAGE.getOrDefault(p.getUUID(), 0L);
        p.experienceLevel = ROLL_COUNT.getOrDefault(p.getUUID(), 0);
        p.experienceProgress = (float) (mileage % GameConfig.MILEAGE_PER_ROLL) / GameConfig.MILEAGE_PER_ROLL;
    }

    // ==================== 状态管理 ====================

    public static void reset() {
        ACCUM.clear();
        MILEAGE.clear();
        ROLL_COUNT.clear();
        LAST_X.clear();
        LAST_Z.clear();
        LAST_DIM.clear();
    }

    public static void onLoggedOut(UUID id) {
        LAST_X.remove(id);
        LAST_Z.remove(id);
        LAST_DIM.remove(id);
        ACCUM.remove(id);
    }

    public static Map<UUID, Long> snapshotMileage() {
        return new HashMap<>(MILEAGE);
    }

    public static Map<UUID, Integer> snapshotRolls() {
        return new HashMap<>(ROLL_COUNT);
    }

    public static void restore(Map<UUID, Long> mileage, Map<UUID, Integer> rolls) {
        MILEAGE.clear();
        MILEAGE.putAll(mileage);
        ROLL_COUNT.clear();
        ROLL_COUNT.putAll(rolls);
    }

    /** 是否有逃生者达到要塞里程阈值（用于检查点链切换）。 */
    public static boolean anyRunnerReachedStronghold(MinecraftServer server) {
        for (UUID id : ManhuntGame.runners()) {
            if (!ManhuntGame.isEliminated(id) && mileage(id) >= GameConfig.STRONGHOLD_MILEAGE) {
                return true;
            }
        }
        return false;
    }
}
