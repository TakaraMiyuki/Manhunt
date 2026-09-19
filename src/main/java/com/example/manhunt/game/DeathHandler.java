package com.example.manhunt.game;

import com.example.manhunt.item.CompassManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 死亡结算、伤害拦截、重生/登录处理。
 */
public final class DeathHandler {
    private DeathHandler() {}

    /** 死亡结算：猎人击杀奖励与复活、逃生者淘汰、末影龙死亡判定。 */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer dead)) {
            // 末影龙死亡单独判定（不限于玩家击杀）
            if (event.getEntity().getType() == EntityTypes.ENDER_DRAGON
                    && event.getEntity().level().dimension() == Level.END
                    && ManhuntGame.isRunning()) {
                ManhuntGame.onDragonKilled(event.getEntity().level().getServer());
            }
            return;
        }
        MinecraftServer server = dead.level().getServer();
        if (server == null || !ManhuntGame.isRunning()) {
            return;
        }

        if (TeamUtil.isHunter(dead)) {
            ServerPlayer killer = killer(event);
            if (killer != null && TeamUtil.isRunner(killer)) {
                ManhuntGame.onHunterKilledByRunner(server);
            }
            ManhuntGame.scheduleHunterRespawn(server, dead);
        } else if (TeamUtil.isRunner(dead)) {
            ManhuntGame.onRunnerDeath(server, dead);
        }
    }

    private static ServerPlayer killer(LivingDeathEvent event) {
        return event.getSource().getEntity() instanceof ServerPlayer p ? p : null;
    }

    /** 逃跑倒计时期间猎人无法伤害逃生者。 */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (ManhuntGame.phase() != ManhuntGame.Phase.ESCAPE) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer victim) || !TeamUtil.isRunner(victim)) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && TeamUtil.isHunter(attacker)) {
            event.setCanceled(true);
        }
    }

    /** 重生后：淘汰逃生者转旁观；重刷属性与增益（死亡会重置属性）。 */
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ManhuntGame.isRunning() || !ManhuntGame.isParticipant(player.getUUID())) {
            return;
        }
        TeamUtil.applyBaseAttributes(player);
        if (TeamUtil.isRunner(player) && ManhuntGame.isEliminated(player.getUUID())) {
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        } else {
            TeamUtil.refreshBuffs(player);
        }
    }

    /** 掉线重连：恢复属性、补发罗盘、淘汰者转旁观。 */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ManhuntGame.isRunning() || !ManhuntGame.isParticipant(player.getUUID())) {
            return;
        }
        TeamUtil.applyBaseAttributes(player);
        if (TeamUtil.isRunner(player)) {
            if (ManhuntGame.isEliminated(player.getUUID())) {
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            } else {
                CompassManager.giveCheckpointCompass(player);
            }
        } else if (ManhuntGame.phase() == ManhuntGame.Phase.RUNNING) {
            CompassManager.giveTrackingCompass(player);
        }
        TeamUtil.refreshBuffs(player);
    }

    /** 逃生者切换维度时切换增益组（进末地强化）。 */
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ManhuntGame.isRunning() || !TeamUtil.isRunner(player)) {
            return;
        }
        TeamUtil.refreshBuffs(player);
        if (event.getTo() == Level.END) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§6[猎人游戏] §d末地强化已生效：抗性提升 2、速度 1、跳跃提升 2、饱和 1、急迫 2"));
        }
    }
}
