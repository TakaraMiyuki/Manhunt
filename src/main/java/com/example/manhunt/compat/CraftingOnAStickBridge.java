package com.example.manhunt.compat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import com.example.manhunt.ManhuntMod;
import com.example.manhunt.util.InvUtil;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * 与 Crafting on a Stick（modId crafting_on_a_stick）+ Curios API 的软联动：
 * 每局开始时把"便携式工作台"物品放进玩家的专属饰品栏（未装模组时为无操作）。
 * 全程反射调用 Curios API（16.0.0+26.2：getCuriosInventory / setEquippedCurio），
 * 不产生编译期依赖；饰品栏放不下或反射失败时退回背包。
 */
public final class CraftingOnAStickBridge {
    private CraftingOnAStickBridge() {}

    private static final String COAS_MOD_ID = "crafting_on_a_stick";
    private static final String CURIOS_SLOT_ID = "crafting_on_a_stick";
    /** Crafting on a Stick 物品 id 候选（工作台款优先）。 */
    private static final List<String> STICK_IDS = List.of(
        COAS_MOD_ID + ":crafting_table",
        COAS_MOD_ID + ":crafting_on_a_stick",
        "craftingonastick:crafting_on_a_stick");

    public static boolean available() {
        return ModList.get().isLoaded(COAS_MOD_ID) && ModList.get().isLoaded("curios");
    }

    private static Item findStickItem() {
        for (String id : STICK_IDS) {
            try {
                var holder = BuiltInRegistries.ITEM.get(Identifier.parse(id));
                if (holder.isPresent()) {
                    return holder.get().value();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** 玩家背包中是否已有便携工作台。 */
    private static boolean alreadyInInventory(ServerPlayer player, Item item) {
        var items = player.getInventory().getNonEquipmentItems();
        for (ItemStack stack : items) {
            if (stack.is(item)) {
                return true;
            }
        }
        return player.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND).is(item);
    }

    /** 每局开始：给玩家一个便携式工作台，优先放入专属饰品栏。 */
    public static void giveStick(ServerPlayer player) {
        if (!available()) {
            return;
        }
        Item stick = findStickItem();
        if (stick == null) {
            ManhuntMod.LOGGER.warn("[Manhunt] 未找到 Crafting on a Stick 物品，便携工作台跳过发放");
            return;
        }
        if (alreadyInInventory(player, stick)) {
            return;
        }
        ItemStack stack = new ItemStack(stick);
        String result = equipIntoCurios(player, stack);
        if ("equipped".equals(result)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§7[猎人游戏] 便携式工作台已放入饰品栏（右键即可使用 3×3 合成）。"), true);
        } else if ("occupied".equals(result)) {
            ManhuntMod.LOGGER.debug("[Manhunt] 玩家饰品栏已有便携工作台");
        } else {
            InvUtil.safeAdd(player, stack);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§7[猎人游戏] 便携式工作台已放入背包。"), true);
        }
    }

    /**
     * 反射调用 Curios API：把物品放进专属饰品栏（crafting_on_a_stick）的空位。
     * @return "equipped" 成功 / "occupied" 专属栏已有物品 / "failed" 无饰品栏或反射失败
     */
    private static String equipIntoCurios(ServerPlayer player, ItemStack stack) {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Optional<?> handlerOpt = (Optional<?>) api
                .getMethod("getCuriosInventory", net.minecraft.world.entity.player.Player.class)
                .invoke(null, player);
            if (handlerOpt.isEmpty()) {
                return "failed";
            }
            Object handler = handlerOpt.get();
            Class<?> handlerType = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Object curios = handlerType.getMethod("getCurios").invoke(handler);
            if (!(curios instanceof java.util.Map<?, ?> curioMap)) {
                return "failed";
            }
            // 优先专属槽
            String preferred = curioMap.containsKey(CURIOS_SLOT_ID) ? CURIOS_SLOT_ID : null;
            String emptyKey = null;
            int emptyIndex = -1;
            for (var entry : curioMap.entrySet()) {
                String slotId = String.valueOf(entry.getKey());
                Object stacks = stacksHandlerType()
                    .getMethod("getStacks").invoke(entry.getValue());
                int count = (int) stacks.getClass().getMethod("getSlots").invoke(stacks);
                Method getStackInSlot = stacks.getClass().getMethod("getStackInSlot", int.class);
                for (int i = 0; i < count; i++) {
                    ItemStack inSlot = (ItemStack) getStackInSlot.invoke(stacks, i);
                    if (slotId.equals(CURIOS_SLOT_ID) && !inSlot.isEmpty()) {
                        return "occupied"; // 专属栏已有便携工作台
                    }
                    if (inSlot.isEmpty() && emptyKey == null) {
                        emptyKey = slotId;
                        emptyIndex = i;
                    }
                    if (preferred != null) {
                        break; // 有专属槽时只检查专属槽
                    }
                }
            }
            if (emptyKey == null) {
                return "failed";
            }
            handlerType.getMethod("setEquippedCurio", String.class, int.class, ItemStack.class)
                .invoke(handler, emptyKey, emptyIndex, stack);
            return "equipped";
        } catch (Throwable t) {
            ManhuntMod.LOGGER.debug("[Manhunt] Curios 饰品栏装备失败，退回背包: {}", t.toString());
            return "failed";
        }
    }

    private static Class<?> stacksHandlerType() throws ClassNotFoundException {
        return Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
    }
}
