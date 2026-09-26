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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
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
 * 死亡结算、伤害规则（逃跑期保护 / 龙与末影人免伤 / 同阵营伤害上限 / 士气积累）、
 * 死亡不掉装备（猎人装备按原槽位暂存，复活时回穿）与 XP 接管。
 */
public final class DeathHandler {
    private DeathHandler() {}

    /** 猎人装备暂存：按原槽位记录（slot="main" 为背包下标 0-35，其余为 EquipmentSlot 名称）。 */
    private record SavedSlot(String slot, int index, ItemStack stack) {}

    private static final Map<UUID, List<SavedSlot>> SAVED_HUNTER_GEAR = new HashMap<>();

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
            snapshotHunterGear(dead);
            ServerPlayer killer = killer(event);
            // 死亡点 20 格内的所有存活逃生者各恢复 15%；击杀者无论距离必定恢复
            ManhuntGame.healRunnersNear(server, killer, dead.blockPosition());
            ManhuntGame.scheduleHunterRespawn(server, dead);
            if (killer != null && TeamUtil.isRunner(killer)) {
                ManhuntGame.onHunterKilledByRunner(server, killer, dead);
            } else {
                ManhuntGame.broadcast(server, "§6[猎人游戏] §c猎人 §f" + dead.getName().getString()
                    + " §c死亡，" + com.example.manhunt.GameConfig.HUNTER_SPECTATE_TICKS / 20 + " 秒后于复活点复活。");
            }
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
     * 猎人装备已在死亡事件中按槽位暂存（{@link #snapshotHunterGear}），复活时回穿；
     * 逃生者（淘汰）物品随之消失。
     */
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer dead) || !ManhuntGame.isParticipant(dead.getUUID())) {
            return;
        }
        event.getDrops().clear();
        event.setCanceled(true);
    }

    /** 死亡瞬间按原槽位快照猎人装备（此时物品栏尚未被原版清空）。 */
    private static void snapshotHunterGear(ServerPlayer dead) {
        List<SavedSlot> gear = new ArrayList<>();
        var items = dead.getInventory().getNonEquipmentItems();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                gear.add(new SavedSlot("main", i, items.get(i).copy()));
            }
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = dead.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                gear.add(new SavedSlot(slot.getName(), -1, stack.copy()));
            }
        }
        SAVED_HUNTER_GEAR.put(dead.getUUID(), gear);
    }

    /** 归还暂存猎人装备：盔甲/副手/主手回穿原槽位，其余按原下标回背包。返回是否归还了物品。 */
    public static boolean restoreHunterGear(ServerPlayer p) {
        List<SavedSlot> gear = SAVED_HUNTER_GEAR.remove(p.getUUID());
        if (gear == null) {
            return false;
        }
        for (SavedSlot s : gear) {
            if (s.slot().equals("main")) {
                p.getInventory().getNonEquipmentItems().set(s.index(), s.stack());
            } else {
                p.setItemSlot(EquipmentSlot.byName(s.slot()), s.stack());
            }
        }
        p.sendSystemMessage(Component.literal("§7[猎人游戏] 装备已回穿原槽位。"), true);
        return true;
    }

    public static void clearHunterGear(UUID id) {
        SAVED_HUNTER_GEAR.remove(id);
    }

    public static void clearAllHunterGear() {
        SAVED_HUNTER_GEAR.clear();
    }

    /** 复活后：淘汰者转正常旁观者；猎人自动重生后由重生调度接手（旁观 → 传送 → 装备回穿）。 */
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
            CompassManager.ensureCompasses(player);
            if (TeamUtil.isHunter(player) && ManhuntGame.isHunterRespawnPending(player.getUUID())) {
                player.setGameMode(GameType.SPECTATOR); // 10 秒旁观等待
            }
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
        // 同阵营伤害上限（击退保持原样，不随削减变化）
        if (ManhuntGame.isRunning()
                && ManhuntGame.isParticipant(victim.getUUID())
                && event.getSource().getEntity() instanceof ServerPlayer attacker
                && ManhuntGame.isParticipant(attacker.getUUID())
                && ManhuntGame.isHunter(attacker.getUUID()) == ManhuntGame.isHunter(victim.getUUID())) {
            if (event.getAmount() > com.example.manhunt.GameConfig.FRIENDLY_FIRE_CAP) {
                event.setAmount(com.example.manhunt.GameConfig.FRIENDLY_FIRE_CAP);
            }
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
            restoreHunterGear(player); // 暂存装备按原槽位归还（含死亡后掉线重连）
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
        // 暂存装备保留至重连归还（不再因掉线丢失）
    }
}
