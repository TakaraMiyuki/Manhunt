package com.example.manhunt;

/**
 * 全部可调数值。平衡性调整只改这一个文件。
 */
public final class GameConfig {
    private GameConfig() {}

    /** 开局给予逃生者的逃跑时间（秒）。 */
    public static final int ESCAPE_SECONDS = 60;

    // ---- 阵营属性 ----
    /** 猎人最大生命值。 */
    public static final double HUNTER_MAX_HEALTH = 40.0;
    /** 逃生者最大生命值。 */
    public static final double RUNNER_MAX_HEALTH = 60.0;

    // ---- 击杀奖励 ----
    /** 逃生者击杀猎人后，全体逃生者获得的生命恢复等级（1 = 生命恢复 II）。 */
    public static final int KILL_REGEN_AMPLIFIER = 2;
    /** 生命恢复时长（秒）。 */
    public static final int KILL_REGEN_SECONDS = 5;

    // ---- 猎人复活 ----
    /** 复活点须距最近逃生者大于该距离（格）。 */
    public static final double HUNTER_RESPAWN_MIN_DIST = 300.0;
    /** 复活位置相对参照猎人的随机偏移范围（格）。 */
    public static final int RESPAWN_OFFSET_MIN = 20;
    public static final int RESPAWN_OFFSET_MAX = 40;
    /** 猎人在末地被击杀时，复活在 4 号检查点附近该半径内（格）。 */
    public static final int END_RESPAWN_RADIUS = 100;
    /** 玩家点击重生后等待传送的最大重试（刻）。 */
    public static final int RESPAWN_TELEPORT_MAX_WAIT = 600;

    // ---- 检查点 ----
    public static final int CHECKPOINT_COUNT = 4;
    /** 1 号检查点距世界出生点距离范围（格）。 */
    public static final int CP1_MIN_DIST = 1000;
    public static final int CP1_MAX_DIST = 1500;
    /** 相邻检查点距离范围（格）。 */
    public static final int CP_STEP_MIN_DIST = 500;
    public static final int CP_STEP_MAX_DIST = 800;
    /** 逃生者进入该半径（格）自动激活检查点。 */
    public static final double CHECKPOINT_ACTIVATE_RADIUS = 6.0;
    /** 末地要塞搜索半径（区块）。 */
    public static final int STRONGHOLD_SEARCH_RADIUS_CHUNKS = 100;
    /** 表面选址时认为过低的 Y（低于海平面视为水面/湖泊）。 */
    public static final int MIN_SURFACE_Y = 63;
    /** 每个检查点随机物资条目数。 */
    public static final int LOOT_ROLLS_PER_CHECKPOINT = 3;

    // ---- 周期任务（刻，20 刻 = 1 秒）----
    public static final int BUFF_REFRESH_INTERVAL_TICKS = 40;
    public static final int COMPASS_UPDATE_INTERVAL_TICKS = 20;
    public static final int BOSSBAR_UPDATE_INTERVAL_TICKS = 20;
    public static final int ACTIVATION_CHECK_INTERVAL_TICKS = 10;
}
