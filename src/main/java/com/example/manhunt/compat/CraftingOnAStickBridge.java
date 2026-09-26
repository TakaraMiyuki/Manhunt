package com.example.manhunt.compat;

import java.util.Map;

import com.example.manhunt.ManhuntMod;
import com.example.manhunt.util.InvUtil;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * 与 Crafting on a Stick（modId crafting_on_a_stick）+ Curios API 的联动：
 * 每局开始时把"便携式工作台"物品放进玩家的专属饰品栏（crafting_on_a_stick 槽）。
 *
 * 编译期直接引用 Curios API（compileOnly），运行期由用户实例的 Curios 提供；
 * 未装 CoAS/Curios 时本桥接全部方法为无操作。
 */
public final class CraftingOnAStickBridge {
    private CraftingOnAStickBridge() {}

    private static final String COAS_SLOT_ID = "crafting_on_a_stick";
    private static final Identifier STICK_ID =
        Identifier.fromNamespaceAndPath("crafting_on_a_stick", "crafting_table");

    public static boolean available() {
        return ModList.get().isLoaded("crafting_on_a_stick") && ModList.get().isLoaded("curios");
    }

    private static Item findStickItem() {
        var holder = BuiltInRegistries.ITEM.get(STICK_ID);
        // 默认注册表对未知 id 可能回落到 AIR——必须排除
        if (holder.isPresent() && holder.get().value() != net.minecraft.world.item.Items.AIR) {
            return holder.get().value();
        }
        return null;
    }

    /** 玩家背包或饰品栏中是否已有便携工作台。 */
    private static boolean alreadyHas(ServerPlayer player, Item item) {
        var items = player.getInventory().getNonEquipmentItems();
        for (ItemStack stack : items) {
            if (stack.is(item)) {
                return true;
            }
        }
        if (player.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND).is(item)) {
            return true;
        }
        var handlerOpt = CuriosApi.getCuriosInventory(player);
        if (handlerOpt.isPresent()) {
            var curios = handlerOpt.get().getCurios();
            ICurioStacksHandler coas = curios.get(COAS_SLOT_ID);
            if (coas != null) {
                var stacks = coas.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    if (stacks.getStackInSlot(i).is(item)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 每局开始：给玩家一个便携式工作台，放入 CoAS 专属饰品栏。 */
    public static void giveStick(ServerPlayer player) {
        if (!available()) {
            return;
        }
        Item stick = findStickItem();
        if (stick == null) {
            ManhuntMod.LOGGER.warn("[Manhunt] 未在注册表中找到 CoAS 便携工作台物品");
            return;
        }
        var handlerOpt = CuriosApi.getCuriosInventory(player);
        if (handlerOpt.isEmpty()) {
            ManhuntMod.LOGGER.warn("[Manhunt] Curios 处理器不可用，便携工作台发放跳过");
            return;
        }
        var curios = handlerOpt.get().getCurios();
        ICurioStacksHandler coas = curios.get(COAS_SLOT_ID);
        if (coas == null) {
            ManhuntMod.LOGGER.warn("[Manhunt] Curios 处理器中无 {} 槽位", COAS_SLOT_ID);
            return;
        }
        var stacks = coas.getStacks();
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (stacks.getStackInSlot(i).isEmpty()) {
                // 官方装备入口：内部处理同步与变更回调
                handlerOpt.get().setEquippedCurio(COAS_SLOT_ID, i, new ItemStack(stick));
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§7[猎人游戏] 便携式工作台已放入饰品栏（按 V 或 CoAS 快捷键使用）。"), true);
                return;
            }
        }
        ManhuntMod.LOGGER.warn("[Manhunt] 玩家 {} 饰品槽已满，便携工作台发放跳过",
            player.getName().getString());
    }
}
