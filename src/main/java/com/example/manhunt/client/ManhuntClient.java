package com.example.manhunt.client;

import com.example.manhunt.ManhuntMod;
import com.example.manhunt.net.LootRollPayload;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

import org.lwjgl.glfw.GLFW;

/**
 * 客户端事件订阅：HUD 抽奖层 + 技能槽边框 + S2C 包 handler + 动画 tick + 领取模式输入。
 * （NeoForge 按 IModBusEvent 自动路由 mod 总线 / 游戏总线）
 */
@EventBusSubscriber(modid = ManhuntMod.MODID, value = Dist.CLIENT)
public final class ManhuntClient {
    private ManhuntClient() {}

    // ==================== 可配置键位（设置 → 控制 → 猎人游戏·资源抽奖） ====================

    public static final KeyMapping.Category LOOT_CATEGORY =
        new KeyMapping.Category(Identifier.fromNamespaceAndPath(ManhuntMod.MODID, "loot"));
    /** 标记/取消标记待领取物品（默认鼠标中键，可改键）。 */
    public static final KeyMapping LOOT_MARK = new KeyMapping(
        "key.manhunt.loot_mark", KeyConflictContext.IN_GAME,
        InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, LOOT_CATEGORY);
    /** 领取标记物品并退出选择阶段（默认鼠标右键，可改键）。 */
    public static final KeyMapping LOOT_CLAIM = new KeyMapping(
        "key.manhunt.loot_claim", KeyConflictContext.IN_GAME,
        InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, LOOT_CATEGORY);


    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(LOOT_CATEGORY);
        event.register(LOOT_MARK);
        event.register(LOOT_CLAIM);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
            Identifier.fromNamespaceAndPath(ManhuntMod.MODID, "loot_roll"),
            ClientRollManager::render);
        event.registerAboveAll(
            Identifier.fromNamespaceAndPath(ManhuntMod.MODID, "skill_slot_border"),
            ManhuntClient::renderSkillSlotBorder);
        event.registerAboveAll(
            Identifier.fromNamespaceAndPath(ManhuntMod.MODID, "morale_bar"),
            ManhuntClient::renderMoraleBar);
    }

    /**
     * 猎人士气条：屏幕上方的独立条状 UI，使用原版 bossbar 雪碧图（蓝色 182×5），
     * 位置在原版 bossbar 区下方避让倒计时。进度 = 距下一档士气（阈值无封顶）。
     */
    private static void renderMoraleBar(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) {
            return;
        }
        if (!ManhuntClientState.isParticipant() || ManhuntClientState.isRunner()) {
            return; // 仅猎人显示
        }
        int morale = ManhuntClientState.morale();
        int rewards = ManhuntClientState.moraleRewards();
        int next = threshold(rewards);
        int prev = rewards == 0 ? 0 : threshold(rewards - 1);
        float progress = net.minecraft.util.Mth.clamp((morale - prev) / (float) Math.max(1, next - prev), 0.0F, 1.0F);
        int x = g.guiWidth() / 2 - 91;
        int y = 24;
        // 标注：士气值 + 档数（1-based，无封顶）
        String label = "§b士气 " + morale + " §7· 档 " + (rewards + 1);
        g.text(mc.font, label, (g.guiWidth() - mc.font.width(label)) / 2, y - 10, 0xFFFFFFFF, true);
        // 原版 bossbar 雪碧图（与 BossHealthOverlay 同款绘制）
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            Identifier.withDefaultNamespace("boss_bar/blue_background"), 182, 5, 0, 0, x, y, 182, 5);
        int fill = net.minecraft.util.Mth.lerpDiscrete(progress, 0, 182);
        if (fill > 0) {
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                Identifier.withDefaultNamespace("boss_bar/blue_progress"), 182, 5, 0, 0, x, y, fill, 5);
        }
    }

    /** 第 rewardsIndex 档阈值；与 MoraleManager.threshold 一致（表末后按步长外推）。 */
    private static int threshold(int rewardsIndex) {
        int[] t = com.example.manhunt.GameConfig.MORALE_THRESHOLDS;
        return rewardsIndex < t.length
            ? t[rewardsIndex]
            : t[t.length - 1] + com.example.manhunt.GameConfig.MORALE_STEP_AFTER_LAST * (rewardsIndex - t.length + 1);
    }

    /**
     * 技能槽特殊边框：快捷栏最右一格为固定技能槽，显示金色脉冲边框。
     * 依据槽内是否为技能卡自我判定（技能卡只出现在游戏内的逃生者身上）。
     */
    private static void renderSkillSlotBorder(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) {
            return;
        }
        if (!ManhuntClientState.isRunner()) {
            // 猎人：追踪罗盘边框（贴近锁定目标时红色脉冲）
            renderCompassBorder(g, mc, delta, com.example.manhunt.item.ManhuntItems.TRACKING_COMPASS.get());
            return;
        }
        ItemStack slot = mc.player.getInventory().getItem(8);
        boolean hasCard = com.example.manhunt.cards.SkillCardsBridge.available()
            && com.example.manhunt.cards.SkillCardsBridge.isSkillCard(slot);
        // 原版槽位物品坐标：x = guiWidth/2 - 90 + i*20 + 2，y = guiHeight - 19
        int x = g.guiWidth() / 2 - 90 + 8 * 20 + 2;
        int y = g.guiHeight() - 19;
        long now = mc.level.getGameTime();
        // 技能库任一卡可用：金色脉冲；全部冷却中或技能库为空：静态金框
        float alpha;
        if (hasCard && ManhuntClientState.isSkillReady()) {
            alpha = 0.55F + 0.45F * (float) Math.abs(Math.sin((now + delta.getGameTimeDeltaPartialTick(false)) * 0.25));
        } else {
            alpha = hasCard ? 0.85F : 0.55F;
        }
        int col = withAlpha(ClientRollManager.accentGold(), alpha);
        g.fill(x - 3, y - 3, x + 19, y - 1, col);
        g.fill(x - 3, y + 17, x + 19, y + 19, col);
        g.fill(x - 3, y - 1, x - 1, y + 17, col);
        g.fill(x + 17, y - 1, x + 19, y + 17, col);
        // 逃生者：检查点罗盘 <50 格红脉冲
        renderCompassBorder(g, mc, delta, com.example.manhunt.item.ManhuntItems.CHECKPOINT_COMPASS.get());
    }

    /**
     * 罗盘槽位边框：罗盘所在格子常显金框；
     * 当罗盘目标（猎人=锁定逃生者 / 逃生者=当前检查点）距离 < 50 格时变为红色快速脉冲。
     */
    private static void renderCompassBorder(GuiGraphicsExtractor g, Minecraft mc, DeltaTracker delta,
                                            net.minecraft.world.item.Item compassItem) {
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) {
            return;
        }
        var inv = mc.player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack stack = inv.get(slot);
            if (!stack.is(compassItem)) {
                continue;
            }
            int x = g.guiWidth() / 2 - 90 + slot * 20 + 2;
            int y = g.guiHeight() - 19;
            long now = mc.level.getGameTime();
            float t = now + delta.getGameTimeDeltaPartialTick(false);
            boolean close = false;
            var tracker = stack.get(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER);
            if (tracker != null && tracker.target().isPresent()
                    && tracker.target().get().dimension() == mc.player.level().dimension()) {
                var tpos = tracker.target().get().pos();
                double d2 = mc.player.distanceToSqr(
                    tpos.getX() + 0.5, tpos.getY() + 0.5, tpos.getZ() + 0.5);
                close = d2 < 50.0 * 50.0;
            }
            int col;
            if (close) {
                col = withAlpha(0xFFE33B3B, 0.55F + 0.45F * (float) Math.abs(Math.sin(t * 0.5)));
            } else {
                col = withAlpha(0xFFFFC844, 0.75F);
            }
            g.fill(x - 3, y - 3, x + 19, y - 1, col);
            g.fill(x - 3, y + 17, x + 19, y + 19, col);
            g.fill(x - 3, y - 1, x - 1, y + 17, col);
            g.fill(x + 17, y - 1, x + 19, y + 17, col);
        }
    }

    private static int withAlpha(int argb, float alpha) {
        int a = (int) ((argb >>> 24) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    // ==================== CoAS 滚轮一键开关 ====================

    private static Class<?> coasWheelScreenClass;

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

    /**
     * 是否为 CoAS 系界面：滚轮，或单物品时经 SBOpen 直接打开的原版合成界面
     * （CoasItem 走 player.openMenu，客户端即 CraftingScreen）。
     */
    private static boolean isCoasScreen(Screen screen) {
        return isCoasWheelScreen(screen)
            || screen instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen;
    }

    private static KeyMapping coasOpenKey() {
        for (KeyMapping km : mc().options.keyMappings) {
            if (km.getName().equals("key.crafting_on_a_stick.open_curios")) {
                return km;
            }
        }
        // 回退：CoAS 键位注册类字段（防键名变更）
        try {
            Object km = Class.forName("com.ofekn.crafting_on_a_stick.client.CoasKeyMappings")
                .getField("OPEN_CURIOS_KEY").get(null);
            if (km instanceof KeyMapping k) {
                return k;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /**
     * CoAS 一键开关：CoAS 界面（滚轮或合成界面）打开时再按开启键 → 关闭。
     * CoAS 以 consumeClick 计数在 tick 中开屏——关闭后必须排空计数，否则同一次按键会立刻重开。
     */
    @SubscribeEvent
    public static void onCoasToggleKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft mc = mc();
        Screen screen = mc.gui.screen();
        if (screen == null || !isCoasScreen(screen)) {
            return;
        }
        KeyMapping openKey = coasOpenKey();
        if (openKey != null && openKey.getKey().getValue() == event.getKey()) {
            mc.gui.setScreen(null);
            while (openKey.consumeClick()) {
                // 排空点击计数
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterClientPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(LootRollPayload.TYPE, (payload, ctx) -> ClientRollManager.start(payload));
        event.register(com.example.manhunt.net.ManhuntRolePayload.TYPE,
            (payload, ctx) -> ManhuntClientState.update(payload.participant(), payload.runner(), payload.skillReady(), payload.morale(), payload.moraleRewards()));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientRollManager.tick();
    }

    // ==================== 领取模式输入 ====================

    @SubscribeEvent
    public static void onMouseScrolling(InputEvent.MouseScrollingEvent event) {
        if (ClientRollManager.onMouseScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (ClientRollManager.onMouseButton(event.getButton(), event.getAction())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        int key = event.getKey();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
                || key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT
                || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            ClientRollManager.onKey(key, event.getAction());
        }
    }
}
