package com.example.manhunt;

/**
 * 全部可调数值。平衡性调整只改这一个文件。
 */
public final class GameConfig {
    private GameConfig() {}

    /** 开局给予逃生者的逃跑时间（秒）。 */
    public static final int ESCAPE_SECONDS = 60;

    // ==================== 里程与抽奖 ====================
    /** 每积累多少里程触发一次资源抽奖。 */
    public static final int MILEAGE_PER_ROLL = 200;
    /** 里程奖池档位阈值（<1000 / <2000 / <3000 / 3000+ 共四档；3000 同时解锁要塞检查点）。 */
    public static final int[] MILEAGE_TIERS = {1000, 2000, 3000};
    /** 每次资源抽奖抽到的物品种数。 */
    public static final int ROLL_ITEMS = 5;
    /** 超级抽奖（检查点/士气触发）一次抽到的物品种数。 */
    public static final int SUPER_ROLL_ITEMS = 7;
    /** 抽奖展示动画时长（刻）。 */
    public static final int ROLL_ANIMATION_TICKS = 90;
    /** 抽奖动画开始滚动的延迟（刻，用于淡入）。 */
    public static final int ROLL_ANIMATION_INTRO_TICKS = 12;
    /** 达到该里程后，下一个刷新的检查点固定指向末地要塞（任一逃生者达标即切换）。 */
    public static final int STRONGHOLD_MILEAGE = 3000;
    /** 单刻水平位移超过该值（格）不计里程（防传送/坠落刷里程）。 */
    public static final double MILEAGE_MAX_TICK_DIST = 10.0;
    /** 猎人里程获取效率（相对逃生者的倍率）。 */
    public static final double HUNTER_MILEAGE_FACTOR = 1.0 / 2.0;
    /** 士气增长后，士气量表接管猎人经验条的持续时间（刻）。 */
    public static final int MORALE_METER_SHOW_TICKS = 100;
    /** 摔落伤害的保底生命值（保留最后 2 点 = 1 颗心，不会摔死）。 */
    public static final float FALL_MIN_HEALTH = 2.0F;
    /** 士气阈值序列（封顶已移除：600 之后每档固定 +200）。 */
    public static final int[] MORALE_THRESHOLDS = {50, 100, 150, 200, 300, 400, 600};
    /** 600 之后每档固定增加 200。 */
    public static final int MORALE_STEP_AFTER_LAST = 200;
    /** 猎人死亡后旁观等待时长（刻）——10 秒后传送至复活点。 */
    public static final int HUNTER_SPECTATE_TICKS = 200;
    /** 猎人罗盘金脉冲的触发距离（格）。 */
    public static final double TRACKING_COMPASS_CLOSE_DIST = 50.0;
    /** 罗盘偏航提示：朝向偏离罗盘指向超过该角度（度）视为偏航。 */
    public static final double COMPASS_DEVIATION_ANGLE = 60.0;
    /** 罗盘偏航提示：持续偏航该时长（刻）后红色脉冲，修正方向解除。 */
    public static final int COMPASS_DEVIATION_TICKS = 60;
    /** 玩家进食加速倍率（相对原版）。 */
    public static final double EAT_SPEED_FACTOR = 1.5;
    /** 技能库持有上限（张，单人调试模式不限）。 */
    public static final int SKILL_MAX_CARDS = 9;
    /** 技能栏容量（隐藏快捷栏格数，与快捷栏一致）。 */
    public static final int SKILL_BAR_SLOTS = 9;
    /** 同阵营伤害上限（点）。 */
    public static final float FRIENDLY_FIRE_CAP = 2.0F;
    /** 昼夜比（日:夜 = 7:3）：夜间时钟速率 = 7/3。 */
    public static final float NIGHT_CLOCK_RATE = 7.0F / 3.0F;
    /** 夜间判定：时钟时间 ≥ 该值视为夜晚（原版 NIGHT 标记）。 */
    public static final long NIGHT_START_TICK = 13000L;

    // ==================== 赏金猎人模式 ====================
    /** 地图同时存在的检查点数：逃生者 ≤3 / 4–5 / 6–7 / ≥8 → 2/3/4/5。 */
    public static final int[] BOUNTY_CP_TABLE = {2, 3, 4, 5};
    /** 检查点环带：距出生点 400–600 格，互相间隔至少该值。 */
    public static final int BOUNTY_CP_SPACING = 400;
    /** 激活末地要塞检查点所需总里程档 = 逃生者数 × 该值。 */
    public static final int BOUNTY_STRONGHOLD_PER_RUNNER = 4000;
    /** 全体技能抽奖档位 = 要塞档 ÷ 该值（进末地前最多 6 次）。 */
    public static final int BOUNTY_SKILL_DIVISOR = 6;
    /** 逃生者初始命数。 */
    public static final int BOUNTY_RUNNER_LIVES = 3;
    /** 死亡失去当前里程的比例。 */
    public static final double BOUNTY_MILEAGE_DEATH_FRACTION = 1.0 / 3.0;
    /** 复活位置：距死亡位置 300–500 格随机方向地表。 */
    public static final int BOUNTY_RESPAWN_MIN = 300;
    public static final int BOUNTY_RESPAWN_MAX = 500;
    /** 死亡后罗盘赦免时长（刻，60 秒）。 */
    public static final int BOUNTY_COMPASS_IMMUNITY_TICKS = 1200;
    /** 猎人对逃生者每造成 1 点伤害获得的赏金。 */
    public static final int BOUNTY_DAMAGE_FACTOR = 3;
    /** 击杀赏金 = 死者里程 ÷ 该值。 */
    public static final int BOUNTY_KILL_DIVISOR = 3;
    /** 赏金量表档位（按赏金猎人数分三套，末档后每档 +BOUNTY_TIER_STEP_AFTER）：
     *  前期靠伤害积攒档位小、后期击杀赏金大档距放宽，猎人越多单档越大。 */
    public static final int[] BOUNTY_TIERS_1H = {100, 200, 400, 900, 1400, 2000, 2700, 3600, 4600};
    public static final int[] BOUNTY_TIERS_2H = {150, 300, 600, 1100, 1600, 2300, 3100, 4000, 5000};
    public static final int[] BOUNTY_TIERS_3P = {200, 400, 800, 1300, 1900, 2700, 3600, 4600};
    /** 赏金量表末档之后每档固定增加。 */
    public static final int BOUNTY_TIER_STEP_AFTER = 1000;
    /** 赏金模式猎人里程积攒效率（相对逃生者）。 */
    public static final double BOUNTY_HUNTER_MILEAGE_FACTOR = 0.7;
    /** 赏金模式猎人旁观时长：主世界被击杀 30 秒 / 末地 60 秒。 */
    public static final int BOUNTY_HUNTER_SPECTATE_TICKS = 600;
    public static final int BOUNTY_HUNTER_END_SPECTATE_TICKS = 1200;
    /** 赏金模式猎人复活：任意队友附近该半径内（格）。 */
    public static final int BOUNTY_TEAM_RESPAWN_RADIUS = 200;
    /** 赏金模式动态性能（猎人:逃生者比值 ≤1:3 常规 / ≥1:4 猎人末地强化档）。 */
    public static final double BOUNTY_HUNTER_MAX_HEALTH = 60.0;
    public static final double BOUNTY_HUNTER_END_MAX_HEALTH = 60.0;
    public static final double BOUNTY_HUNTER_END_MAX_HEALTH_LARGE = 80.0;
    public static final int BOUNTY_HUNTER_RESISTANCE = 1;
    public static final int BOUNTY_HUNTER_SPEED = 2;
    public static final int BOUNTY_HUNTER_END_SPEED = 2;
    public static final int BOUNTY_HUNTER_END_SPEED_LARGE = 3;
    public static final int BOUNTY_HUNTER_HASTE = 2;
    public static final int BOUNTY_HUNTER_END_SATURATION = 0;
    public static final int BOUNTY_HUNTER_END_JUMP = 1;
    public static final double BOUNTY_RUNNER_MAX_HEALTH = 40.0;
    public static final double BOUNTY_RUNNER_END_MAX_HEALTH = 60.0;
    public static final int BOUNTY_RUNNER_HASTE = 2;
    public static final int BOUNTY_RUNNER_END_SPEED = 1;
    public static final int BOUNTY_RUNNER_END_JUMP = 1;
    public static final int BOUNTY_RUNNER_END_RESISTANCE = 1;
    /** 末地内击杀逃生者的回复比例（不给赏金）。 */
    public static final double BOUNTY_END_HEAL = 0.20;
    /** 头号赏金标记数量：逃生者 ≤3 / 4–7 / ≥8 → 1 / 2 / 3。 */
    public static final int[] BOUNTY_MARK_TABLE = {1, 2, 3};
    /** 头号赏金击杀倍率（按标记名次 1/2/3；首名倍率随人数变化见 BOUNTY_MULT_FIRST）。 */
    public static final double BOUNTY_MULT_RANK2 = 1.25;
    public static final double BOUNTY_MULT_RANK1_SMALL = 1.5; // 逃生者 ≤7
    public static final double BOUNTY_MULT_RANK1_LARGE = 2.0; // 逃生者 ≥8
    public static final double BOUNTY_MULT_RANK3_SMALL = 1.8; // 逃生者 6–7
    public static final double BOUNTY_MULT_RANK3_LARGE = 2.0; // 逃生者 ≥8
    /** 超级疾跑：饥饿消耗间隔（刻，每秒 1 点）。 */
    public static final int SPRINT_HUNGER_INTERVAL_TICKS = 20;
    /** 技能三选一的 UI accent（紫色）。 */
    public static final int SKILL_CHOICE_ACCENT = 0xFFB266FF;

    // ==================== 技能卡 ====================
    /** 抽卡权重：普通/稀有/黑卡/彩卡（黑卡与稀有同权；合计任意，按比例分配）。 */
    public static final int CARD_WEIGHT_COMMON = 54;
    public static final int CARD_WEIGHT_RARE = 30;
    public static final int CARD_WEIGHT_BLACK = 30;
    public static final int CARD_WEIGHT_RAINBOW = 4;
    /** 技能切换最短间隔（刻）：仅防同一点击重复计数，不影响连点手感。 */
    public static final int SKILL_SWITCH_COOLDOWN_TICKS = 3;

    // ==================== 检查点 ====================
    /** 相邻检查点距离范围（格），含 1 号距世界出生点。 */
    public static final int CP_MIN_DIST = 400;
    public static final int CP_MAX_DIST = 600;
    /** 逃生者进入该半径（格）自动激活检查点。 */
    public static final double CHECKPOINT_ACTIVATE_RADIUS = 6.0;
    /** 末地要塞搜索半径（区块）。 */
    public static final int STRONGHOLD_SEARCH_RADIUS_CHUNKS = 100;
    /** 检查点粒子圈刷新间隔（刻）。 */
    public static final int CHECKPOINT_RING_INTERVAL_TICKS = 10;
    /** 检查点激活后绿色粒子圈的保留时长（秒）。 */
    public static final int CHECKPOINT_GREEN_SECONDS = 10;
    /** 表面选址时认为过低的 Y（低于海平面视为水面/湖泊）。 */
    public static final int MIN_SURFACE_Y = 63;

    // ==================== 击杀与复活 ====================
    /** 猎人死亡时，死亡点该半径内的存活逃生者（含击杀者）恢复自身最大生命的比例。 */
    public static final double KILLER_HEAL_FRACTION = 0.15;
    /** 猎人死亡时的逃生者治疗半径（格）。 */
    public static final double HEAL_RADIUS = 20.0;
    /** 复活点须距最近逃生者大于该距离（格）。 */
    public static final double HUNTER_RESPAWN_MIN_DIST = 300.0;
    /** 复活位置相对参照猎人的随机偏移范围（格，约 20 格）。 */
    public static final int RESPAWN_OFFSET_MIN = 12;
    public static final int RESPAWN_OFFSET_MAX = 24;
    /** 无合格参照猎人时，复活在距最近逃生者约该距离的随机地表（格）。 */
    public static final double HUNTER_RESPAWN_FALLBACK_DIST = 500.0;

    // ==================== 周期任务（刻，20 刻 = 1 秒）====================
    public static final int BUFF_REFRESH_INTERVAL_TICKS = 40;
    public static final int COMPASS_UPDATE_INTERVAL_TICKS = 20;
    public static final int BOSSBAR_UPDATE_INTERVAL_TICKS = 20;
    public static final int ACTIVATION_CHECK_INTERVAL_TICKS = 10;
    /** 里程量表（经验条）同步间隔。 */
    public static final int METER_SYNC_INTERVAL_TICKS = 20;
    /** 人数档位重算间隔。 */
    public static final int TIER_RECALC_INTERVAL_TICKS = 40;
    /** 抽奖 UI 距屏幕顶部偏移（避让 bossbar/检查点距离显示）。 */
    public static final int ROLL_UI_TOP_OFFSET = 40;
}
