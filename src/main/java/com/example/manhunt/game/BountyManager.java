package com.example.manhunt.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.example.manhunt.GameConfig;
import com.example.manhunt.loot.LootRoller;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * 赏金量表（全猎人共享，占用猎人经验条 5 秒）与头号赏金标记。
 * <ul>
 *   <li>赏金来源：对逃生者造成伤害（1 伤 = 3 赏金）、击杀逃生者（死者里程 ÷ 3 × 排名倍率）；</li>
 *   <li>档位 {300, 800, 1600, 2800, 4400, 6500} 之后每档 +2500，跨档每个在线猎人各一次超级抽奖；</li>
 *   <li>头号赏金：按个人里程排名前 N 名（≤3 人→1、4–7→2、≥8→3），金色发光，
 *       击杀标记者按名次倍率结算（第 2 名 1.25×，第 1 名 1.5×/2×，第 3 名 1.8×/2×）。</li>
 * </ul>
 */
public final class BountyManager {
    private BountyManager() {}

    private static int bounty = 0;
    private static int rewards = 0;
    private static long lastGainTime = Long.MIN_VALUE;
    /** 当前头号赏金标记（按排名 0-based）。 */
    private static final List<UUID> MARKED = new ArrayList<>();
    /** 头号赏金发光队伍（金色描边）。 */
    private static final String BOUNTY_TEAM_NAME = "manhunt_bounty";

    /** 当前生效的档位表（按赏金猎人数：1 名 / 2 名 / 3 名及以上）。 */
    public static int[] tiers() {
        int hunters = ManhuntGame.hunters().size();
        return hunters <= 1 ? GameConfig.BOUNTY_TIERS_1H
            : hunters == 2 ? GameConfig.BOUNTY_TIERS_2H
            : GameConfig.BOUNTY_TIERS_3P;
    }

    /** 第 rewardsIndex 档的阈值；表末之后按步长外推。 */
    public static int threshold(int rewardsIndex) {
        int[] t = tiers();
        return rewardsIndex < t.length
            ? t[rewardsIndex]
            : t[t.length - 1] + GameConfig.BOUNTY_TIER_STEP_AFTER * (rewardsIndex - t.length + 1);
    }

    /** 累计赏金并结算跨过的档位（跨档 = 每个在线猎人各一次超级抽奖）。 */
    public static void addBounty(MinecraftServer server, int amount) {
        if (amount <= 0) {
            return;
        }
        lastGainTime = server.overworld().getGameTime();
        bounty += amount;
        int newRewards = 0;
        while (bounty >= threshold(rewards)) {
            rewards++;
            newRewards++;
        }
        if (newRewards > 0) {
            ManhuntGame.broadcast(server, "§6[赏金猎人] §6赏金量表高涨（" + bounty + "）！全体赏金猎人获得超级抽奖！");
            for (int i = 0; i < newRewards; i++) {
                for (ServerPlayer hunter : server.getPlayerList().getPlayers()) {
                    if (TeamUtil.isHunter(hunter)) {
                        LootRoller.superRoll(hunter);
                    }
                }
            }
        }
    }

    /** 调试：直接设置赏金值（按新值重算档数，补触发跨档超级抽奖）。 */
    public static void setBounty(MinecraftServer server, int value) {
        bounty = Math.max(0, value);
        int newRewards = 0;
        while (bounty >= threshold(rewards)) {
            rewards++;
            newRewards++;
        }
        for (int i = 0; i < newRewards; i++) {
            for (ServerPlayer hunter : server.getPlayerList().getPlayers()) {
                if (TeamUtil.isHunter(hunter)) {
                    LootRoller.superRoll(hunter);
                }
            }
        }
    }

    public static int bounty() {
        return bounty;
    }

    public static int rewards() {
        return rewards;
    }

    /** 赏金量表是否临时接管猎人经验条（赏金增长后 5 秒内）。 */
    public static boolean showingOnMeter(MinecraftServer server) {
        return lastGainTime != Long.MIN_VALUE
            && server.overworld().getGameTime() - lastGainTime < GameConfig.MORALE_METER_SHOW_TICKS;
    }

    // ==================== 头号赏金 ====================

    /** 每 20 刻刷新：池档位检查（技能三选一）+ 头号标记 + 侧边栏。 */
    public static void tick(MinecraftServer server) {
        refreshMarks(server);
        checkPoolTiers(server);
        updateSidebar(server);
    }

    /** 标记名次（0-based）的击杀倍率；未标记返回 1。 */
    public static double killMultiplier(MinecraftServer server, UUID runnerId) {
        int rank = MARKED.indexOf(runnerId);
        if (rank < 0) {
            return 1.0;
        }
        int n = ManhuntGame.runners().size();
        return switch (rank) {
            case 0 -> n >= 8 ? GameConfig.BOUNTY_MULT_RANK1_LARGE : GameConfig.BOUNTY_MULT_RANK1_SMALL;
            case 1 -> GameConfig.BOUNTY_MULT_RANK2;
            default -> n >= 8 ? GameConfig.BOUNTY_MULT_RANK3_LARGE : GameConfig.BOUNTY_MULT_RANK3_SMALL;
        };
    }

    public static boolean isMarked(UUID runnerId) {
        return MARKED.contains(runnerId);
    }

    /** 标记者在赏金榜的名次（1-based）；未标记返回 -1。 */
    public static int markRank(UUID runnerId) {
        int rank = MARKED.indexOf(runnerId);
        return rank < 0 ? -1 : rank + 1;
    }

    /** 头号标记数：逃生者 ≤3 / 4–7 / ≥8 → 1 / 2 / 3。 */
    private static int markCount(int runners) {
        int[] table = GameConfig.BOUNTY_MARK_TABLE;
        if (runners <= 3) return table[0];
        if (runners <= 7) return table[1];
        return table[2];
    }

    /** 每秒：按个人里程重新排名，维护金色发光队伍与发光效果。 */
    public static void refreshMarks(MinecraftServer server) {
        List<ServerPlayer> alive = new ArrayList<>(ManhuntGame.onlineAliveRunners(server));
        alive.sort(Comparator.comparingLong((ServerPlayer p) -> MileageManager.mileage(p.getUUID())).reversed());
        List<UUID> next = new ArrayList<>();
        int limit = markCount(ManhuntGame.runners().size());
        for (ServerPlayer p : alive) {
            if (next.size() < limit) {
                next.add(p.getUUID());
            }
        }
        MARKED.clear();
        MARKED.addAll(next);

        // 金色发光队伍维护：标记者入队（发光由 team color 金色决定），跌出即出队
        ServerScoreboard sb = server.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam(BOUNTY_TEAM_NAME);
        if (team == null) {
            team = sb.addPlayerTeam(BOUNTY_TEAM_NAME);
            team.setColor(java.util.Optional.of(net.minecraft.world.scores.TeamColor.GOLD));
        }
        java.util.Set<String> members = new java.util.HashSet<>(team.getPlayers());
        for (UUID id : MARKED) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && !members.contains(p.getScoreboardName())) {
                sb.addPlayerToTeam(p.getScoreboardName(), team);
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING,
                    GameConfig.BUFF_REFRESH_INTERVAL_TICKS * 3, 0, true, false), null);
            }
        }
        for (String name : new ArrayList<>(members)) {
            UUID id = resolveUuid(server, name);
            if (id == null || !MARKED.contains(id)) {
                sb.removePlayerFromTeam(name, team);
            }
        }
    }

    private static UUID resolveUuid(MinecraftServer server, String scoreboardName) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getScoreboardName().equals(scoreboardName)) {
                return p.getUUID();
            }
        }
        return null;
    }

    // ==================== 总里程池档位（技能三选一） ====================

    private static long lastSkillTier = 0;

    /** 池每跨一个技能档 → 全体在线存活逃生者各一次三选一技能抽奖（封顶 BOUNTY_SKILL_DIVISOR=6 次）。 */
    private static void checkPoolTiers(MinecraftServer server) {
        // 池达到要塞档：立即切换为仅剩要塞（此后不再触发技能抽奖）
        if (ManhuntGame.currentCheckpoint() == null && !ManhuntGame.bountyCheckpoints().isEmpty()
                && MileageManager.poolTotal() >= ManhuntGame.bountyStrongholdTier()) {
            CheckpointManager.switchToStronghold(server);
        }
        int tier = ManhuntGame.bountySkillTier();
        if (tier <= 0) {
            return;
        }
        long pool = MileageManager.poolTotal();
        int crossed = (int) Math.min(GameConfig.BOUNTY_SKILL_DIVISOR, pool / tier); // 进末地前最多 6 张
        if (crossed <= lastSkillTier) {
            return;
        }
        lastSkillTier = crossed;
        ManhuntGame.broadcast(server, "§6[赏金猎人] §d总里程池达到 §f" + (crossed * tier)
            + "§d！全体逃生者触发§d技能抽奖§d（三选一）！");
        com.example.manhunt.loot.SkillDrawManager.offerAll(server);
    }

    public static void restoreSkillTier(long pool, int tier) {
        lastSkillTier = tier > 0 ? (int) (pool / tier) : 0;
    }

    public static long lastSkillTier() {
        return lastSkillTier;
    }

    // ==================== 侧边栏赏金榜 ====================

    private static Objective sidebar;

    /** 建立右侧赏金榜（DUMMY 目标挂 SIDEBAR，分数 = 击杀可得基础赏金）。 */
    public static void setupSidebar(MinecraftServer server) {
        ServerScoreboard sb = server.getScoreboard();
        sidebar = sb.getObjective("manhunt_bounty");
        if (sidebar == null) {
            sidebar = sb.addObjective("manhunt_bounty", ObjectiveCriteria.DUMMY,
                Component.literal("§6赏金榜"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        sb.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR, sidebar);
    }

    /** 每秒刷新侧边栏：每行一名存活逃生者，行名 = 排名+名字（标记者金色含倍率）。 */
    private static void updateSidebar(MinecraftServer server) {
        if (sidebar == null) {
            return;
        }
        ServerScoreboard sb = server.getScoreboard();
        List<ServerPlayer> alive = new ArrayList<>(ManhuntGame.onlineAliveRunners(server));
        alive.sort(Comparator.comparingLong((ServerPlayer p) -> MileageManager.mileage(p.getUUID())).reversed());
        int rank = 1;
        for (ServerPlayer p : alive) {
            long base = MileageManager.mileage(p.getUUID()) / GameConfig.BOUNTY_KILL_DIVISOR;
            double mult = killMultiplier(server, p.getUUID());
            String name;
            if (isMarked(p.getUUID())) {
                name = "§6#" + rank + " " + p.getName().getString() + " §7(×" + trim(mult) + ")";
            } else {
                name = "§f#" + rank + " " + p.getName().getString();
            }
            sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly(name), sidebar).set((int) Math.min(Integer.MAX_VALUE, base));
            rank++;
        }
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    // ==================== 状态 ====================

    public static List<UUID> marked() {
        return MARKED;
    }

    public static void restore(int savedBounty, int savedRewards, List<UUID> marks, long skillTierMark) {
        bounty = savedBounty;
        rewards = savedRewards;
        MARKED.clear();
        MARKED.addAll(marks);
        lastSkillTier = skillTierMark;
    }

    public static int[] snapshot() {
        return new int[]{bounty, rewards};
    }

    public static void reset() {
        bounty = 0;
        rewards = 0;
        lastGainTime = Long.MIN_VALUE;
        MARKED.clear();
        lastSkillTier = 0;
        sidebar = null;
    }

    /** 对局结束：状态复位（侧边栏目标与发光队伍由 ManhuntGame.cleanupBountyState 移除）。 */
    public static void reset(MinecraftServer server) {
        reset();
    }
}
