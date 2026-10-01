package com.example.manhunt.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.example.manhunt.GameConfig;
import com.example.manhunt.cards.SkillSlotManager;
import com.example.manhunt.loot.PendingRewardManager;
import com.example.manhunt.loot.SkillDrawManager;
import com.example.manhunt.cards.SkillCardsBridge;
import com.example.manhunt.item.CompassManager;
import com.example.manhunt.net.LootRollPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 游戏核心状态机与每刻调度。
 * 阶段：IDLE → ESCAPE(60 秒逃跑倒计时) → RUNNING → ENDED。
 * 检查点为无限链：逐个解锁；任一逃生者里程达标后下一个固定为末地要塞。
 */
public final class ManhuntGame {
    private ManhuntGame() {}

    public enum Phase { IDLE, ESCAPE, RUNNING, ENDED }

    private static Phase phase = Phase.IDLE;
    /** 当前游戏模式（赏金模式下多检查点/三命/赏金量表生效）。 */
    private static Mode mode = Mode.CLASSIC;
    private static final Set<UUID> HUNTERS = new HashSet<>();
    private static final Set<UUID> RUNNERS = new HashSet<>();
    private static final Set<UUID> ELIMINATED = new HashSet<>();
    /** 单人调试模式：无猎人开局，逃生者死亡不结算胜负。 */
    private static boolean soloMode = false;
    /** 当前目标检查点；null 表示已进入末地阶段（杀龙）。 */
    private static BlockPos currentCheckpoint;
    /** 当前检查点是否为末地要塞。 */
    private static boolean currentIsStronghold = false;
    /** 调试强制：下一个刷新的检查点为要塞。 */
    private static boolean forceNextStronghold = false;
    /** 最近激活的检查点（复活兜底锚点）。 */
    private static BlockPos lastCheckpoint;
    /** 末地传送门房间位置（要塞检查点生成时定位，猎人罗盘指向目标）。 */
    private static net.minecraft.core.GlobalPos strongholdPortalPos;
    private static int activatedCount = 0;
    private static int escapeTicksLeft = 0;
    private static int tickCounter = 0;

    private static ServerBossEvent countdownBar;
    private static ServerBossEvent checkpointBar;

    /** 猎人重生计划：死亡 → 自动重生（旁观）→ 传送至复活点（赏金模式：主世界=队友附近 / 末地=传送门前）。 */
    private record HunterRespawn(long respawnAt, long spectateUntil, boolean bountyEnd, UUID teammate,
                                 net.minecraft.core.GlobalPos deathPos) {}

    private static final Map<UUID, HunterRespawn> PENDING_HUNTER_RESPAWNS = new HashMap<>();

    // ==================== 赏金模式状态 ====================
    /** 赏金模式：同时存在的普通检查点（激活一个移除并生成新的；要塞阶段清空）。 */
    private static final List<BlockPos> BOUNTY_CHECKPOINTS = new ArrayList<>();
    /** 赏金模式：逃生者剩余命数。 */
    private static final Map<UUID, Integer> LIVES = new HashMap<>();
    /** 赏金模式：罗盘赦免截止时刻（死亡重生后 60 秒内不可被罗盘追踪）。 */
    private static final Map<UUID, Long> BOUNTY_IMMUNE_UNTIL = new HashMap<>();
    /** 赏金模式：逃生者重生计划（死亡 → 下一刻重生 → 传送至距死亡位置 300–500 格）。 */
    private record RunnerRespawn(GlobalPos pos, long respawnAt) {}

    private static final Map<UUID, RunnerRespawn> PENDING_RUNNER_RESPAWNS = new HashMap<>();
    /** 淘汰者原地旁观重定位：死亡 → 下一刻重生 → 回到死亡位置转旁观（而非世界出生点）。 */
    private static final Map<UUID, GlobalPos> PENDING_ELIMINATED_SPECTATORS = new HashMap<>();
    /** 头号赏金发光队伍（金色描边，仅标记者加入）。 */
    private static final String BOUNTY_TEAM_NAME = "manhunt_bounty";
    /** 逃生者发光分色队伍前缀：发光描边颜色 = 队伍颜色（循环取色，不用红色）。 */
    private static final String GLOW_TEAM_PREFIX = "manhunt_glow_";
    private static final TeamColor[] GLOW_COLORS = {
        TeamColor.GOLD, TeamColor.GREEN, TeamColor.AQUA, TeamColor.LIGHT_PURPLE,
        TeamColor.YELLOW, TeamColor.WHITE, TeamColor.BLUE, TeamColor.DARK_GREEN,
        TeamColor.DARK_AQUA, TeamColor.DARK_PURPLE, TeamColor.GRAY, TeamColor.DARK_BLUE};

    // ==================== 状态访问 ====================

    public static Phase phase() { return phase; }

    public static Mode mode() { return mode; }

    public static boolean isBounty() { return mode == Mode.BOUNTY; }

    public static boolean isRunning() { return phase == Phase.ESCAPE || phase == Phase.RUNNING; }

    public static boolean isHunter(UUID id) { return HUNTERS.contains(id); }

    public static boolean isRunner(UUID id) { return RUNNERS.contains(id); }

    public static boolean isParticipant(UUID id) { return HUNTERS.contains(id) || RUNNERS.contains(id); }

    public static boolean isEliminated(UUID id) { return ELIMINATED.contains(id); }

    public static Set<UUID> hunters() { return HUNTERS; }

    public static Set<UUID> runners() { return RUNNERS; }

    public static boolean soloMode() { return soloMode; }

    public static int activatedCount() { return activatedCount; }

    public static BlockPos currentCheckpoint() { return currentCheckpoint; }

    public static boolean isCurrentStronghold() { return currentIsStronghold; }

    public static BlockPos lastCheckpoint() { return lastCheckpoint; }

    public static net.minecraft.core.GlobalPos strongholdPortalPos() { return strongholdPortalPos; }

    public static void setStrongholdPortalPos(net.minecraft.core.GlobalPos pos) { strongholdPortalPos = pos; }

    public static void setCurrentCheckpoint(BlockPos pos, boolean stronghold) {
        currentCheckpoint = pos;
        currentIsStronghold = pos != null && stronghold;
    }

    public static void forceNextStronghold() {
        forceNextStronghold = true;
    }

    public static boolean nextStrongholdForced() {
        return forceNextStronghold;
    }

    static void resetStrongholdForce() {
        forceNextStronghold = false;
        strongholdPortalPos = null;
    }

    // ==================== 赏金模式访问 ====================

    public static List<BlockPos> bountyCheckpoints() { return BOUNTY_CHECKPOINTS; }

    /** 赏金模式：地图同时存在的检查点上限（按逃生者人数查表）。 */
    public static int bountyCheckpointLimit() {
        int n = aliveRunnerCount();
        int[] table = GameConfig.BOUNTY_CP_TABLE;
        if (n <= 3) return table[0];
        if (n <= 5) return table[1];
        if (n <= 7) return table[2];
        return table[3];
    }

    /** 赏金模式：激活末地要塞所需总里程档（逃生者数 × 3000）。 */
    public static int bountyStrongholdTier() {
        return Math.max(1, RUNNERS.size()) * GameConfig.BOUNTY_STRONGHOLD_PER_RUNNER;
    }

    /** 赏金模式：全体技能抽奖档位（要塞档 ÷ 6）。 */
    public static int bountySkillTier() {
        return Math.max(1, bountyStrongholdTier() / GameConfig.BOUNTY_SKILL_DIVISOR);
    }

    /** 赏金模式动态性能分档：存活逃生者数 ≥ 猎人数 × 4 → 猎人末地强化档（80 血/速度3）。 */
    public static boolean bountyHunterLarge() {
        int hunters = Math.max(1, HUNTERS.size());
        return aliveRunnerCount() >= 4 * hunters;
    }

    public static int lives(UUID id) { return LIVES.getOrDefault(id, 0); }

    /** 罗盘赦免是否生效（死亡重生后 60 秒）。 */
    public static boolean isCompassImmune(UUID id, MinecraftServer server) {
        Long until = BOUNTY_IMMUNE_UNTIL.get(id);
        return until != null && server.overworld().getGameTime() < until;
    }

    public static void setCompassImmune(MinecraftServer server, UUID id, long ticks) {
        BOUNTY_IMMUNE_UNTIL.put(id, server.overworld().getGameTime() + ticks);
    }

    // ==================== 生命周期 ====================

    /**
     * 开局：分配队伍、生成首个检查点、进入逃跑倒计时（经典模式）。
     * @return 错误提示；null 表示成功
     */
    public static String start(MinecraftServer server, Set<UUID> hunters, Set<UUID> runners, boolean solo) {
        return start(server, hunters, runners, solo, Mode.CLASSIC);
    }

    /**
     * 开局：分配队伍、生成检查点（经典=单链 / 赏金=多检查点）、进入逃跑倒计时。
     * @return 错误提示；null 表示成功
     */
    public static String start(MinecraftServer server, Set<UUID> hunters, Set<UUID> runners, boolean solo, Mode m) {
        if (isRunning()) {
            return "游戏已在进行中，请先 /manhunt stop。";
        }
        if (!solo && (hunters.isEmpty() || runners.isEmpty())) {
            return "猎人与逃生者都必须至少 1 人（单人调试请用 /manhunt debug solo）。";
        }
        if (solo && runners.isEmpty()) {
            return "单人模式至少需要 1 名逃生者。";
        }
        HUNTERS.clear();
        RUNNERS.clear();
        ELIMINATED.clear();
        HUNTERS.addAll(hunters);
        RUNNERS.addAll(runners);
        PENDING_HUNTER_RESPAWNS.clear();
        DeathHandler.clearAllHunterGear();
        soloMode = solo;
        mode = m;
        activatedCount = 0;
        currentCheckpoint = null;
        currentIsStronghold = false;
        lastCheckpoint = null;
        forceNextStronghold = false;
        BOUNTY_CHECKPOINTS.clear();
        BOUNTY_IMMUNE_UNTIL.clear();
        PENDING_RUNNER_RESPAWNS.clear();
        PENDING_ELIMINATED_SPECTATORS.clear();
        LIVES.clear();
        for (UUID id : RUNNERS) {
            LIVES.put(id, GameConfig.BOUNTY_RUNNER_LIVES);
        }
        MileageManager.reset();
        MoraleManager.reset();
        BountyManager.reset();
        SprintManager.reset();
        SkillSlotManager.reset();
        SkillDrawManager.reset();
        TierSystem.reset();

        if (isBounty()) {
            CheckpointManager.generateBountyCheckpoints(server);
        } else {
            CheckpointManager.generateFirst(server);
        }

        // 集合：所有在线参与者传送到世界出生点
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getLevelData().getRespawnData().pos();
        for (ServerPlayer p : onlineParticipants(server)) {
            p.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, Set.of(), p.getYRot(), p.getXRot(), false);
        }

        phase = Phase.ESCAPE;
        escapeTicksLeft = GameConfig.ESCAPE_SECONDS * 20;
        tickCounter = 0;

        countdownBar = new ServerBossEvent(
            UUID.randomUUID(),
            Component.literal("§a逃生时间 " + GameConfig.ESCAPE_SECONDS + "s"),
            BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
        countdownBar.setProgress(1.0F);
        for (ServerPlayer p : onlineParticipants(server)) {
            // 清空被动卡饰品槽（防跨局残留）+ 重置技能卡永久加成（赤鳞跃动等）
            com.example.manhunt.compat.SkillPassiveBridge.clearPassives(p);
            SkillCardsBridge.resetPersistentBonuses(p);
            com.example.manhunt.compat.CraftingOnAStickBridge.giveStick(p);
            TeamUtil.applyBaseAttributes(p);
            TeamUtil.fullyRestore(p);
            TeamUtil.refreshBuffs(p);
            if (TeamUtil.isRunner(p)) {
                if (isBounty()) {
                    TeamUtil.giveBountyRunnerKit(p);
                } else {
                    TeamUtil.giveInitialKit(p);
                }
                CompassManager.giveCheckpointCompass(p);
            } else if (isBounty()) {
                TeamUtil.giveBountyHunterKit(p);
            }
            MileageManager.syncMeter(p);
            countdownBar.addPlayer(p);
            if (TeamUtil.isRunner(p)) {
                if (isBounty()) {
                    p.sendSystemMessage(Component.literal(
                        "§e[赏金猎人] 你是 §a逃生者§e！共有 §f" + GameConfig.BOUNTY_RUNNER_LIVES + " 条命§e，"
                            + "地图上有 " + BOUNTY_CHECKPOINTS.size() + " 个检查点（金色粒子圈）可自由选择前往。"));
                    p.sendSystemMessage(Component.literal(
                        "§e[赏金猎人] 你的里程将计入全队总里程池（当前档位 §f" + bountyStrongholdTier() + "§e）。"
                            + "每移动 " + GameConfig.MILEAGE_PER_ROLL + " 格触发一次资源抽奖；按 §fC§e 开启超级疾跑。"));
                } else {
                    p.sendSystemMessage(Component.literal(
                        "§e[猎人游戏] 你是 §a逃生者§e！60 秒后猎人开始追杀。沿检查点链前进，里程达标后前往末地击杀末影龙！"));
                    p.sendSystemMessage(Component.literal(
                        "§e[猎人游戏] 每移动 " + GameConfig.MILEAGE_PER_ROLL + " 格触发一次资源抽奖；激活检查点可抽技能卡。"));
                }
            } else if (isBounty()) {
                p.sendSystemMessage(Component.literal(
                    "§e[赏金猎人] 你是 §c赏金猎人§e！60 秒倒计时结束后开始追杀。对逃生者造成伤害与击杀都会积累§6赏金§e，"
                        + "赏金档位满即获得超级抽奖。击杀高赏金逃生者收益更高！"));
            } else {
                p.sendSystemMessage(Component.literal(
                    "§e[猎人游戏] 你是 §c猎人§e！60 秒倒计时结束后开始追杀所有逃生者。对逃生者造成伤害会积累全队士气并获得超级抽奖。"));
            }
        }
        if (isBounty()) {
            // 赏金模式：仅头号赏金逃生者发光（金色），由 BountyManager 每秒刷新；建立右侧赏金榜
            BountyManager.setupSidebar(server);
            BountyManager.refreshMarks(server);
        } else {
            // 经典模式：逃生者发光分色，每人一个独立颜色队伍（发光描边随队伍颜色）
            assignRunnerGlowTeams(server);
        }
        // 把当前对局人数写入记分板 manhunt_state（数据包可读，用于动态调整末影龙血量）
        syncStateScores(server);
        if (solo) {
            broadcast(server, "§6[猎人游戏] §b单人调试模式开始（无猎人，死亡不结算）。");
        } else {
            broadcast(server, "§6[猎人游戏] §f游戏开始！§a逃生者 " + runners.size() + " 人 §7| §c猎人 " + hunters.size() + " 人");
        }
        // 每局开始时确保末地有龙：上一局被击杀后自动重生（未击杀过则为无操作）
        ServerLevel end = server.getLevel(Level.END);
        if (end != null && end.getDragonFight() != null) {
            end.getDragonFight().tryRespawn();
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
        PendingRewardManager.depositAll(server);
        SkillDrawManager.reset();
        phase = Phase.IDLE;
        escapeTicksLeft = 0;
        soloMode = false;
        mode = Mode.CLASSIC;
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
        MileageManager.reset();
        MoraleManager.reset();
        BountyManager.reset(server);
        SprintManager.reset();
        SkillSlotManager.reset();
        ELIMINATED.clear();
        cleanupRunnerGlowTeams(server);
        cleanupBountyState(server);
        // 对局结束：清空状态分数（数据包侧后续召唤按 200 血处理）
        ServerScoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective("manhunt_state");
        if (objective != null) {
            scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly("alive_runners"), objective).set(0);
            scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly("hunters"), objective).set(0);
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isParticipant(p.getUUID())) {
                com.example.manhunt.compat.SkillPassiveBridge.clearPassives(p);
                SkillCardsBridge.resetPersistentBonuses(p);
                if (!p.isDeadOrDying()) {
                    p.getInventory().clearContent(); // 对局重置清空背包
                    TeamUtil.resetToDefault(p);
                }
                if (p.gameMode() == GameType.SPECTATOR) {
                    p.setGameMode(GameType.SURVIVAL); // 旁观/淘汰状态复位
                }
            }
            // 通知客户端清除本地状态（角色/士气条/技能库）
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                new com.example.manhunt.net.ManhuntRolePayload(false, false, false, 0, 0, List.of(), -1, false, false, false, 0, -1));
        }
        PENDING_HUNTER_RESPAWNS.clear();
        PENDING_ELIMINATED_SPECTATORS.clear();
        DeathHandler.clearAllHunterGear();
        // 昼夜速率复位
        server.overworld().dimensionType().defaultClock().ifPresent(clock ->
            server.overworld().clockManager().setRate(clock, 1.0F));
        ManhuntStateIO.save(server);
    }

    /** 赏金模式状态清理：检查点列表、命数、赦免、重生计划、赏金发光队伍。 */
    private static void cleanupBountyState(MinecraftServer server) {
        BOUNTY_CHECKPOINTS.clear();
        BOUNTY_IMMUNE_UNTIL.clear();
        PENDING_RUNNER_RESPAWNS.clear();
        LIVES.clear();
        ServerScoreboard sb = server.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam(BOUNTY_TEAM_NAME);
        if (team != null) {
            sb.removePlayerTeam(team);
        }
        Objective sidebar = sb.getObjective("manhunt_bounty");
        if (sidebar != null) {
            sb.removeObjective(sidebar);
        }
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
        if (!soloMode) {
            broadcast(server, "§6[猎人游戏] §c逃跑时间结束！猎人已开始追杀！");
        }
        ManhuntStateIO.save(server);
    }

    // ==================== 每刻调度 ====================

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        processPendingRespawns(server);
        processPendingRunnerRespawns(server);
        processPendingEliminatedSpectators(server);

        if (phase == Phase.ESCAPE) {
            escapeTicksLeft--;
            if (escapeTicksLeft <= 0) {
                beginChase(server);
            } else if (tickCounter % 20 == 0 && countdownBar != null) {
                countdownBar.setName(Component.literal(
                    "§a逃生时间 " + (escapeTicksLeft / 20) + "s"));
                countdownBar.setProgress(escapeTicksLeft / (float) (GameConfig.ESCAPE_SECONDS * 20));
            }
        }

        if (!isRunning()) {
            return;
        }
        tickCounter++;

        // 里程累计（每刻）
        MileageManager.tick(server);

        // 赏金模式：超级疾跑饥饿消耗（每刻）+ 赏金榜/头号标记/池档位（每秒）
        if (isBounty()) {
            SprintManager.tick(server);
            if (tickCounter % 20 == 0) {
                BountyManager.tick(server);
            }
        }

        // 昼夜 3:7：夜间时钟加速（每秒核对，仅速率变化时发包）
        if (tickCounter % 20 == 0) {
            updateDaylightRate(server);
        }

        if (tickCounter % GameConfig.METER_SYNC_INTERVAL_TICKS == 0) {
            long now = server.overworld().getGameTime();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                boolean participant = isParticipant(p.getUUID());
                boolean runner = TeamUtil.isRunner(p);
                boolean skillReady = participant && runner && SkillSlotManager.hasReadyCard(p);
                // 复活倒计时（猎人旁观等待）
                int respawnSeconds = -1;
                HunterRespawn hr = PENDING_HUNTER_RESPAWNS.get(p.getUUID());
                if (hr != null && now < hr.spectateUntil()) {
                    respawnSeconds = (int) Math.max(0, (hr.spectateUntil() - now + 19) / 20);
                }
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                    new com.example.manhunt.net.ManhuntRolePayload(participant, runner, skillReady,
                        isBounty() ? BountyManager.bounty() : MoraleManager.morale(),
                        isBounty() ? BountyManager.rewards() : MoraleManager.rewards(),
                        SkillSlotManager.skillIdList(p), SkillSlotManager.selectedIndex(p.getUUID()),
                        phase == Phase.ESCAPE,
                        isBounty(), runner && SprintManager.isSprinting(p.getUUID()),
                        HUNTERS.size(), respawnSeconds));
                if (participant) {
                    CompassManager.ensureCompasses(p);
                    MileageManager.syncMeter(p);
                }
            }
        }
        if (tickCounter % GameConfig.BUFF_REFRESH_INTERVAL_TICKS == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (isParticipant(p.getUUID())) {
                    TeamUtil.refreshBuffs(p);
                }
            }
        }
        if (tickCounter % GameConfig.TIER_RECALC_INTERVAL_TICKS == 0 && TierSystem.recalculate(server)) {
            broadcast(server, "§6[猎人游戏] §e人数比变化：性能档位调整为 §f" + TierSystem.displayTier() + " §e级"
                + (TierSystem.forcedTier() != null ? "（已锁定）" : ""));
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
        if (tickCounter % GameConfig.CHECKPOINT_RING_INTERVAL_TICKS == 0) {
            CheckpointManager.tickEffects(server);
        }
    }

    private static void updateCheckpointBossbar(MinecraftServer server) {
        if (isBounty()) {
            updateBountyBossbar(server);
            return;
        }
        BlockPos cp = currentCheckpoint;
        if (cp == null) {
            removeCheckpointBar(server); // 末地阶段：隐藏检查点距离条
            return;
        }
        double minDistSq = Double.MAX_VALUE;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                minDistSq = Math.min(minDistSq, p.distanceToSqr(cp.getX() + 0.5, cp.getY() + 0.5, cp.getZ() + 0.5));
            }
        }
        int dist = minDistSq == Double.MAX_VALUE ? 0 : (int) Math.sqrt(minDistSq);
        String label = currentIsStronghold ? "§d末地要塞" : "§e检查点 " + (activatedCount + 1);
        checkpointBar.setName(Component.literal(label + " §7- 距离 §f" + dist + "m"));
        checkpointBar.setProgress(Math.max(0.05F, 1.0F - Math.min(1.0F, dist / 1000.0F)));
    }

    /** 赏金模式 bossbar：普通阶段=检查点数+总里程池；要塞阶段=距要塞实际距离；末地=隐藏。 */
    private static void updateBountyBossbar(MinecraftServer server) {
        if (currentCheckpoint == null && BOUNTY_CHECKPOINTS.isEmpty()) {
            removeCheckpointBar(server); // 末地阶段：隐藏
            return;
        }
        if (currentIsStronghold) {
            // 要塞阶段：显示距要塞的实际距离
            double minDistSq = Double.MAX_VALUE;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID()) && !p.level().dimension().equals(Level.END)) {
                    minDistSq = Math.min(minDistSq, p.distanceToSqr(
                        currentCheckpoint.getX() + 0.5, currentCheckpoint.getY() + 0.5, currentCheckpoint.getZ() + 0.5));
                }
            }
            int dist = minDistSq == Double.MAX_VALUE ? 0 : (int) Math.sqrt(minDistSq);
            checkpointBar.setName(Component.literal("§d末地要塞 §7- 距离 §f" + dist + "m"));
            checkpointBar.setProgress(Math.max(0.05F, 1.0F - Math.min(1.0F, dist / 1000.0F)));
            return;
        }
        int tier = bountyStrongholdTier();
        long pool = MileageManager.poolTotal();
        checkpointBar.setName(Component.literal(
            "§e检查点 ×" + BOUNTY_CHECKPOINTS.size() + " §7| §6总里程 §f" + pool + "§7/§f" + tier));
        checkpointBar.setProgress(Math.max(0.03F, Math.min(1.0F, pool / (float) tier)));
    }

    /** 隐藏检查点距离条（末地阶段）：移除全部观众并销毁 bossbar。 */
    private static void removeCheckpointBar(MinecraftServer server) {
        if (checkpointBar != null) {
            for (ServerPlayer p : new ArrayList<>(checkpointBar.getPlayers())) {
                checkpointBar.removePlayer(p);
            }
            checkpointBar = null;
        }
    }

    // ==================== 检查点激活 ====================

    /** 由 CheckpointManager 在激活判定后调用：结算奖励并记录（经典模式）。 */
    public static void onCheckpointActivated(MinecraftServer server, ServerPlayer activator, boolean isStronghold) {
        if (isBounty()) {
            onBountyCheckpointActivated(server, activator, isStronghold);
            return;
        }
        onClassicCheckpointActivated(server, activator, isStronghold);
    }

    /** 赏金模式激活：仅激活者超级抽奖；要塞激活 → 全员彩卡 + 进入末地阶段。 */
    private static void onBountyCheckpointActivated(MinecraftServer server, ServerPlayer activator, boolean isStronghold) {
        activatedCount++;
        lastCheckpoint = currentCheckpoint;
        if (currentCheckpoint != null) {
            CheckpointManager.markActivated(currentCheckpoint, server);
        }
        String label = isStronghold ? "§d末地要塞" : "§e一个检查点";
        broadcast(server, "§6[赏金猎人] §a逃生者 §f" + activator.getName().getString() + " §a激活了" + label + "！");

        if (isStronghold) {
            // 要塞激活：全员彩卡（无超级抽奖）+ 开门物资
            if (SkillCardsBridge.available()) {
                net.minecraft.util.RandomSource rng = net.minecraft.util.RandomSource.create();
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (!TeamUtil.isRunner(p) || isEliminated(p.getUUID())) {
                        continue;
                    }
                    SkillCardsBridge.CardDraw draw = SkillCardsBridge.drawRainbow(rng, SkillSlotManager.ownedIds(p));
                    if (draw != null) {
                        SkillSlotManager.giveDrawnCard(p, draw);
                        sendRoll(p, LootRollPayload.TYPE_CARD,
                            List.of(draw.stack()), SkillCardsBridge.cardAccent(draw));
                    }
                }
            }
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                    var eyes = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_EYE, 12);
                    if (!com.example.manhunt.util.InvUtil.safeAdd(p, eyes) && !eyes.isEmpty()) {
                        p.drop(eyes, false);
                        p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§c[赏金猎人] 背包已满，部分末影之眼未拾取，已掉落在脚下！"), false);
                    }
                }
            }
            broadcast(server, "§6[赏金猎人] §d全员获得彩卡与末影之眼 ×12，开启传送门！进入末地后将不再有复活机会！");
            ManhuntStateIO.save(server);
            return;
        }
        // 普通检查点：仅激活者超级抽奖
        com.example.manhunt.loot.LootRoller.superRoll(activator);
        ManhuntStateIO.save(server);
    }

    /** 经典模式激活：激活者抽卡 + 全员抽卡 + 全员超级抽奖。 */
    private static void onClassicCheckpointActivated(MinecraftServer server, ServerPlayer activator, boolean isStronghold) {
        activatedCount++;
        lastCheckpoint = currentCheckpoint;
        if (currentCheckpoint != null) {
            CheckpointManager.markActivated(currentCheckpoint, server);
        }
        String label = isStronghold ? "§d末地要塞" : "§e第 " + activatedCount + " 个检查点";
        broadcast(server, "§6[猎人游戏] §a逃生者 §f" + activator.getName().getString()
            + " §a激活了" + label + "！");

        // 技能卡：激活者抽取（要塞固定彩卡）
        if (SkillCardsBridge.available()) {
            net.minecraft.util.RandomSource rng = net.minecraft.util.RandomSource.create();
            // 不可重复抽取：排除技能库已有的卡，全部集齐则跳过
            var exclude = SkillSlotManager.ownedIds(activator);
            SkillCardsBridge.CardDraw draw = isStronghold
                ? SkillCardsBridge.drawRainbow(rng, exclude)
                : SkillCardsBridge.drawRandom(rng, exclude);
            if (draw != null) {
                SkillSlotManager.giveDrawnCard(activator, draw);
                // 动画（激活者）
                sendRoll(activator, LootRollPayload.TYPE_CARD,
                    List.of(draw.stack()), SkillCardsBridge.cardAccent(draw));
            } else {
                activator.sendSystemMessage(Component.literal(
                    "§6[猎人游戏] §7技能库已集齐全部 14 张卡牌！"), true);
            }
            // 人人有份：其余存活逃生者各抽一张（加权，不可重复抽取）
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p == activator || !TeamUtil.isRunner(p) || isEliminated(p.getUUID())) {
                    continue;
                }
                SkillCardsBridge.CardDraw bonus = SkillCardsBridge.drawRandom(rng, SkillSlotManager.ownedIds(p));
                if (bonus != null) {
                    SkillSlotManager.giveDrawnCard(p, bonus);
                    sendRoll(p, LootRollPayload.TYPE_CARD,
                        List.of(bonus.stack()), SkillCardsBridge.cardAccent(bonus));
                }
            }
            if (aliveRunnerCount() > 1) {
                broadcast(server, "§6[猎人游戏] §d全体逃生者各获得一张技能卡！");
            }
        }

        // 超级抽奖：全体存活逃生者
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                com.example.manhunt.loot.LootRoller.superRoll(p);
            }
        }
        // 要塞检查点：保障开门物资（足量末影之眼，鞘翅只能通过抽奖获取）
        if (isStronghold) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                    var eyes = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_EYE, 12);
                    if (!com.example.manhunt.util.InvUtil.safeAdd(p, eyes) && !eyes.isEmpty()) {
                        p.drop(eyes, false); // 背包放不下：掉落在脚下并提醒
                        p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§c[猎人游戏] 背包已满，部分末影之眼未拾取，已掉落在脚下！"), false);
                    }
                }
            }
            broadcast(server, "§6[猎人游戏] §d全队已获得末影之眼 ×12，开启传送门！");
        }
        if (checkpointBar != null) {
            checkpointBar.setProgress(Math.min(1.0F, activatedCount / 10.0F));
        }
        ManhuntStateIO.save(server);
    }

    private static void sendRoll(ServerPlayer p, int type, List<net.minecraft.world.item.ItemStack> items, int accent) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
            new LootRollPayload(type, items, accent, LootRollPayload.MODE_NEW, null));
    }

    // ==================== 死亡与胜负 ====================

    /** 逃生者击杀猎人：全服公告（治疗结算在死亡事件中统一处理）。 */
    public static void onHunterKilledByRunner(MinecraftServer server, ServerPlayer killer, ServerPlayer deadHunter) {
        broadcast(server, "§6[猎人游戏] §c一名猎人被 §f" + killer.getName().getString()
            + " §c击杀！猎人将在 " + GameConfig.HUNTER_SPECTATE_TICKS / 20 + " 秒后于复活点复活。");
    }

    /** 猎人死亡：死亡点 {@code HEAL_RADIUS} 格内的所有存活逃生者（含击杀者）各恢复一定比例生命。 */
    public static void healRunnersNear(MinecraftServer server, ServerPlayer killer, net.minecraft.core.BlockPos deathPos) {
        for (ServerPlayer r : onlineAliveRunners(server)) {
            if (r != killer && r.distanceToSqr(deathPos.getX(), deathPos.getY(), deathPos.getZ())
                    > GameConfig.HEAL_RADIUS * GameConfig.HEAL_RADIUS) {
                continue;
            }
            float heal = r.getMaxHealth() * (float) GameConfig.KILLER_HEAL_FRACTION;
            r.heal(heal);
            r.sendSystemMessage(Component.literal(
                "§6[猎人游戏] §a恢复 " + Math.round(GameConfig.KILLER_HEAL_FRACTION * 100) + "% 生命（+"
                    + String.format("%.0f", heal) + "）"), true);
        }
    }

    /** 猎人死亡：规划自动重生（下一刻）与旁观等待时长；复活点在传送时刻选址。 */
    public static void scheduleHunterRespawn(MinecraftServer server, ServerPlayer deadHunter) {
        long now = server.overworld().getGameTime();
        if (isBounty()) {
            boolean diedInEnd = deadHunter.level().dimension() == Level.END;
            int spectate = diedInEnd
                ? GameConfig.BOUNTY_HUNTER_END_SPECTATE_TICKS   // 末地 60 秒
                : GameConfig.BOUNTY_HUNTER_SPECTATE_TICKS;      // 主世界 30 秒
            // 主世界复活锚点：随机选一名存活队友（其他猎人）
            UUID teammate = null;
            for (UUID id : HUNTERS) {
                if (!id.equals(deadHunter.getUUID()) && server.getPlayerList().getPlayer(id) != null) {
                    teammate = id;
                    break;
                }
            }
            PENDING_HUNTER_RESPAWNS.put(deadHunter.getUUID(),
                new HunterRespawn(now + 2L, now + 2L + spectate, diedInEnd, teammate,
                    net.minecraft.core.GlobalPos.of(deadHunter.level().dimension(), deadHunter.blockPosition())));
            deadHunter.sendSystemMessage(Component.literal(
                "§6[赏金猎人] §7将在 §f" + spectate / 20 + " §7秒后于"
                    + (diedInEnd ? "末地传送门前" : "队友附近") + "复活。"), true);
            return;
        }
        PENDING_HUNTER_RESPAWNS.put(deadHunter.getUUID(),
            new HunterRespawn(now + 2L, now + 2L + GameConfig.HUNTER_SPECTATE_TICKS, false, null,
                net.minecraft.core.GlobalPos.of(deadHunter.level().dimension(), deadHunter.blockPosition())));
    }

    public static boolean isHunterRespawnPending(UUID id) {
        return PENDING_HUNTER_RESPAWNS.containsKey(id);
    }

    /** 把当前对局人数写入记分板 objective "manhunt_state"（数据包可读，用于动态调整末影龙血量等）。 */
    private static void syncStateScores(MinecraftServer server) {
        ServerScoreboard sb = server.getScoreboard();
        Objective objective = sb.getObjective("manhunt_state");
        if (objective == null) {
            objective = sb.addObjective("manhunt_state", ObjectiveCriteria.DUMMY,
                Component.literal("Manhunt 状态"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly("alive_runners"), objective).set(aliveRunnerCount());
        sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly("hunters"), objective).set(HUNTERS.size());
    }

    /** 逃生者死亡：经典=直接淘汰；赏金=扣命数（末地内或命数耗尽才淘汰），复活并失去 1/3 里程。 */
    public static void onRunnerDeath(MinecraftServer server, ServerPlayer deadRunner) {
        onRunnerDeath(server, deadRunner, null);
    }

    public static void onRunnerDeath(MinecraftServer server, ServerPlayer deadRunner, ServerPlayer killer) {
        // 赏金击杀结算（任意一条命被击杀都计；末地内改为击杀者回血）
        if (isBounty() && killer != null && TeamUtil.isHunter(killer)) {
            boolean inEnd = deadRunner.level().dimension() == Level.END;
            if (inEnd) {
                float heal = killer.getMaxHealth() * (float) GameConfig.BOUNTY_END_HEAL;
                killer.heal(heal);
                killer.sendSystemMessage(Component.literal(
                    "§6[赏金猎人] §a末地击杀！恢复 " + Math.round(GameConfig.BOUNTY_END_HEAL * 100) + "% 生命（+"
                        + String.format("%.0f", heal) + "）"), true);
            } else {
                long base = MileageManager.mileage(deadRunner.getUUID()) / GameConfig.BOUNTY_KILL_DIVISOR;
                double mult = BountyManager.killMultiplier(server, deadRunner.getUUID());
                BountyManager.addBounty(server, (int) Math.max(1, Math.round(base * mult)));
                killer.sendSystemMessage(Component.literal(
                    "§6[赏金猎人] §a击杀 §f" + deadRunner.getName().getString()
                        + "§a！赏金 +" + Math.round(base * mult)
                        + (mult > 1.0 ? " §7(基础 " + base + " ×" + mult + ")" : "")), true);
            }
        }
        if (isBounty() && deadRunner.level().dimension() != Level.END) {
            // 单人调试同样消耗命数并复活（仅胜负判定由 soloMode 屏蔽）
            int lives = LIVES.getOrDefault(deadRunner.getUUID(), 1) - 1;
            LIVES.put(deadRunner.getUUID(), Math.max(0, lives));
            if (lives > 0) {
                // 还有命：失去 1/3 里程 + 自动重生（距死亡位置 300–500 格）+ 罗盘赦免
                long before = MileageManager.mileage(deadRunner.getUUID());
                long after = before - Math.round(before * GameConfig.BOUNTY_MILEAGE_DEATH_FRACTION);
                MileageManager.setMileage(deadRunner, Math.max(0, after));
                scheduleRunnerRespawn(server, deadRunner);
                setCompassImmune(server, deadRunner.getUUID(), GameConfig.BOUNTY_COMPASS_IMMUNITY_TICKS);
                broadcast(server, "§6[赏金猎人] §a逃生者 §f" + deadRunner.getName().getString()
                    + " §c被击杀！§7剩余命数 §f" + lives
                    + " §7| 里程 -" + (before - after)
                    + (killer instanceof ServerPlayer k ? " §7| 赏金猎人 §f" + k.getName().getString() : ""));
                ManhuntStateIO.save(server);
                return;
            }
        }
        PendingRewardManager.discard(deadRunner.getUUID());
        ELIMINATED.add(deadRunner.getUUID());
        // 原地旁观：重生后回到死亡位置（而非世界出生点）
        PENDING_ELIMINATED_SPECTATORS.put(deadRunner.getUUID(),
            GlobalPos.of(deadRunner.level().dimension(), deadRunner.blockPosition()));
        syncStateScores(server); // 淘汰后刷新存活逃生者人数
        if (isBounty()) {
            broadcast(server, "§6[赏金猎人] §a逃生者 §f" + deadRunner.getName().getString() + " §c被淘汰！"
                + (deadRunner.level().dimension() == Level.END ? " §7（末地死亡无复活）" : " §7（命数耗尽）")
                + " (剩余 " + aliveRunnerCount() + " 人)");
        } else {
            broadcast(server, "§6[猎人游戏] §a逃生者 §f" + deadRunner.getName().getString() + " §c被淘汰！"
                + " (剩余 " + aliveRunnerCount() + " 人)");
        }
        if (!soloMode && aliveRunnerCount() == 0) {
            endGame(server, isBounty() ? "§c赏金猎人获胜！所有逃生者已被淘汰。"
                : "§c猎人获胜！所有逃生者已被淘汰。");
        }
        ManhuntStateIO.save(server);
    }

    /** 赏金模式：规划逃生者重生（下一刻强制重生 → 传送至距死亡位置 300–500 格地表）。 */
    private static void scheduleRunnerRespawn(MinecraftServer server, ServerPlayer deadRunner) {
        net.minecraft.core.GlobalPos pos = RespawnSelector.selectRunnerRespawn(server, deadRunner);
        PENDING_RUNNER_RESPAWNS.put(deadRunner.getUUID(),
            new RunnerRespawn(pos, server.overworld().getGameTime() + 2L));
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

    // ==================== 猎人重生流程 ====================

    /**
     * 猎人重生：死亡 → 下一刻服务端强制重生（跳过死亡界面）→ 10 秒旁观者模式 →
     * 传送至复活点（传送时刻选址，末地门覆盖生效）+ 装备回穿 + 回生存模式 + 公告坐标。
     * 掉线玩家保留计划与装备，重连后继续执行。
     */
    private static void processPendingRespawns(MinecraftServer server) {
        if (PENDING_HUNTER_RESPAWNS.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        List<UUID> done = new ArrayList<>();
        for (Map.Entry<UUID, HunterRespawn> e : PENDING_HUNTER_RESPAWNS.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                continue; // 离线：保留计划与暂存装备，重连后继续
            }
            HunterRespawn hr = e.getValue();
            if (!p.isAlive()) {
                if (now >= hr.respawnAt()) {
                    // 服务端强制重生（仅对死亡玩家生效，避免与玩家手动重生冲突）
                    p.connection.handleClientCommand(new ServerboundClientCommandPacket(
                        ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                    // 原地旁观：重生后立刻回到死亡位置转为旁观者（而非世界出生点）
                    ServerPlayer np = server.getPlayerList().getPlayer(e.getKey());
                    if (np != null && np.isAlive() && hr.deathPos() != null) {
                        ServerLevel deathLevel = server.getLevel(hr.deathPos().dimension());
                        net.minecraft.core.BlockPos dp = hr.deathPos().pos();
                        if (deathLevel != null) {
                            np.teleportTo(deathLevel, dp.getX() + 0.5, dp.getY() + 1, dp.getZ() + 0.5,
                                Set.of(), np.getYRot(), np.getXRot(), false);
                        }
                        np.setGameMode(GameType.SPECTATOR);
                    }
                }
                continue;
            }
            if (now < hr.spectateUntil()) {
                if (p.gameMode() != GameType.SPECTATOR) {
                    p.setGameMode(GameType.SPECTATOR);
                }
                continue;
            }
            GlobalPos pos;
            if (hr.bountyEnd()) {
                GlobalPos portal = strongholdPortalPos;
                pos = portal != null ? portal : RespawnSelector.selectHunterRespawn(server, p);
            } else if (hr.teammate() != null) {
                pos = RespawnSelector.selectNearTeammate(server, hr.teammate(), p);
            } else {
                pos = RespawnSelector.selectHunterRespawn(server, p);
            }
            ServerLevel level = server.getLevel(pos.dimension());
            net.minecraft.core.BlockPos bp = pos.pos();
            if (level != null) {
                p.teleportTo(level, bp.getX() + 0.5, bp.getY() + 1, bp.getZ() + 0.5,
                    Set.of(), p.getYRot(), p.getXRot(), false);
            }
            DeathHandler.restoreHunterGear(p);
            TeamUtil.applyBaseAttributes(p);
            TeamUtil.refreshBuffs(p);
            p.setGameMode(GameType.SURVIVAL);
            MileageManager.syncMeter(p);
            p.sendSystemMessage(Component.literal(
                "§6[猎人游戏] §a你已在复活点复活（§f" + bp.getX() + ", " + bp.getY() + ", " + bp.getZ() + "§a）。"));
            done.add(e.getKey());
        }
        for (UUID id : done) {
            PENDING_HUNTER_RESPAWNS.remove(id);
        }
    }

    /** 淘汰者原地旁观：强制重生 → 回到死亡位置 → 旁观者模式（经典与赏金一致）。 */
    private static void processPendingEliminatedSpectators(MinecraftServer server) {
        if (PENDING_ELIMINATED_SPECTATORS.isEmpty()) {
            return;
        }
        List<UUID> done = new ArrayList<>();
        for (Map.Entry<UUID, GlobalPos> e : PENDING_ELIMINATED_SPECTATORS.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                continue; // 离线：保留计划，重连后处理
            }
            if (!p.isAlive()) {
                // 死亡处理已在当刻完成，可直接强制重生（跳过死亡界面）
                p.connection.handleClientCommand(new ServerboundClientCommandPacket(
                    ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                continue;
            }
            if (p.gameMode() != GameType.SPECTATOR) {
                ServerLevel level = server.getLevel(e.getValue().dimension());
                net.minecraft.core.BlockPos dp = e.getValue().pos();
                if (level != null) {
                    p.teleportTo(level, dp.getX() + 0.5, dp.getY() + 1, dp.getZ() + 0.5,
                        Set.of(), p.getYRot(), p.getXRot(), false);
                }
                if (isBounty()) {
                    DeathHandler.restoreHunterGear(p); // 赏金模式：装备不丢失
                }
                p.setGameMode(GameType.SPECTATOR);
            }
            done.add(e.getKey());
        }
        for (UUID id : done) {
            PENDING_ELIMINATED_SPECTATORS.remove(id);
        }
    }

    // ==================== 昼夜与发光队伍 ====================

    /** 赏金模式逃生者重生：强制重生（跳过死亡界面）→ 传送至预定复活点 → 生存模式。 */
    private static void processPendingRunnerRespawns(MinecraftServer server) {
        if (PENDING_RUNNER_RESPAWNS.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        List<UUID> done = new ArrayList<>();
        for (Map.Entry<UUID, RunnerRespawn> e : PENDING_RUNNER_RESPAWNS.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                continue; // 离线：重连后继续
            }
            if (!p.isAlive()) {
                if (now >= e.getValue().respawnAt()) {
                    p.connection.handleClientCommand(new ServerboundClientCommandPacket(
                        ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                }
                continue;
            }
            GlobalPos pos = e.getValue().pos();
            ServerLevel level = server.getLevel(pos.dimension());
            net.minecraft.core.BlockPos bp = pos.pos();
            if (level != null) {
                p.teleportTo(level, bp.getX() + 0.5, bp.getY() + 1, bp.getZ() + 0.5,
                    Set.of(), p.getYRot(), p.getXRot(), false);
            }
            TeamUtil.applyBaseAttributes(p);
            TeamUtil.fullyRestore(p);
            TeamUtil.refreshBuffs(p);
            p.setGameMode(GameType.SURVIVAL);
            MileageManager.syncMeter(p);
            p.sendSystemMessage(Component.literal(
                "§6[赏金猎人] §a你已在复活点复活（§f" + bp.getX() + ", " + bp.getY() + ", " + bp.getZ()
                    + "§a），60 秒内罗盘无法追踪你。"));
            done.add(e.getKey());
        }
        for (UUID id : done) {
            PENDING_RUNNER_RESPAWNS.remove(id);
        }
        if (!done.isEmpty()) {
            ManhuntStateIO.save(server);
        }
    }

    /** 昼夜 3:7（日:夜 = 7:3）：夜间时钟加速，仅在速率变化时调用 setRate。 */
    private static void updateDaylightRate(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        overworld.dimensionType().defaultClock().ifPresent(clock -> {
            var cm = overworld.clockManager();
            float want = cm.getTotalTicks(clock) % 24000L >= GameConfig.NIGHT_START_TICK
                ? GameConfig.NIGHT_CLOCK_RATE
                : 1.0F;
            if (Math.abs(cm.getRate(clock) - want) > 1.0E-4F) {
                cm.setRate(clock, want);
            }
        });
    }

    /** 开局：为每位逃生者创建独立颜色的发光队伍。 */
    private static void assignRunnerGlowTeams(MinecraftServer server) {
        ServerScoreboard sb = server.getScoreboard();
        int i = 0;
        for (UUID id : RUNNERS) {
            PlayerTeam team = sb.addPlayerTeam(GLOW_TEAM_PREFIX + i);
            team.setColor(java.util.Optional.of(GLOW_COLORS[i % GLOW_COLORS.length]));
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                sb.addPlayerToTeam(p.getScoreboardName(), team);
            }
            i++;
        }
    }

    /** 结束：移除全部发光分色队伍（成员随之出队）。 */
    private static void cleanupRunnerGlowTeams(MinecraftServer server) {
        ServerScoreboard sb = server.getScoreboard();
        for (PlayerTeam team : new ArrayList<>(sb.getPlayerTeams())) {
            if (team.getName().startsWith(GLOW_TEAM_PREFIX)) {
                sb.removePlayerTeam(team);
            }
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

    static boolean rawSoloMode() { return soloMode; }

    static BlockPos rawLastCheckpoint() { return lastCheckpoint; }

    static void restoreState(Phase p, Set<UUID> hunters, Set<UUID> runners, Set<UUID> eliminated,
                             int activated, BlockPos currentCp, boolean stronghold,
                             BlockPos lastCp, boolean solo, Mode m,
                             Map<UUID, Integer> lives, List<BlockPos> bountyCps, long pool) {
        phase = p;
        HUNTERS.clear();
        HUNTERS.addAll(hunters);
        RUNNERS.clear();
        RUNNERS.addAll(runners);
        ELIMINATED.clear();
        ELIMINATED.addAll(eliminated);
        activatedCount = activated;
        currentCheckpoint = currentCp;
        currentIsStronghold = stronghold && currentCp != null;
        lastCheckpoint = lastCp;
        soloMode = solo;
        mode = m;
        escapeTicksLeft = 0;
        BOUNTY_CHECKPOINTS.clear();
        BOUNTY_CHECKPOINTS.addAll(bountyCps);
        LIVES.clear();
        LIVES.putAll(lives);
        MileageManager.restorePool(pool);
    }

    static Map<UUID, Integer> rawLives() { return LIVES; }

    static List<BlockPos> rawBountyCheckpoints() { return BOUNTY_CHECKPOINTS; }

    static void reattachBossbars(MinecraftServer server) {
        if (phase == Phase.RUNNING) {
            checkpointBar = new ServerBossEvent(UUID.randomUUID(),
                Component.literal("检查点进度"),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
            if (isBounty()) {
                BountyManager.setupSidebar(server);
                BountyManager.refreshMarks(server);
            }
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                    checkpointBar.addPlayer(p);
                }
            }
        }
    }
}
