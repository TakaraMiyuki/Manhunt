package com.example.manhunt.client;

/**
 * 客户端本地角色状态：由 ManhuntRolePayload 每秒刷新，驱动技能槽边框等本地 UI。
 */
public final class ManhuntClientState {
    private ManhuntClientState() {}

    private static volatile boolean participant;
    private static volatile boolean runner;

    public static void update(boolean isParticipant, boolean isRunner) {
        participant = isParticipant;
        runner = isRunner;
    }

    public static boolean isParticipant() {
        return participant;
    }

    public static boolean isRunner() {
        return runner;
    }

    public static void clear() {
        participant = false;
        runner = false;
    }
}
