package com.example.manhunt.game;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.example.manhunt.GameConfig;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * 超级疾跑（赏金模式，逃生者固定能力）：左 Alt 切换开关。
 * 开启期间获得速度 II；每 0.5 秒消耗 1 点饥饿，饥饿归零自动关闭。
 */
public final class SprintManager {
    private SprintManager() {}

    private static final Set<UUID> SPRINTING = new HashSet<>();

    public static boolean isSprinting(UUID id) {
        return SPRINTING.contains(id);
    }

    /** 设置开关（仅赏金模式的存活逃生者；状态不变则为空操作）。 */
    public static void set(ServerPlayer player, boolean on) {
        if (!ManhuntGame.isBounty() || !ManhuntGame.isRunning()
                || !TeamUtil.isRunner(player) || ManhuntGame.isEliminated(player.getUUID())) {
            return;
        }
        UUID id = player.getUUID();
        if (on && SPRINTING.add(id)) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED,
                GameConfig.BUFF_REFRESH_INTERVAL_TICKS * 3, 1, true, false), null);
            player.sendSystemMessage(Component.literal("§6[赏金猎人] §b超级疾跑开启！§7饥饿将快速消耗。"), true);
        } else if (!on && SPRINTING.remove(id)) {
            player.sendSystemMessage(Component.literal("§6[赏金猎人] §7超级疾跑已关闭。"), true);
        }
    }

    /** 每刻：饥饿消耗与速度维持；饥饿耗尽自动关闭。 */
    public static void tick(MinecraftServer server) {
        if (SPRINTING.isEmpty()) {
            return;
        }
        for (UUID id : new HashSet<>(SPRINTING)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || !ManhuntGame.isRunning() || !TeamUtil.isRunner(p)
                    || ManhuntGame.isEliminated(id)) {
                SPRINTING.remove(id);
                continue;
            }
            if (p.getFoodData().getFoodLevel() <= 0) {
                SPRINTING.remove(id);
                p.sendSystemMessage(Component.literal("§6[赏金猎人] §c饥饿不足，超级疾跑已关闭。"), true);
                continue;
            }
            if (p.tickCount % GameConfig.SPRINT_HUNGER_INTERVAL_TICKS == 0) {
                var food = p.getFoodData();
                food.setFoodLevel(Math.max(0, food.getFoodLevel() - 1));
            }
        }
    }

    /** 速度 II 维持（由 TeamUtil.refreshBuffs 在赏金疾跑分支调用）。 */
    public static void applySpeed(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.SPEED,
            GameConfig.BUFF_REFRESH_INTERVAL_TICKS * 3, 1, true, false), null);
    }

    public static void reset() {
        SPRINTING.clear();
    }
}
