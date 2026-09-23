package com.example.manhunt.client;

/**
 * 客户端本地角色状态：由 ManhuntRolePayload 每秒刷新，驱动技能槽边框等本地 UI。
 */
public final class ManhuntClientState {
    private ManhuntClientState() {}

    private static volatile boolean participant;
    private static volatile boolean runner;
    private static volatile boolean skillReady;

    public static void update(boolean isParticipant, boolean isRunner, boolean hasReadySkill) {
        participant = isParticipant;
        runner = isRunner;
        skillReady = hasReadySkill;
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

    public static void clear() {
        participant = false;
        runner = false;
        skillReady = false;
    }
}
