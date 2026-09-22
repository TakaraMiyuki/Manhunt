package com.example.manhunt.util;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 背包安全入包：跳过技能槽（快捷栏最右一格，由技能栏系统独占），
 * 防止拾取/发放/回退的物品落进技能槽后被守护逻辑反复搬运或覆盖。
 */
public final class InvUtil {
    private InvUtil() {}

    /** 合并或放入空位（始终跳过技能槽）。完全放不下返回 false。 */
    public static boolean safeAdd(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
        int slotCount = items.size();

        // 1) 与已有可堆叠物品合并（跳过技能槽）
        ItemStack remaining = stack;
        for (int i = 0; i < slotCount && !remaining.isEmpty(); i++) {
            if (i == skillSlot()) {
                continue;
            }
            ItemStack inSlot = items.get(i);
            if (!inSlot.isEmpty() && ItemStack.isSameItemSameComponents(inSlot, remaining)) {
                int max = Math.min(inSlot.getMaxStackSize(), remaining.getMaxStackSize());
                int canTake = max - inSlot.getCount();
                if (canTake > 0) {
                    int moved = Math.min(canTake, remaining.getCount());
                    inSlot.grow(moved);
                    remaining.shrink(moved);
                    if (remaining.isEmpty()) {
                        return true;
                    }
                }
            }
        }

        // 2) 放入空位（跳过技能槽）
        for (int i = 0; i < slotCount; i++) {
            if (i == skillSlot()) {
                continue;
            }
            if (items.get(i).isEmpty()) {
                items.set(i, remaining.split(remaining.getCount()));
                return true;
            }
        }
        return false;
    }

    private static int skillSlot() {
        return com.example.manhunt.GameConfig.CARD_SLOT;
    }
}
