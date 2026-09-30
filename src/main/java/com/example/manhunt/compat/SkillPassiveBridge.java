package com.example.manhunt.compat;

import com.example.manhunt.ManhuntMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 被动卡饰品栏桥接（赏金/经典联动通用）：Manhunt 数据包注册了 6 格的
 * {@code skill_passive} 饰品槽（validators=tag，物品需在 curios:skill_passive tag——由
 * SkillCards 提供），玩家抽到的被动卡自动装备到该槽位，离开对局时清空。
 */
public final class SkillPassiveBridge {
    private SkillPassiveBridge() {}

    public static final String SLOT_ID = "skill_passive";
    private static final int SLOT_COUNT = 6;

    /** 是否存在该饰品槽（curios 未装或槽未注册时为 false）。 */
    private static ICurioStacksHandler handler(ServerPlayer player) {
        var handlerOpt = CuriosApi.getCuriosInventory(player);
        if (handlerOpt.isEmpty()) {
            return null;
        }
        ICurioStacksHandler slot = handlerOpt.get().getCurios().get(SLOT_ID);
        if (slot == null) {
            return null;
        }
        return slot;
    }

    /** 把被动卡自动装备进 skill_passive 饰品槽；满员返回 false（调用方丢弃并提示）。 */
    public static boolean equipPassive(ServerPlayer player, ItemStack stack) {
        ICurioStacksHandler slot = handler(player);
        if (slot == null) {
            ManhuntMod.LOGGER.warn("[Manhunt] 被动卡饰品槽不可用（Curios 未装或槽未注册）");
            return false;
        }
        var handlerOpt = CuriosApi.getCuriosInventory(player);
        var stacks = slot.getStacks();
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (stacks.getStackInSlot(i).isEmpty()) {
                // 官方装备入口：内部处理同步与变更回调
                handlerOpt.get().setEquippedCurio(SLOT_ID, i, stack);
                return true;
            }
        }
        return false;
    }

    /** 清空全部被动卡饰品槽（开局/对局清理防跨局残留）。 */
    public static void clearPassives(ServerPlayer player) {
        ICurioStacksHandler slot = handler(player);
        if (slot == null) {
            return;
        }
        var handlerOpt = CuriosApi.getCuriosInventory(player);
        var stacks = slot.getStacks();
        for (int i = 0; i < Math.min(stacks.getSlots(), SLOT_COUNT); i++) {
            if (!stacks.getStackInSlot(i).isEmpty()) {
                handlerOpt.get().setEquippedCurio(SLOT_ID, i, ItemStack.EMPTY);
            }
        }
    }

    /** 玩家被动饰品槽中的技能卡物品 id（用于不可重复抽取）。 */
    public static Set<String> equippedIds(ServerPlayer player) {
        Set<String> ids = new LinkedHashSet<>();
        ICurioStacksHandler slot = handler(player);
        if (slot == null) {
            return ids;
        }
        var stacks = slot.getStacks();
        for (int i = 0; i < stacks.getSlots(); i++) {
            ItemStack stack = stacks.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ids.add(net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(stack.getItem()).toString());
            }
        }
        return ids;
    }
}
