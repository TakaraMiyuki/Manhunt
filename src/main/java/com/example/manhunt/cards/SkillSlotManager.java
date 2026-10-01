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

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 技能栏：9 格专属隐藏快捷栏（与快捷栏容量一致），收纳全部主动技能卡。
 * 选中格 = "切出"的技能；客户端按住左 Alt 时热栏临时替换为技能栏，右键释放选中技能。
 * 被动卡不走技能栏——自动放入 Curios 被动饰品栏（{@link com.example.manhunt.compat.SkillPassiveBridge}）。
 * 卡牌以 ItemStack 形式存于服务端容器，不进玩家背包（无掉落/复制问题）。
 */
public final class SkillSlotManager {
    private SkillSlotManager() {}

    /** 技能栏容量（= 快捷栏格数）。 */
    public static final int BAR_SLOTS = GameConfig.SKILL_BAR_SLOTS;

    /** 技能栏（uuid → 9 格）。 */
    private static final Map<UUID, ItemStack[]> BAR = new HashMap<>();
    /** 选中格（"切出"的技能）。 */
    private static final Map<UUID, Integer> SELECTED = new HashMap<>();

    private static ItemStack[] bar(UUID id) {
        return BAR.computeIfAbsent(id, k -> new ItemStack[BAR_SLOTS]);
    }

    // ==================== 发放入栏 ====================

    /** 发放抽到的技能卡：被动卡 → Curios 饰品栏（6 格，满则丢弃）；主动卡 → 技能栏第一个空格。 */
    public static void giveDrawnCard(ServerPlayer runner, SkillCardsBridge.CardDraw draw) {
        if (!SkillCardsBridge.available()) {
            return;
        }
        if (SkillCardsBridge.isPassiveCard(draw.stack())) {
            if (com.example.manhunt.compat.SkillPassiveBridge.equipPassive(runner, draw.stack())) {
                runner.sendSystemMessage(Component.literal(
                    "§6[技能栏] §f" + draw.stack().getHoverName().getString()
                        + " §7（被动）已放入饰品栏并开始生效。"));
            } else {
                runner.sendSystemMessage(Component.literal(
                    "§6[技能栏] §f" + draw.stack().getHoverName().getString()
                        + " §7（被动）已丢弃——被动饰品栏已满（6 张）。"));
            }
            return;
        }
        UUID id = runner.getUUID();
        if (!ManhuntGame.soloMode() && cardCount(id) >= GameConfig.SKILL_MAX_CARDS) {
            runner.sendSystemMessage(Component.literal(
                "§6[技能栏] §f" + draw.stack().getHoverName().getString()
                    + " §7已丢弃——技能栏已满（" + GameConfig.SKILL_MAX_CARDS + " 张）。"));
            return;
        }
        ItemStack[] bar = bar(id);
        for (int i = 0; i < BAR_SLOTS; i++) {
            if (bar[i] == null || bar[i].isEmpty()) {
                bar[i] = draw.stack().copy();
                runner.sendSystemMessage(Component.literal(
                    "§6[技能栏] §f" + draw.stack().getHoverName().getString()
                        + " §7已放入技能栏第 " + (i + 1) + " 格（共 "
                        + cardCount(id) + " 张，按住左 Alt 查看）"));
                return;
            }
        }
        runner.sendSystemMessage(Component.literal(
            "§6[技能栏] §f" + draw.stack().getHoverName().getString() + " §7已丢弃——技能栏已满。"));
    }

    // ==================== 选中与使用 ====================

    /** 选中某格（Alt 技能栏滚轮/数字键切换）。 */
    public static void selectSkill(ServerPlayer player, int index) {
        if (!SkillCardsBridge.available() || !ManhuntGame.isRunning() || !TeamUtil.isRunner(player)
                || ManhuntGame.isEliminated(player.getUUID())) {
            return;
        }
        if (index < 0 || index >= BAR_SLOTS) {
            return;
        }
        SELECTED.put(player.getUUID(), index);
    }

    public static int selectedIndex(UUID id) {
        return SELECTED.getOrDefault(id, 0);
    }

    /** 右键释放选中技能（Alt 技能栏模式下客户端发起）。 */
    public static void useSelected(ServerPlayer player) {
        if (!SkillCardsBridge.available() || !ManhuntGame.isRunning() || !TeamUtil.isRunner(player)
                || ManhuntGame.isEliminated(player.getUUID())) {
            return;
        }
        ItemStack[] bar = bar(player.getUUID());
        int index = Math.floorMod(SELECTED.getOrDefault(player.getUUID(), 0), BAR_SLOTS);
        ItemStack card = bar[index];
        if (card == null || card.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7该技能栏位为空。"), true);
            return;
        }
        if (player.getCooldowns().isOnCooldown(card)) {
            return;
        }
        boolean used = SkillCardsBridge.activate(player, card);
        if (used) {
            player.getCooldowns().addCooldown(card, SkillCardsBridge.cooldownTicks(card));
            announceSkill(player, card, index + 1, BAR_SLOTS);
        }
    }

    /** 切换/选定后的播报：卡名 + 位置 + 效果概述。 */
    private static void announceSkill(ServerPlayer player, ItemStack card, int idx, int total) {
        String brief = Component.translatable(
            card.getItem().getDescriptionId() + ".brief").getString();
        player.sendSystemMessage(Component.literal(
            "§6[技能] §f" + card.getHoverName().getString()
                + " §7(" + idx + "/" + total + ") §b" + brief), true);
    }

    // ==================== 背包编辑（背包界面技能栏） ====================

    /**
     * 对局中调整顺序：from → to 移动/交换（不可取出）。
     * @return 是否有变化（客户端据此刷新）
     */
    public static boolean moveCard(UUID id, int from, int to) {
        if (from < 0 || from >= BAR_SLOTS || to < 0 || to >= BAR_SLOTS || from == to) {
            return false;
        }
        ItemStack[] bar = bar(id);
        ItemStack moved = bar[from];
        if (moved == null || moved.isEmpty()) {
            return false;
        }
        ItemStack target = bar[to];
        bar[from] = target == null ? ItemStack.EMPTY : target;
        bar[to] = moved;
        return true;
    }

    /**
     * 调试/未开局：直接设置槽位（放入或取出）。
     * @return 是否接受
     */
    public static boolean editSlot(ServerPlayer player, int slot, ItemStack stack) {
        if (slot < 0 || slot >= BAR_SLOTS) {
            return false;
        }
        if (!stack.isEmpty() && !SkillCardsBridge.isSkillCard(stack)) {
            return false; // 只收技能卡
        }
        if (!stack.isEmpty() && SkillCardsBridge.isPassiveCard(stack)) {
            return false; // 被动卡走饰品栏，不入技能栏
        }
        bar(player.getUUID())[slot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        return true;
    }

    // ==================== 查询 ====================

    /** 技能栏中是否存在任一冷却完毕的卡（驱动选中提示）。 */
    public static boolean hasReadyCard(ServerPlayer player) {
        if (!SkillCardsBridge.available()) {
            return false;
        }
        for (ItemStack card : bar(player.getUUID())) {
            if (card != null && !card.isEmpty() && !player.getCooldowns().isOnCooldown(card)) {
                return true;
            }
        }
        return false;
    }

    /** 已拥有的卡牌物品 id 集合（技能栏 + 被动饰品栏 + 背包中的被动卡；用于不可重复抽取）。 */
    public static java.util.Set<String> ownedIds(ServerPlayer player) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (ItemStack stack : bar(player.getUUID())) {
            if (stack != null && !stack.isEmpty()) {
                ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        ids.addAll(com.example.manhunt.compat.SkillPassiveBridge.equippedIds(player));
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (SkillCardsBridge.isPassiveCard(stack)) {
                ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        return ids;
    }

    public static boolean hasCards(UUID id) {
        for (ItemStack stack : bar(id)) {
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static int cardCount(UUID id) {
        int n = 0;
        for (ItemStack stack : bar(id)) {
            if (stack != null && !stack.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** 技能栏全部卡牌物品 id（按槽位顺序，空格为空串；S2C 下发驱动 Alt 技能栏渲染）。 */
    public static List<String> skillIdList(ServerPlayer player) {
        List<String> ids = new ArrayList<>();
        for (ItemStack stack : bar(player.getUUID())) {
            ids.add(stack == null || stack.isEmpty()
                ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return ids;
    }

    /** 技能卡不可丢弃（取消抛掷；对局中的被动/主动卡均不落地面）。 */
    public static void onItemToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        if (!SkillCardsBridge.available() || !ManhuntGame.isRunning()) {
            return;
        }
        var player = event.getPlayer();
        if (!ManhuntGame.isRunner(player.getUUID()) || ManhuntGame.isEliminated(player.getUUID())) {
            return;
        }
        if (SkillCardsBridge.isSkillCard(event.getEntity().getItem())) {
            event.setCanceled(true);
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal("§7[猎人游戏] 技能卡无法丢弃。"), true);
            }
        }
    }

    // ==================== 状态 ====================

    public static void onLoggedOut(UUID id) {
        // 技能栏随对局持久化，登出保留
    }

    public static void reset() {
        BAR.clear();
        SELECTED.clear();
    }

    // ==================== 持久化 ====================

    /** 技能栏序列化：按槽位顺序的物品 id 列表（空格 = 空串；技能卡无组件，id 足够）。 */
    public static Map<UUID, List<String>> snapshotIds() {
        Map<UUID, List<String>> out = new HashMap<>();
        for (Map.Entry<UUID, ItemStack[]> e : BAR.entrySet()) {
            List<String> ids = new ArrayList<>();
            for (ItemStack stack : e.getValue()) {
                ids.add(stack == null || stack.isEmpty()
                    ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
            out.put(e.getKey(), ids);
        }
        return out;
    }

    public static void restore(Map<UUID, List<String>> saved) {
        BAR.clear();
        SELECTED.clear();
        if (!SkillCardsBridge.available()) {
            ManhuntMod.LOGGER.warn("[Manhunt] 存档含技能栏但未安装技能卡模组，已跳过恢复");
            return;
        }
        for (Map.Entry<UUID, List<String>> e : saved.entrySet()) {
            ItemStack[] bar = new ItemStack[BAR_SLOTS];
            for (int i = 0; i < Math.min(BAR_SLOTS, e.getValue().size()); i++) {
                String idStr = e.getValue().get(i);
                if (idStr == null || idStr.isEmpty()) {
                    continue;
                }
                try {
                    var holder = BuiltInRegistries.ITEM.get(Identifier.parse(idStr));
                    if (holder.isPresent() && SkillCardsBridge.isSkillCard(new ItemStack(holder.get().value()))) {
                        bar[i] = new ItemStack(holder.get().value());
                    }
                } catch (Exception ignored) {
                }
            }
            BAR.put(e.getKey(), bar);
        }
    }
}
