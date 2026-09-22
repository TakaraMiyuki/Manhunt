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

    // ==================== 技能卡 ====================
    /** 抽卡权重：普通/稀有/黑卡/彩卡（合计任意，按比例分配）。 */
    public static final int CARD_WEIGHT_COMMON = 68;
    public static final int CARD_WEIGHT_RARE = 20;
    public static final int CARD_WEIGHT_BLACK = 10;
    public static final int CARD_WEIGHT_RAINBOW = 2;
    /** 技能栏固定于快捷栏最右一格（0-based）。 */
    public static final int CARD_SLOT = 8;
    /** 技能切换最短间隔（刻），防止按住左键连续切换。 */
    public static final int SKILL_SWITCH_COOLDOWN_TICKS = 10;

    // ==================== 士气 ====================
    /** 士气阈值：每达到一档，全体在线猎人各获得一次超级抽奖（1000 封顶）。 */
    public static final int[] MORALE_THRESHOLDS = {50, 100, 200, 300, 500, 700, 1000};
    public static final int MORALE_CAP = 1000;

    // ==================== 检查点 ====================
    /** 相邻检查点距离范围（格），含 1 号距世界出生点。 */
    public static final int CP_MIN_DIST = 400;
    public static final int CP_MAX_DIST = 600;
    /** 逃生者进入该半径（格）自动激活检查点。 */
    public static final double CHECKPOINT_ACTIVATE_RADIUS = 6.0;
    /** 末地要塞搜索半径（区块）。 */
    public static final int STRONGHOLD_SEARCH_RADIUS_CHUNKS = 100;
    /** 表面选址时认为过低的 Y（低于海平面视为水面/湖泊）。 */
    public static final int MIN_SURFACE_Y = 63;

    // ==================== 击杀与复活 ====================
    /** 逃生者击杀猎人后，击杀者恢复自身最大生命的比例。 */
    public static final double KILLER_HEAL_FRACTION = 0.20;
    /** 复活点须距最近逃生者大于该距离（格）。 */
    public static final double HUNTER_RESPAWN_MIN_DIST = 300.0;
    /** 复活位置相对参照猎人的随机偏移范围（格）。 */
    public static final int RESPAWN_OFFSET_MIN = 20;
    public static final int RESPAWN_OFFSET_MAX = 40;
    /** 猎人在末地被击杀时，复活在最近激活检查点（要塞）附近该半径内（格）。 */
    public static final int END_RESPAWN_RADIUS = 100;
    /** 玩家点击重生后等待传送的最大重试（刻）。 */
    public static final int RESPAWN_TELEPORT_MAX_WAIT = 600;

    // ==================== 周期任务（刻，20 刻 = 1 秒）====================
    public static final int BUFF_REFRESH_INTERVAL_TICKS = 40;
    public static final int COMPASS_UPDATE_INTERVAL_TICKS = 20;
    public static final int BOSSBAR_UPDATE_INTERVAL_TICKS = 20;
    public static final int ACTIVATION_CHECK_INTERVAL_TICKS = 10;
    /** 里程量表（经验条）同步间隔。 */
    public static final int METER_SYNC_INTERVAL_TICKS = 20;
    /** 人数档位重算间隔。 */
    public static final int TIER_RECALC_INTERVAL_TICKS = 40;
    /** 技能栏守护间隔（经验条/技能槽镜像）。 */
    public static final int CARD_SLOT_GUARD_INTERVAL_TICKS = 20;
    /** 抽奖 UI 距屏幕顶部偏移（避让 bossbar/检查点距离显示）。 */
    public static final int ROLL_UI_TOP_OFFSET = 40;
}
