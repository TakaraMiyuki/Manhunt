package com.example.manhunt.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.manhunt.item.CompassManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/**
 * 死亡结算、伤害规则（逃跑期保护 / 龙与末影人免伤 / 士气积累）、
 * 死亡不掉装备（猎人背包保留到复活）与 XP 接管。
 */
public final class DeathHandler {
    private DeathHandler() {}

    /** 猎人死亡后暂存的背包（uuid → 物品），复活时归还。 */
    private static final Map<UUID, List<ItemStack>> SAVED_HUNTER_INVENTORY = new HashMap<>();

    // ==================== 死亡结算 ====================

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
                ManhuntGame.onHunterKilledByRunner(server, killer);
            }
            ManhuntGame.scheduleHunterRespawn(server, dead);
        } else if (TeamUtil.isRunner(dead)) {
            ManhuntGame.onRunnerDeath(server, dead);
        }
    }

    private static ServerPlayer killer(LivingDeathEvent event) {
        return event.getSource().getEntity() instanceof ServerPlayer p ? p : null;
    }

    // ==================== 掉落与装备保留 ====================

    /**
     * 参与者死亡不产生掉落物：
     * 猎人背包暂存，复活时原样归还；逃生者（淘汰）物品随之消失。
     */
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer dead) || !ManhuntGame.isParticipant(dead.getUUID())) {
            return;
        }
        if (TeamUtil.isHunter(dead)) {
            List<ItemStack> saved = new ArrayList<>();
            for (ItemEntity drop : event.getDrops()) {
                if (!drop.getItem().isEmpty()) {
                    saved.add(drop.getItem());
                }
            }
            SAVED_HUNTER_INVENTORY.put(dead.getUUID(), saved);
        }
        event.getDrops().clear();
        event.setCanceled(true);
    }

    /** 复活后归还猎人背包。 */
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ManhuntGame.isRunning() || !ManhuntGame.isParticipant(player.getUUID())) {
            return;
        }
        TeamUtil.applyBaseAttributes(player);
        if (TeamUtil.isRunner(player) && ManhuntGame.isEliminated(player.getUUID())) {
            player.setGameMode(GameType.SPECTATOR);
        } else {
            TeamUtil.refreshBuffs(player);
            List<ItemStack> saved = SAVED_HUNTER_INVENTORY.remove(player.getUUID());
            if (saved != null) {
                for (ItemStack stack : saved) {
                    if (!com.example.manhunt.util.InvUtil.safeAdd(player, stack)) {
                        player.drop(stack, false);
                    }
                }
                player.sendSystemMessage(Component.literal("§7[猎人游戏] 装备已随复活归还。"));
            }
            CompassManager.ensureCompasses(player);
        }
    }

    // ==================== 伤害规则 ====================

    /** 逃跑倒计时期间猎人无法伤害逃生者；末影龙与末影人始终无法伤害猎人；
     *  参与者不会摔死——摔落伤害最多扣到保留 1 颗心。 */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (ManhuntGame.isRunning() && ManhuntGame.isParticipant(victim.getUUID())
                && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
            float keep = com.example.manhunt.GameConfig.FALL_MIN_HEALTH;
            float health = victim.getHealth();
            if (event.getAmount() >= health - keep) {
                event.setAmount(Math.max(0.0F, health - keep));
            }
        }
        if (TeamUtil.isHunter(victim) && ManhuntGame.isRunning() && isEndMobDamage(event.getSource())) {
            event.setCanceled(true);
            return;
        }
        if (ManhuntGame.phase() != ManhuntGame.Phase.ESCAPE) {
            return;
        }
        if (TeamUtil.isRunner(victim)
                && event.getSource().getEntity() instanceof ServerPlayer attacker && TeamUtil.isHunter(attacker)) {
            event.setCanceled(true);
        }
    }

    /** 龙拍击/冲撞/龙息/龙火球与末影人的攻击伤害。 */
    private static boolean isEndMobDamage(DamageSource source) {
        if (source.getEntity() instanceof EnderDragon || source.getEntity() instanceof EnderMan) {
            return true;
        }
        if (source.is(DamageTypes.DRAGON_BREATH)) {
            return true;
        }
        return source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
    }

    /** 猎人对逃生者造成的实际伤害转化为全队士气。 */
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (!ManhuntGame.isRunning()
                || !(event.getEntity() instanceof ServerPlayer victim)
                || !TeamUtil.isRunner(victim)) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && TeamUtil.isHunter(attacker)) {
            int amount = Math.round(event.getInflictedDamage());
            if (amount > 0) {
                MoraleManager.addMorale(victim.level().getServer(), amount);
            }
        }
    }

    /** 末影人不再以猎人为目标。 */
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof EnderMan)
                || !(event.getNewAboutToBeSetTarget() instanceof ServerPlayer target)) {
            return;
        }
        if (TeamUtil.isHunter(target) && ManhuntGame.isRunning()) {
            event.setNewAboutToBeSetTarget(null);
            event.setCanceled(true);
        }
    }

    // ==================== XP 接管 ====================

    /** 参与者经验条被里程/士气量表占用：禁止一切 XP 获取与掉落。 */
    public static void onPickupXp(PlayerXpEvent.PickupXp event) {
        if (ManhuntGame.isRunning() && ManhuntGame.isParticipant(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (ManhuntGame.isRunning() && ManhuntGame.isParticipant(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    // ==================== 登录恢复 ====================

    /** 掉线重连：恢复属性、补发罗盘、淘汰者转旁观、归还暂存背包。 */
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
                player.setGameMode(GameType.SPECTATOR);
            } else {
                CompassManager.giveCheckpointCompass(player);
            }
        } else if (ManhuntGame.phase() == ManhuntGame.Phase.RUNNING) {
            CompassManager.giveTrackingCompass(player);
            List<ItemStack> saved = SAVED_HUNTER_INVENTORY.remove(player.getUUID());
            if (saved != null) {
                for (ItemStack stack : saved) {
                    if (!com.example.manhunt.util.InvUtil.safeAdd(player, stack)) {
                        player.drop(stack, false);
                    }
                }
            }
        }
        TeamUtil.refreshBuffs(player);
        MileageManager.syncMeter(player);
        com.example.manhunt.compat.CraftingOnAStickBridge.giveStick(player);
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
            player.sendSystemMessage(Component.literal(
                "§6[猎人游戏] §d末地强化已生效（档位 " + TierSystem.displayTier() + "）"));
        }
    }

    public static void onLoggedOut(UUID id) {
        SAVED_HUNTER_INVENTORY.remove(id);
    }
}
