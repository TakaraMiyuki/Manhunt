package com.example.manhunt.cards;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.manhunt.GameConfig;
import com.example.manhunt.ManhuntMod;
import com.example.manhunt.game.ManhuntGame;
import com.example.manhunt.game.TeamUtil;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 技能栏系统：快捷栏最右一格为固定技能槽（显示当前激活的技能卡）。
 * 逃生者可持有无限张技能卡——全部存入"技能库"，槽位显示激活的那张：
 * 右键使用技能（原版 SkillCardItem 逻辑），左键切换到下一张。
 */
public final class SkillSlotManager {
    private SkillSlotManager() {}

    /** 逃生者技能库（含当前激活的卡）。 */
    private static final Map<UUID, List<ItemStack>> COLLECTION = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_INDEX = new HashMap<>();
    private static final Map<UUID, Integer> LAST_SWITCH = new HashMap<>();

    // ==================== 抽卡入库 ====================

    /** 发放抽到的技能卡：加入技能库，若槽位空闲则立即装备。 */
    public static void giveDrawnCard(ServerPlayer runner, SkillCardsBridge.CardDraw draw) {
        if (!SkillCardsBridge.available()) {
            return;
        }
        UUID id = runner.getUUID();
        List<ItemStack> cards = COLLECTION.computeIfAbsent(id, k -> new ArrayList<>());
        cards.add(draw.stack().copy());
        Integer idx = ACTIVE_INDEX.get(id);
        if (idx == null || idx < 0 || idx >= cards.size()) {
            ACTIVE_INDEX.put(id, cards.size() - 1);
        }
        equipActive(runner);
        runner.sendSystemMessage(Component.literal(
            "§6[技能库] §f" + draw.stack().getHoverName().getString()
                + " §7已加入（共 " + cards.size() + " 张，左键切换）"));
    }

    /** 把激活的技能卡镜像到固定槽位。 */
    private static void equipActive(ServerPlayer player) {
        List<ItemStack> cards = COLLECTION.get(player.getUUID());
        Integer idx = ACTIVE_INDEX.get(player.getUUID());
        NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
        if (cards == null || cards.isEmpty() || idx == null || idx < 0 || idx >= cards.size()) {
            items.set(GameConfig.CARD_SLOT, ItemStack.EMPTY);
            return;
        }
        items.set(GameConfig.CARD_SLOT, cards.get(idx).copy());
    }

    // ==================== 切换 ====================

    /** 左键切换到下一张技能卡（带节流）。 */
    public static void switchSkill(ServerPlayer player) {
        if (!SkillCardsBridge.available() || !ManhuntGame.isRunning() || !TeamUtil.isRunner(player)
                || ManhuntGame.isEliminated(player.getUUID())) {
            return;
        }
        List<ItemStack> cards = COLLECTION.get(player.getUUID());
        if (cards == null || cards.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7技能库为空。"), true);
            return;
        }
        int now = player.tickCount;
        int last = LAST_SWITCH.getOrDefault(player.getUUID(), -GameConfig.SKILL_SWITCH_COOLDOWN_TICKS);
        if (now - last < GameConfig.SKILL_SWITCH_COOLDOWN_TICKS) {
            return;
        }
        LAST_SWITCH.put(player.getUUID(), now);
        int idx = (ACTIVE_INDEX.getOrDefault(player.getUUID(), 0) + 1) % cards.size();
        ACTIVE_INDEX.put(player.getUUID(), idx);
        equipActive(player);
        player.sendSystemMessage(Component.literal(
            "§6[技能] §f" + cards.get(idx).getHoverName().getString()
                + " §7(" + (idx + 1) + "/" + cards.size() + ")"), true);
    }

    // ==================== 左键拦截 ====================

    /** 持技能卡左键方块：取消破坏并切换技能（双侧取消，切换仅在服务端）。 */
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!SkillCardsBridge.available() || !ManhuntGame.isRunning()) {
            return;
        }
        var player = event.getEntity();
        if (!ManhuntGame.isRunner(player.getUUID()) || ManhuntGame.isEliminated(player.getUUID())) {
            return;
        }
        if (SkillCardsBridge.isSkillCard(player.getMainHandItem())) {
            event.setCanceled(true);
            if (player instanceof ServerPlayer serverPlayer) {
                switchSkill(serverPlayer);
            }
        }
    }

    /** 持技能卡左键实体：取消攻击并切换技能（双侧取消，切换仅在服务端）。 */
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!SkillCardsBridge.available() || !ManhuntGame.isRunning()) {
            return;
        }
        var player = event.getEntity();
        if (!ManhuntGame.isRunner(player.getUUID()) || ManhuntGame.isEliminated(player.getUUID())) {
            return;
        }
        if (SkillCardsBridge.isSkillCard(player.getMainHandItem())) {
            event.setCanceled(true);
            if (player instanceof ServerPlayer serverPlayer) {
                switchSkill(serverPlayer);
            }
        }
    }

    /** 持技能卡左键挥空（仅客户端触发）：通知服务端切换技能。 */
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (!SkillCardsBridge.available() || !ManhuntGame.isRunning()) {
            return;
        }
        var player = event.getEntity();
        if (!ManhuntGame.isRunner(player.getUUID()) || ManhuntGame.isEliminated(player.getUUID())) {
            return;
        }
        if (SkillCardsBridge.isSkillCard(player.getMainHandItem())) {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new com.example.manhunt.net.SkillSwitchPayload());
        }
    }

    // ==================== 守护 ====================

    /** 每秒守护：固定槽位必须显示激活卡；技能卡不得出现在其他栏位（防复制）。 */
    public static void tickGuard(MinecraftServer server) {
        if (!SkillCardsBridge.available()) {
            return;
        }
        for (ServerPlayer p : ManhuntGame.onlineAliveRunners(server)) {
            NonNullList<ItemStack> items = p.getInventory().getNonEquipmentItems();
            for (int i = 0; i < items.size(); i++) {
                if (i != GameConfig.CARD_SLOT && SkillCardsBridge.isSkillCard(items.get(i))) {
                    items.set(i, ItemStack.EMPTY); // 镜像副本，清除防复制
                }
            }
            // 技能槽被放入其他物品：经安全入包退回（跳过技能槽，防止再落回原位）
            ItemStack inSlot = items.get(GameConfig.CARD_SLOT);
            if (!inSlot.isEmpty() && !SkillCardsBridge.isSkillCard(inSlot)) {
                items.set(GameConfig.CARD_SLOT, ItemStack.EMPTY);
                if (!com.example.manhunt.util.InvUtil.safeAdd(p, inSlot)) {
                    p.drop(inSlot, false);
                }
                p.sendSystemMessage(Component.literal("§7[猎人游戏] 技能槽为固定栏位，物品已移回背包其他栏位。"), true);
            }
            equipActive(p);
        }
    }

    // ==================== 状态 ====================

    public static boolean hasCards(UUID id) {
        List<ItemStack> cards = COLLECTION.get(id);
        return cards != null && !cards.isEmpty();
    }

    public static int cardCount(UUID id) {
        List<ItemStack> cards = COLLECTION.get(id);
        return cards == null ? 0 : cards.size();
    }

    public static void onLoggedOut(UUID id) {
        LAST_SWITCH.remove(id);
    }

    public static void reset() {
        COLLECTION.clear();
        ACTIVE_INDEX.clear();
        LAST_SWITCH.clear();
    }

    // ==================== 持久化 ====================

    /** 技能库序列化为物品 id 列表（技能卡无组件，id 足够）。 */
    public static Map<UUID, List<String>> snapshotIds() {
        Map<UUID, List<String>> out = new HashMap<>();
        for (Map.Entry<UUID, List<ItemStack>> e : COLLECTION.entrySet()) {
            List<String> ids = new ArrayList<>();
            for (ItemStack stack : e.getValue()) {
                Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                ids.add(itemId.toString());
            }
            out.put(e.getKey(), ids);
        }
        return out;
    }

    public static void restore(Map<UUID, List<String>> saved) {
        COLLECTION.clear();
        ACTIVE_INDEX.clear();
        if (!SkillCardsBridge.available()) {
            ManhuntMod.LOGGER.warn("[Manhunt] 存档含技能库但未安装技能卡模组，已跳过恢复");
            return;
        }
        for (Map.Entry<UUID, List<String>> e : saved.entrySet()) {
            List<ItemStack> cards = new ArrayList<>();
            for (String idStr : e.getValue()) {
                try {
                    var holder = BuiltInRegistries.ITEM.get(Identifier.parse(idStr));
                    if (holder.isPresent() && SkillCardsBridge.isSkillCard(new ItemStack(holder.get().value()))) {
                        cards.add(new ItemStack(holder.get().value()));
                    }
                } catch (Exception ignored) {
                }
            }
            if (!cards.isEmpty()) {
                COLLECTION.put(e.getKey(), cards);
                ACTIVE_INDEX.put(e.getKey(), 0);
            }
        }
    }
}
