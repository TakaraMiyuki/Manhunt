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
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;
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

    /** 猎人重生计划：死亡 → 自动重生（10 秒旁观者）→ 传送至复活点（选址在传送时刻计算）。 */
    private record HunterRespawn(long respawnAt, long spectateUntil) {}

    private static final Map<UUID, HunterRespawn> PENDING_HUNTER_RESPAWNS = new HashMap<>();
    /** 逃生者发光分色队伍前缀：发光描边颜色 = 队伍颜色（循环取色，不用红色）。 */
    private static final String GLOW_TEAM_PREFIX = "manhunt_glow_";
    private static final TeamColor[] GLOW_COLORS = {
        TeamColor.GOLD, TeamColor.GREEN, TeamColor.AQUA, TeamColor.LIGHT_PURPLE,
        TeamColor.YELLOW, TeamColor.WHITE, TeamColor.BLUE, TeamColor.DARK_GREEN,
        TeamColor.DARK_AQUA, TeamColor.DARK_PURPLE, TeamColor.GRAY, TeamColor.DARK_BLUE};

    // ==================== 状态访问 ====================

    public static Phase phase() { return phase; }

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

    // ==================== 生命周期 ====================

    /**
     * 开局：分配队伍、生成首个检查点、进入逃跑倒计时。
     * @return 错误提示；null 表示成功
     */
    public static String start(MinecraftServer server, Set<UUID> hunters, Set<UUID> runners, boolean solo) {
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
        activatedCount = 0;
        currentCheckpoint = null;
        currentIsStronghold = false;
        lastCheckpoint = null;
        forceNextStronghold = false;
        MileageManager.reset();
        MoraleManager.reset();
        SkillSlotManager.reset();
        TierSystem.reset();

        CheckpointManager.generateFirst(server);

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
            // 重置技能卡永久加成（赤鳞跃动等），避免跨局残留
            SkillCardsBridge.resetPersistentBonuses(p);
            com.example.manhunt.compat.CraftingOnAStickBridge.giveStick(p);
            TeamUtil.applyBaseAttributes(p);
            TeamUtil.fullyRestore(p);
            TeamUtil.refreshBuffs(p);
            if (TeamUtil.isRunner(p)) {
                TeamUtil.giveInitialKit(p);
                CompassManager.giveCheckpointCompass(p);
            }
            MileageManager.syncMeter(p);
            countdownBar.addPlayer(p);
            if (TeamUtil.isRunner(p)) {
                p.sendSystemMessage(Component.literal(
                    "§e[猎人游戏] 你是 §a逃生者§e！60 秒后猎人开始追杀。沿检查点链前进，里程达标后前往末地击杀末影龙！"));
                p.sendSystemMessage(Component.literal(
                    "§e[猎人游戏] 每移动 " + GameConfig.MILEAGE_PER_ROLL + " 格触发一次资源抽奖；激活检查点可抽技能卡。"));
            } else {
                p.sendSystemMessage(Component.literal(
                    "§e[猎人游戏] 你是 §c猎人§e！60 秒倒计时结束后开始追杀所有逃生者。对逃生者造成伤害会积累全队士气并获得超级抽奖。"));
            }
        }
        // 逃生者发光分色：每人一个独立颜色队伍（发光描边随队伍颜色）
        assignRunnerGlowTeams(server);
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
        phase = Phase.IDLE;
        escapeTicksLeft = 0;
        soloMode = false;
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
        SkillSlotManager.reset();
        ELIMINATED.clear();
        cleanupRunnerGlowTeams(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isParticipant(p.getUUID())) {
                SkillCardsBridge.resetPersistentBonuses(p);
                if (!p.isDeadOrDying()) {
                    p.getInventory().clearContent(); // 对局重置清空背包
                    TeamUtil.resetToDefault(p);
                }
                if (p.gameMode() == GameType.SPECTATOR) {
                    p.setGameMode(GameType.SURVIVAL); // 旁观/淘汰状态复位
                }
            }
            // 通知客户端清除本地状态（角色/士气条）
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                new com.example.manhunt.net.ManhuntRolePayload(false, false, false, 0, 0));
        }
        PENDING_HUNTER_RESPAWNS.clear();
        DeathHandler.clearAllHunterGear();
        // 昼夜速率复位
        server.overworld().dimensionType().defaultClock().ifPresent(clock ->
            server.overworld().clockManager().setRate(clock, 1.0F));
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

        // 昼夜 3:7：夜间时钟加速（每秒核对，仅速率变化时发包）
        if (tickCounter % 20 == 0) {
            updateDaylightRate(server);
        }

        if (tickCounter % GameConfig.METER_SYNC_INTERVAL_TICKS == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                boolean participant = isParticipant(p.getUUID());
                boolean runner = TeamUtil.isRunner(p);
                boolean skillReady = participant && runner && SkillSlotManager.hasReadyCard(p);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                    new com.example.manhunt.net.ManhuntRolePayload(participant, runner, skillReady,
                        MoraleManager.morale(), MoraleManager.rewards()));
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
        if (tickCounter % GameConfig.CARD_SLOT_GUARD_INTERVAL_TICKS == 0) {
            SkillSlotManager.tickGuard(server);
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
        BlockPos cp = currentCheckpoint;
        if (cp == null) {
            checkpointBar.setName(Component.literal("§d前往末地击杀§5末影龙§d！"));
            checkpointBar.setProgress(1.0F);
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

    // ==================== 检查点激活 ====================

    /** 由 CheckpointManager 在激活判定后调用：结算奖励并记录。 */
    public static void onCheckpointActivated(MinecraftServer server, ServerPlayer activator, boolean isStronghold) {
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
            new LootRollPayload(type, items, accent, LootRollPayload.MODE_NEW));
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
        PENDING_HUNTER_RESPAWNS.put(deadHunter.getUUID(),
            new HunterRespawn(now + 2L, now + 2L + GameConfig.HUNTER_SPECTATE_TICKS));
    }

    public static boolean isHunterRespawnPending(UUID id) {
        return PENDING_HUNTER_RESPAWNS.containsKey(id);
    }

    /** 逃生者死亡：淘汰；全部淘汰则猎人获胜（单人调试模式除外）。 */
    public static void onRunnerDeath(MinecraftServer server, ServerPlayer deadRunner) {
        PendingRewardManager.discard(deadRunner.getUUID());
        ELIMINATED.add(deadRunner.getUUID());
        broadcast(server, "§6[猎人游戏] §a逃生者 §f" + deadRunner.getName().getString() + " §c被淘汰！"
            + " (剩余 " + aliveRunnerCount() + " 人)");
        if (!soloMode && aliveRunnerCount() == 0) {
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
                }
                continue;
            }
            if (now < hr.spectateUntil()) {
                if (p.gameMode() != GameType.SPECTATOR) {
                    p.setGameMode(GameType.SPECTATOR);
                }
                continue;
            }
            GlobalPos pos = RespawnSelector.selectHunterRespawn(server, p);
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

    // ==================== 昼夜与发光队伍 ====================

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
                             BlockPos lastCp, boolean solo) {
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
        escapeTicksLeft = 0;
    }

    static void reattachBossbars(MinecraftServer server) {
        if (phase == Phase.RUNNING) {
            checkpointBar = new ServerBossEvent(UUID.randomUUID(),
                Component.literal("检查点进度"),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
            checkpointBar.setProgress(Math.min(1.0F, activatedCount / 10.0F));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                    checkpointBar.addPlayer(p);
                }
            }
        }
    }
}
