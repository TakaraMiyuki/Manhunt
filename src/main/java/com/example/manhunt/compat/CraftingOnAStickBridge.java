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
 * 与 Crafting on a Stick + Curios API 的软联动：
 * 每局开始时把"便携式工作台"物品放进玩家的饰品栏（未装模组时为无操作）。
 * 全程反射调用 Curios API，不产生编译期依赖；饰品栏放不下或反射失败时退回背包。
 */
public final class CraftingOnAStickBridge {
    private CraftingOnAStickBridge() {}

    /** Crafting on a Stick 物品 id 候选（不同版本命名可能不同）。 */
    private static final List<String> STICK_IDS = List.of(
        "craftingonastick:crafting_on_a_stick",
        "craftingonastick:crafting_on_a_stick_simple");

    public static boolean available() {
        return ModList.get().isLoaded("craftingonastick") && ModList.get().isLoaded("curios");
    }

    /** 玩家背包或饰品栏中是否已有便携工作台。 */
    private static boolean alreadyHas(ServerPlayer player, Item item) {
        var items = player.getInventory().getNonEquipmentItems();
        for (ItemStack stack : items) {
            if (stack.is(item)) {
                return true;
            }
        }
        return player.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND).is(item);
    }

    /** 每局开始：给玩家一个便携式工作台，优先放入饰品栏。 */
    public static void giveStick(ServerPlayer player) {
        if (!available()) {
            return;
        }
        Item stick = findStickItem();
        if (stick == null) {
            ManhuntMod.LOGGER.warn("[Manhunt] 未找到 Crafting on a Stick 物品，便携工作台跳过发放");
            return;
        }
        if (alreadyHas(player, stick)) {
            return;
        }
        ItemStack stack = new ItemStack(stick);
        if (equipIntoCurios(player, stack)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§7[猎人游戏] 便携式工作台已放入饰品栏（右键即可使用 3×3 合成）。"), true);
        } else {
            InvUtil.safeAdd(player, stack);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§7[猎人游戏] 便携式工作台已放入背包（建议放入饰品栏）。"), true);
        }
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

    /** 反射调用 Curios API：把物品放入第一个空饰品栏位。 */
    private static boolean equipIntoCurios(ServerPlayer player, ItemStack stack) {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getCuriosInventory = api.getMethod("getCuriosInventory",
                net.minecraft.world.entity.player.Player.class);
            Optional<?> handlerOpt = (Optional<?>) getCuriosInventory.invoke(null, player);
            if (handlerOpt.isEmpty()) {
                return false;
            }
            Object handler = handlerOpt.get();
            Class<?> handlerType = Class.forName("top.theillusivec4.curios.api.type.inventory.ICuriosItemHandler");
            MapMethod getCurios = new MapMethod(handlerType.getMethod("getCurios"));
            Object curios = getCurios.invoke(handler);
            if (!(curios instanceof java.util.Map<?, ?> curioMap)) {
                return false;
            }
            Class<?> stacksHandlerType = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            for (var entry : curioMap.entrySet()) {
                Object stacksHandler = entry.getValue();
                Object stacks = stacksHandlerType.getMethod("getStacks").invoke(stacksHandler);
                Method size = stacks.getClass().getMethod("getContainerSize");
                Method get = stacks.getClass().getMethod("getItem", int.class);
                Method set = stacks.getClass().getMethod("setItem", int.class, ItemStack.class);
                int count = (int) size.invoke(stacks);
                for (int i = 0; i < count; i++) {
                    ItemStack inSlot = (ItemStack) get.invoke(stacks, i);
                    if (inSlot.isEmpty()) {
                        set.invoke(stacks, i, stack);
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            ManhuntMod.LOGGER.debug("[Manhunt] Curios 饰品栏装备失败，退回背包: {}", t.toString());
        }
        return false;
    }

    /** 避免直接依赖 Curios 类型的转发小类。 */
    private record MapMethod(Method method) {
        Object invoke(Object target) throws Exception {
            return method.invoke(target);
        }
    }
}
