package com.example.manhunt.cards;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.manhunt.GameConfig;
import com.example.manhunt.game.ManhuntGame;

import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 技能卡栏位管理：快捷栏最右 3 格（slot 6/7/8）固定放置非彩技能卡。
 * <ul>
 *   <li>彩卡不占固定栏位，直接进入背包；</li>
 *   <li>固定栏位已满时，新卡进入待定区，玩家通过聊天按钮选择"丢弃某张现有卡"或"放弃新卡"；</li>
 *   <li>每秒守护：被移出固定栏位的非彩卡自动换回。</li>
 * </ul>
 */
public final class CardSlotManager {
    private CardSlotManager() {}

    /** 待定新卡：等待玩家决定丢弃/放弃。 */
    private static final Map<UUID, ItemStack> PENDING = new HashMap<>();

    // ==================== 发放 ====================

    /** 发放抽到的技能卡。 */
    public static void giveDrawnCard(ServerPlayer runner, SkillCardsBridge.CardDraw draw) {
        var grade = SkillCardsBridge.gradeOf(draw.stack());
        if (grade == com.example.skillcards.registry.Card.Grade.RAINBOW) {
            // 彩卡不占固定栏位
            if (!runner.getInventory().add(draw.stack())) {
                runner.drop(draw.stack(), false);
            }
            runner.sendSystemMessage(Component.literal(
                "§d§l[彩卡] §r§d" + draw.stack().getHoverName().getString() + " §7已放入背包（不占用技能栏）"));
            return;
        }
        int slot = firstEmptyFixedSlot(runner);
        if (slot >= 0) {
            runner.getInventory().getNonEquipmentItems().set(slot, draw.stack());
            runner.sendSystemMessage(Component.literal(
                "§6[技能卡] §f" + draw.stack().getHoverName().getString() + " §7已放入技能栏（第 "
                    + (slot - GameConfig.CARD_FIXED_SLOTS[0] + 1) + " 格）"));
        } else {
            PENDING.put(runner.getUUID(), draw.stack());
            sendChoiceMessage(runner, draw.stack());
        }
    }

    private static int firstEmptyFixedSlot(ServerPlayer runner) {
        NonNullList<ItemStack> items = runner.getInventory().getNonEquipmentItems();
        for (int slot : GameConfig.CARD_FIXED_SLOTS) {
            if (items.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static void sendChoiceMessage(ServerPlayer runner, ItemStack newCard) {
        runner.sendSystemMessage(Component.literal(
            "§6[技能栏已满] §f获得新卡 §e" + newCard.getHoverName().getString() + "§f，请选择："));
        // 逐个固定栏位给出"丢弃并替换"按钮
        for (int slot : GameConfig.CARD_FIXED_SLOTS) {
            ItemStack occupant = runner.getInventory().getNonEquipmentItems().get(slot);
            String occupantName = occupant.isEmpty() ? "空" : occupant.getHoverName().getString();
            MutableComponent button = Component.literal("§c[丢弃 " + occupantName + " §7(第"
                + (slot + 1) + "格)§c] ");
            button.withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(
                "/manhunt cardchoose " + slot)));
            runner.sendSystemMessage(button);
        }
        MutableComponent giveUp = Component.literal("§7[放弃新卡]");
        giveUp.withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/manhunt cardchoose giveup")));
        runner.sendSystemMessage(giveUp);
    }

    // ==================== 选择（/manhunt cardchoose 调用） ====================

    /** 处理玩家的选择。slot 为固定栏位序号，或 -1 表示放弃新卡。 */
    public static void handleChoice(ServerPlayer runner, int slot) {
        ItemStack pending = PENDING.remove(runner.getUUID());
        if (pending == null) {
            runner.sendSystemMessage(Component.literal("§7没有待处理的新技能卡。"));
            return;
        }
        if (slot < 0) {
            runner.sendSystemMessage(Component.literal("§7已放弃新卡 §f" + pending.getHoverName().getString()));
            return;
        }
        NonNullList<ItemStack> items = runner.getInventory().getNonEquipmentItems();
        if (!isValidFixedSlot(slot) || items.get(slot).isEmpty()) {
            // 栏位已空或无效：直接放入
            if (isValidFixedSlot(slot)) {
                items.set(slot, pending);
            } else {
                if (!runner.getInventory().add(pending)) {
                    runner.drop(pending, false);
                }
            }
            return;
        }
        String discarded = items.get(slot).getHoverName().getString();
        items.set(slot, pending);
        runner.sendSystemMessage(Component.literal(
            "§6[技能卡] §7丢弃了 §f" + discarded + "§7，新卡 §e" + pending.getHoverName().getString() + " §7已就位"));
    }

    private static boolean isValidFixedSlot(int slot) {
        for (int s : GameConfig.CARD_FIXED_SLOTS) {
            if (s == slot) {
                return true;
            }
        }
        return false;
    }

    // ==================== 守护 ====================

    /** 每秒守护：非彩技能卡出现在固定栏位之外时自动换回。 */
    public static void tickGuard(MinecraftServer server) {
        for (ServerPlayer p : ManhuntGame.onlineAliveRunners(server)) {
            NonNullList<ItemStack> items = p.getInventory().getNonEquipmentItems();
            for (int i = 0; i < items.size(); i++) {
                if (isFixedSlot(i)) {
                    continue;
                }
                ItemStack stack = items.get(i);
                var grade = SkillCardsBridge.gradeOf(stack);
                if (grade == null || grade == com.example.skillcards.registry.Card.Grade.RAINBOW) {
                    continue;
                }
                // 换回固定栏位：优先空位，否则与第一个固定栏位占用者对调
                int target = -1;
                for (int slot : GameConfig.CARD_FIXED_SLOTS) {
                    if (items.get(slot).isEmpty()) {
                        target = slot;
                        break;
                    }
                }
                if (target >= 0) {
                    items.set(target, stack);
                    items.set(i, ItemStack.EMPTY);
                } else {
                    // 全满（异常情况兜底）：与第一个固定栏位对调
                    int first = GameConfig.CARD_FIXED_SLOTS[0];
                    ItemStack occupant = items.get(first);
                    items.set(first, stack);
                    items.set(i, occupant);
                }
            }
        }
    }

    private static boolean isFixedSlot(int slot) {
        return isValidFixedSlot(slot);
    }

    public static boolean hasPending(UUID id) {
        return PENDING.containsKey(id);
    }

    public static void onLoggedOut(UUID id) {
        PENDING.remove(id);
    }

    public static void reset() {
        PENDING.clear();
    }
}
