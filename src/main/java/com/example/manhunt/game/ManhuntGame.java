package com.example.manhunt.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.example.manhunt.GameConfig;
import com.example.manhunt.item.CompassManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 游戏核心状态机与每刻调度。
 * 阶段：IDLE → ESCAPE(60 秒逃跑倒计时) → RUNNING → ENDED。
 */
public final class ManhuntGame {
    private ManhuntGame() {}

    public enum Phase { IDLE, ESCAPE, RUNNING, ENDED }

    private static Phase phase = Phase.IDLE;
    private static final Set<UUID> HUNTERS = new HashSet<>();
    private static final Set<UUID> RUNNERS = new HashSet<>();
    private static final Set<UUID> ELIMINATED = new HashSet<>();
    /** 已激活的检查点数量（0~4）。 */
    private static int unlocked = 0;
    /** 四个检查点坐标（主世界地表）。 */
    private static final BlockPos[] CHECKPOINTS = new BlockPos[GameConfig.CHECKPOINT_COUNT];
    private static int escapeTicksLeft = 0;
    private static int tickCounter = 0;

    private static ServerBossEvent countdownBar;
    private static ServerBossEvent checkpointBar;

    /** 猎人死亡后待重生传送：uuid → 目标位置与剩余等待刻数。 */
    private record RespawnRequest(GlobalPos pos, int maxWait) {}
    private static final Map<UUID, GlobalPos> PENDING_RESPAWNS = new HashMap<>();
    private static final Map<UUID, Integer> RESPAWN_WAITS = new HashMap<>();

    // ==================== 状态访问 ====================

    public static Phase phase() { return phase; }

    public static boolean isRunning() { return phase == Phase.ESCAPE || phase == Phase.RUNNING; }

    public static boolean isHunter(UUID id) { return HUNTERS.contains(id); }

    public static boolean isRunner(UUID id) { return RUNNERS.contains(id); }

    public static boolean isParticipant(UUID id) { return HUNTERS.contains(id) || RUNNERS.contains(id); }

    public static boolean isEliminated(UUID id) { return ELIMINATED.contains(id); }

    public static Set<UUID> hunters() { return HUNTERS; }

    public static Set<UUID> runners() { return RUNNERS; }

    public static int unlockedCount() { return unlocked; }

    /** 逃生者当前目标检查点（1-based 序号；全部解锁后返回 0）。 */
    public static int currentCheckpointNumber() { return unlocked < GameConfig.CHECKPOINT_COUNT ? unlocked + 1 : 0; }

    public static BlockPos checkpoint(int index1based) {
        if (index1based < 1 || index1based > GameConfig.CHECKPOINT_COUNT) {
            return null;
        }
        return CHECKPOINTS[index1based - 1];
    }

    public static BlockPos currentCheckpoint() {
        return unlocked < GameConfig.CHECKPOINT_COUNT ? CHECKPOINTS[unlocked] : null;
    }

    public static void setCheckpoint(int index0based, BlockPos pos) { CHECKPOINTS[index0based] = pos; }

    // ==================== 生命周期 ====================

    /** 开局：分配已就绪的队伍、生成检查点、进入逃跑倒计时。 */
    public static String start(MinecraftServer server, Set<UUID> hunters, Set<UUID> runners) {
        if (isRunning()) {
            return "游戏已在进行中，请先 /manhunt stop。";
        }
        if (hunters.isEmpty() || runners.isEmpty()) {
            return "猎人与逃生者都必须至少 1 人。";
        }
        HUNTERS.clear();
        RUNNERS.clear();
        ELIMINATED.clear();
        HUNTERS.addAll(hunters);
        RUNNERS.addAll(runners);
        PENDING_RESPAWNS.clear();
        RESPAWN_WAITS.clear();

        // 生成检查点
        String cpError = CheckpointManager.generateAll(server);
        unlocked = 0;

        // 集合：所有在线参与者传送到世界出生点
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getLevelData().getRespawnData().pos();
        for (ServerPlayer p : onlineParticipants(server)) {
            p.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, Set.of(), p.getYRot(), p.getXRot(), false);
        }

        phase = Phase.ESCAPE;
        escapeTicksLeft = GameConfig.ESCAPE_SECONDS * 20;
        tickCounter = 0;

        // 倒计时 bossbar（全体参与者）
        countdownBar = new ServerBossEvent(
            UUID.randomUUID(),
            Component.literal("§a逃生时间 " + GameConfig.ESCAPE_SECONDS + "s"),
            BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
        countdownBar.setProgress(1.0F);
        for (ServerPlayer p : onlineParticipants(server)) {
            TeamUtil.applyBaseAttributes(p);
            TeamUtil.refreshBuffs(p);
            if (TeamUtil.isRunner(p)) {
                TeamUtil.giveInitialKit(p);
                CompassManager.giveCheckpointCompass(p);
            }
            countdownBar.addPlayer(p);
            p.sendSystemMessage(Component.literal(
                "§e[猎人游戏] 你是 §a逃生者§e！60 秒后猎人开始追杀。激活 4 个检查点并击杀末影龙即可获胜！"));
            p.sendSystemMessage(Component.literal(
                "§e[猎人游戏] 你是 §c猎人§e！60 秒倒计时结束后开始追杀所有逃生者。"));
        }
        broadcast(server, "§6[猎人游戏] §f游戏开始！§a逃生者 " + runners.size() + " 人 §7| §c猎人 " + hunters.size() + " 人");
        broadcast(server, "§6[猎人游戏] §f1 号检查点已解锁，逃生者罗盘已指向目标。");
        if (cpError != null) {
            broadcast(server, "§c[猎人游戏] 检查点生成警告: " + cpError);
        }
        ManhuntStateIO.save(server);
        return null;
    }

    public static void stop(MinecraftServer server) {
        if (phase == Phase.IDLE) {
            return;
        }
        broadcast(server, "§6[猎人游戏] §c游戏已被管理员终止。");
        cleanup(server);
    }

    private static void cleanup(MinecraftServer server) {
        phase = Phase.IDLE;
        escapeTicksLeft = 0;
        if (countdownBar != null) {
            for (ServerPlayer p : new ArrayList<>(countdownBar.getPlayers())) {
                countdownBar.removePlayer(p);
            }
            countdownBar = null;
        }
        if (checkpointBar != null) {
            for (ServerPlayer p : new ArrayList<>(checkpointBar.getPlayers())) {
                checkpointBar.removePlayer(p);
            }
            checkpointBar = null;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isParticipant(p.getUUID())) {
                TeamUtil.resetToDefault(p);
            }
        }
        PENDING_RESPAWNS.clear();
        RESPAWN_WAITS.clear();
        ManhuntStateIO.save(server);
    }

    /** 倒计时结束，猎人解禁。 */
    private static void beginChase(MinecraftServer server) {
        phase = Phase.RUNNING;
        if (countdownBar != null) {
            for (ServerPlayer p : new ArrayList<>(countdownBar.getPlayers())) {
                countdownBar.removePlayer(p);
            }
            countdownBar = null;
        }
        // 检查点进度 bossbar（逃生者）
        checkpointBar = new ServerBossEvent(
            UUID.randomUUID(),
            Component.literal("检查点进度"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        checkpointBar.setProgress(0.0F);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (TeamUtil.isHunter(p)) {
                TeamUtil.clearEscapeDebuffs(p);
                CompassManager.giveTrackingCompass(p);
            }
            if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                checkpointBar.addPlayer(p);
            }
            TeamUtil.refreshBuffs(p);
        }
        broadcast(server, "§6[猎人游戏] §c逃跑时间结束！猎人已开始追杀！");
        ManhuntStateIO.save(server);
    }

    // ==================== 事件回调 ====================

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        processPendingRespawns(server);

        if (phase == Phase.ESCAPE) {
            escapeTicksLeft--;
            if (escapeTicksLeft <= 0) {
                beginChase(server);
                return;
            }
            if (tickCounter % 20 == 0 && countdownBar != null) {
                countdownBar.setName(Component.literal(
                    "§a逃生时间 " + (escapeTicksLeft / 20) + "s"));
                countdownBar.setProgress(escapeTicksLeft / (float) (GameConfig.ESCAPE_SECONDS * 20));
            }
        }

        if (!isRunning()) {
            return;
        }
        tickCounter++;

        if (tickCounter % GameConfig.BUFF_REFRESH_INTERVAL_TICKS == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (isParticipant(p.getUUID())) {
                    TeamUtil.refreshBuffs(p);
                }
            }
        }
        if (tickCounter % GameConfig.COMPASS_UPDATE_INTERVAL_TICKS == 0) {
            CompassManager.updateAll(server);
        }
        if (tickCounter % GameConfig.BOSSBAR_UPDATE_INTERVAL_TICKS == 0 && checkpointBar != null) {
            updateCheckpointBossbar(server);
        }
        if (phase == Phase.RUNNING && tickCounter % GameConfig.ACTIVATION_CHECK_INTERVAL_TICKS == 0) {
            CheckpointManager.checkActivations(server);
        }
    }

    private static void updateCheckpointBossbar(MinecraftServer server) {
        BlockPos cp = currentCheckpoint();
        if (cp == null) {
            checkpointBar.setName(Component.literal("§e检查点 4/4 已激活 §7- 前往末地击杀§d末影龙§7！"));
            checkpointBar.setProgress(1.0F);
            return;
        }
        // 以最近在线逃生者的距离显示
        double minDistSq = Double.MAX_VALUE;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                minDistSq = Math.min(minDistSq, p.distanceToSqr(cp.getX() + 0.5, cp.getY() + 0.5, cp.getZ() + 0.5));
            }
        }
        int dist = minDistSq == Double.MAX_VALUE ? 0 : (int) Math.sqrt(minDistSq);
        int number = unlocked + 1;
        checkpointBar.setName(Component.literal("§e检查点 " + number + "/4 §7- 距离 §f" + dist + "m"));
        checkpointBar.setProgress(Math.max(0.05F, 1.0F - Math.min(1.0F, dist / 1500.0F)));
    }

    // ==================== 检查点 ====================

    /** 逃生者激活当前检查点（已由 CheckpointManager 校验位置与身份）。 */
    public static void activateCheckpoint(MinecraftServer server, ServerPlayer activator) {
        int number = unlocked + 1;
        unlocked++;
        broadcast(server, "§6[猎人游戏] §a逃生者 §f" + activator.getName().getString()
            + " §a激活了第 " + number + " 个检查点！物资已发放。");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                LootProvider.giveCheckpointLoot(p, number);
            }
        }
        if (unlocked >= GameConfig.CHECKPOINT_COUNT) {
            broadcast(server, "§6[猎人游戏] §d全部检查点已激活！逃生者已获得鞘翅与末影之眼，击杀§5末影龙§d即获胜！");
        } else {
            BlockPos next = CHECKPOINTS[unlocked];
            if (next != null) {
                String hint = unlocked == GameConfig.CHECKPOINT_COUNT - 1
                    ? " §d(4 号检查点位于末地要塞正上方，要塞入口在地下！)"
                    : "";
                sendToRunners(server, "§6[猎人游戏] §f下一个检查点坐标: §e" + next.getX() + ", " + next.getY() + ", " + next.getZ() + hint);
            }
        }
        if (checkpointBar != null) {
            checkpointBar.setProgress(unlocked / (float) GameConfig.CHECKPOINT_COUNT);
        }
        ManhuntStateIO.save(server);
    }

    // ==================== 死亡与胜负 ====================

    /** 逃生者击杀猎人：全体逃生者获得生命恢复。 */
    public static void onHunterKilledByRunner(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.REGENERATION,
                    GameConfig.KILL_REGEN_SECONDS * 20,
                    GameConfig.KILL_REGEN_AMPLIFIER), null);
            }
        }
        broadcast(server, "§6[猎人游戏] §c一名猎人被击杀！逃生者获得 §a生命恢复 III (" + GameConfig.KILL_REGEN_SECONDS + "s)");
    }

    /** 猎人死亡后规划复活位置（重生后由 tick 传送）。 */
    public static void scheduleHunterRespawn(MinecraftServer server, ServerPlayer deadHunter) {
        GlobalPos pos = RespawnSelector.selectHunterRespawn(server, deadHunter);
        PENDING_RESPAWNS.put(deadHunter.getUUID(), pos);
        RESPAWN_WAITS.put(deadHunter.getUUID(), GameConfig.RESPAWN_TELEPORT_MAX_WAIT);
    }

    /** 逃生者死亡：淘汰；全部淘汰则猎人获胜。 */
    public static void onRunnerDeath(MinecraftServer server, ServerPlayer deadRunner) {
        ELIMINATED.add(deadRunner.getUUID());
        broadcast(server, "§6[猎人游戏] §a逃生者 §f" + deadRunner.getName().getString() + " §c被淘汰！"
            + " (剩余 " + aliveRunnerCount() + " 人)");
        if (aliveRunnerCount() == 0) {
            endGame(server, "§c猎人获胜！所有逃生者已被淘汰。");
        }
        ManhuntStateIO.save(server);
    }

    /** 末影龙死亡 → 逃生者胜利。 */
    public static void onDragonKilled(MinecraftServer server) {
        if (phase == Phase.RUNNING) {
            endGame(server, "§d§l逃生者击杀了末影龙！§r§b逃生者获胜！！");
        }
    }

    private static int aliveRunnerCount() {
        int n = 0;
        for (UUID id : RUNNERS) {
            if (!ELIMINATED.contains(id)) {
                n++;
            }
        }
        return n;
    }

    public static void endGame(MinecraftServer server, String message) {
        broadcast(server, "§6[猎人游戏] §f" + message);
        broadcast(server, "§6[猎人游戏] §7游戏结束。使用 /manhunt start 开始新的一局。");
        cleanup(server);
    }

    // ==================== 重生后传送 ====================

    private static void processPendingRespawns(MinecraftServer server) {
        if (PENDING_RESPAWNS.isEmpty()) {
            return;
        }
        List<UUID> done = new ArrayList<>();
        for (Map.Entry<UUID, GlobalPos> e : PENDING_RESPAWNS.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                // 尚未点击重生，继续等待直到超时
                int wait = RESPAWN_WAITS.getOrDefault(e.getKey(), 0) - 1;
                if (wait <= 0) {
                    done.add(e.getKey());
                } else {
                    RESPAWN_WAITS.put(e.getKey(), wait);
                }
                continue;
            }
            GlobalPos pos = e.getValue();
            ServerLevel level = server.getLevel(pos.dimension());
            if (level != null) {
                BlockPos bp = pos.pos();
                double x = bp.getX() + 0.5, z = bp.getZ() + 0.5;
                double y = bp.getY() + 1;
                p.teleportTo(level, x, y, z, Set.of(), p.getYRot(), p.getXRot(), false);
            }
            TeamUtil.applyBaseAttributes(p);
            TeamUtil.refreshBuffs(p);
            done.add(e.getKey());
        }
        for (UUID id : done) {
            PENDING_RESPAWNS.remove(id);
            RESPAWN_WAITS.remove(id);
        }
    }

    // ==================== 辅助 ====================

    public static List<ServerPlayer> onlineParticipants(MinecraftServer server) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isParticipant(p.getUUID())) {
                list.add(p);
            }
        }
        return list;
    }

    /** 在线且未淘汰的逃生者（按 UUID 排序，保证罗盘循环顺序稳定）。 */
    public static List<ServerPlayer> onlineAliveRunners(MinecraftServer server) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                list.add(p);
            }
        }
        list.sort((a, b) -> a.getUUID().compareTo(b.getUUID()));
        return list;
    }

    public static void broadcast(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }

    public static void sendToRunners(MinecraftServer server, String text) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (TeamUtil.isRunner(p)) {
                p.sendSystemMessage(Component.literal(text));
            }
        }
    }

    // ==================== 持久化 ====================

    public static void onServerStarted(MinecraftServer server) {
        ManhuntStateIO.load(server);
    }

    public static void onServerStopping(MinecraftServer server) {
        ManhuntStateIO.save(server);
    }

    static Phase rawPhase() { return phase; }

    static void restoreState(Phase p, Set<UUID> hunters, Set<UUID> runners, Set<UUID> eliminated,
                             int unlockedCount, BlockPos[] checkpoints) {
        phase = p;
        HUNTERS.clear();
        HUNTERS.addAll(hunters);
        RUNNERS.clear();
        RUNNERS.addAll(runners);
        ELIMINATED.clear();
        ELIMINATED.addAll(eliminated);
        unlocked = unlockedCount;
        for (int i = 0; i < CHECKPOINTS.length && i < checkpoints.length; i++) {
            CHECKPOINTS[i] = checkpoints[i];
        }
        escapeTicksLeft = 0;
    }

    static boolean hasCheckpointBar() { return checkpointBar != null; }

    static void reattachBossbars(MinecraftServer server) {
        if (phase == Phase.RUNNING) {
            checkpointBar = new ServerBossEvent(UUID.randomUUID(),
                Component.literal("检查点进度"),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
            checkpointBar.setProgress(unlocked / (float) GameConfig.CHECKPOINT_COUNT);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                    checkpointBar.addPlayer(p);
                }
            }
        }
    }
}
