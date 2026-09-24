import re

BASE = "src/main/java/com/example/manhunt/"

def patch(path, pairs):
    p = BASE + path
    s = open(p, encoding="utf-8").read()
    changed = False
    for old, new in pairs:
        if new in s and old not in s:
            continue
        assert old in s, f"{path}: anchor not found: {old[:70]!r}"
        s = s.replace(old, new, 1)
        changed = True
    if changed:
        open(p, "w", encoding="utf-8", newline="\n").write(s)
        print(path, "ok")
    else:
        print(path, "skip")

# ============ 1: 桥接反射修复（接口查找 + setAccessible + WARN） ============
patch("compat/CraftingOnAStickBridge.java", [
    ("""    private static String equipIntoCurios(ServerPlayer player, ItemStack stack) {
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
    }""",
     """    /** 反射查找方法并强制可访问（Curios 槽位实现类可能非公开）。 */
    private static Method lookup(Class<?> type, String name, Class<?>... params) throws Exception {
        Method m = type.getMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    private static String equipIntoCurios(ServerPlayer player, ItemStack stack) {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Optional<?> handlerOpt = (Optional<?>) lookup(api,
                "getCuriosInventory", net.minecraft.world.entity.player.Player.class)
                .invoke(null, player);
            if (handlerOpt.isEmpty()) {
                ManhuntMod.LOGGER.warn("[Manhunt] Curios getCuriosInventory 为空，无法装备饰品栏");
                return "failed";
            }
            Object handler = handlerOpt.get();
            Class<?> handlerType = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Object curios = lookup(handlerType, "getCurios").invoke(handler);
            if (!(curios instanceof java.util.Map<?, ?> curioMap)) {
                ManhuntMod.LOGGER.warn("[Manhunt] Curios getCurios 返回异常类型");
                return "failed";
            }
            Method setEquipped = lookup(handlerType, "setEquippedCurio",
                String.class, int.class, ItemStack.class);
            // 优先 CoAS 专属槽；无专属槽时退而求其次找任意空位
            String chosenKey = curioMap.containsKey(CURIOS_SLOT_ID) ? CURIOS_SLOT_ID : null;
            String emptyKey = null;
            int emptyIndex = -1;
            for (var entry : curioMap.entrySet()) {
                String slotId = String.valueOf(entry.getKey());
                Object stacks = lookup(stacksHandlerType(), "getStacks").invoke(entry.getValue());
                Class<?> dyn = Class.forName("top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler");
                int count = (int) lookup(dyn, "getSlots").invoke(stacks);
                Method getStackInSlot = lookup(dyn, "getStackInSlot", int.class);
                for (int i = 0; i < count; i++) {
                    ItemStack inSlot = (ItemStack) getStackInSlot.invoke(stacks, i);
                    if (slotId.equals(CURIOS_SLOT_ID) && !inSlot.isEmpty()) {
                        return "occupied"; // 专属栏已有便携工作台
                    }
                    if (inSlot.isEmpty() && emptyKey == null) {
                        emptyKey = slotId;
                        emptyIndex = i;
                    }
                    if (chosenKey != null) {
                        break; // 有专属槽时只检查专属槽
                    }
                }
            }
            if (emptyKey == null) {
                ManhuntMod.LOGGER.warn("[Manhunt] Curios 饰品栏无空位，退回背包");
                return "failed";
            }
            setEquipped.invoke(handler, emptyKey, emptyIndex, stack);
            return "equipped";
        } catch (Throwable t) {
            ManhuntMod.LOGGER.warn("[Manhunt] Curios 饰品栏装备失败，退回背包", t);
            return "failed";
        }
    }"""),
])

# ============ 2: CoAS 滚轮一键开关（Manhunt 客户端） ============
patch("client/ManhuntClient.java", [
    ("""import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;""",
     """import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;"""),
    ("""    private static int withAlpha(int argb, float alpha) {
        int a = (int) ((argb >>> 24) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }""",
     """    private static int withAlpha(int argb, float alpha) {
        int a = (int) ((argb >>> 24) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    // ==================== CoAS 滚轮一键开关 ====================

    private static Boolean coasWheelScreenClass;
    private static boolean coasPressArmed;

    private static boolean isCoasWheelScreen(Screen screen) {
        if (coasWheelScreenClass == null) {
            try {
                coasWheelScreenClass = Class.forName(
                    "com.ofekn.crafting_on_a_stick.client.CoasWheelScreen");
            } catch (Throwable t) {
                coasWheelScreenClass = Void.class; // 未装 CoAS
            }
        }
        return coasWheelScreenClass.isInstance(screen);
    }

    private static KeyMapping coasOpenKey() {
        for (KeyMapping km : mc().options.keyMappings) {
            if (km.getName().equals("key.crafting_on_a_stick.open_curios")) {
                return km;
            }
        }
        return null;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** CoasWheelScreen 打开时再按开启键 → 关闭（一键开关）。 */
    @SubscribeEvent
    public static void onCoasToggleKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft mc = mc();
        Screen screen = mc.gui.screen();
        boolean coasScreen = screen != null && isCoasWheelScreen(screen);
        if (!coasScreen) {
            coasPressArmed = true; // 本次按下可能打开了滚轮
            return;
        }
        KeyMapping openKey = coasOpenKey();
        if (openKey != null && openKey.getKey().getValue() == event.getKey() && coasPressArmed) {
            mc.gui.setScreen(null);
            coasPressArmed = false;
        }
    }"""),
])
