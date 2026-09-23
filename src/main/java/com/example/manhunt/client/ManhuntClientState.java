package com.example.manhunt.client;

/**
 * 客户端本地角色状态：由 ManhuntRolePayload 每秒刷新，驱动技能槽边框等本地 UI。
 */
public final class ManhuntClientState {
    private ManhuntClientState() {}

    private static volatile boolean participant;
    private static volatile boolean runner;
    private static volatile boolean skillReady;
    private static volatile int morale;
    private static volatile int moraleRewards;

    public static void update(boolean isParticipant, boolean isRunner, boolean hasReadySkill,
                              int moraleValue, int moraleRewardCount) {
        participant = isParticipant;
        runner = isRunner;
        skillReady = hasReadySkill;
        morale = moraleValue;
        moraleRewards = moraleRewardCount;
    }

    public static boolean isParticipant() {
        return participant;
    }

    public static boolean isRunner() {
        return runner;
    }

    /** 技能库中是否存在任一冷却完毕的卡。 */
    public static boolean isSkillReady() {
        return skillReady;
    }

    /** 当前全队士气（猎人）。 */
    public static int morale() {
        return morale;
    }

    /** 已达成的士气档数（猎人）。 */
    public static int moraleRewards() {
        return moraleRewards;
    }

    public static void clear() {
        participant = false;
        runner = false;
        skillReady = false;
        morale = 0;
        moraleRewards = 0;
    }
}
