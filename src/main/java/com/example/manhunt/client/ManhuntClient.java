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
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
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
    /** 超级疾跑开关（赏金模式，默认 C，可改键）。 */
    public static final KeyMapping SPRINT_KEY = new KeyMapping(
        "key.manhunt.super_sprint_toggle", KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, LOOT_CATEGORY);
    /** 技能栏呼出（按住，默认左 Alt，可改键）。 */
    public static final KeyMapping SKILL_BAR_KEY = new KeyMapping(
        "key.manhunt.skill_bar", KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, LOOT_CATEGORY);


    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(LOOT_CATEGORY);
        event.register(LOOT_MARK);
        event.register(LOOT_CLAIM);
        event.register(SPRINT_KEY);
        event.register(SKILL_BAR_KEY);
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
        event.registerAboveAll(
            Identifier.fromNamespaceAndPath(ManhuntMod.MODID, "respawn_hint"),
            ManhuntClient::renderRespawnHint);
        event.registerAboveAll(
            Identifier.fromNamespaceAndPath(ManhuntMod.MODID, "skill_hotbar"),
            ClientSkillBar::renderHotbar);
    }

    /** Alt 技能栏激活时取消原版热栏渲染（由技能热栏层替换）。 */
    @SubscribeEvent
    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        ClientSkillBar.onRenderGuiLayerPre(event);
    }

    /** 背包界面：技能栏覆盖层渲染。 */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        ClientSkillBar.onScreenRender(event);
    }

    /** 背包界面：技能栏点击（调整顺序/调试放入取出）。 */
    @SubscribeEvent
    public static void onScreenMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (ClientSkillBar.onScreenClick(event)) {
            event.setCanceled(true);
        }
    }

    /** 复活倒计时小字：屏幕准星下方居中（旁观等待复活时显示剩余秒数）。 */
    private static void renderRespawnHint(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) {
            return;
        }
        int seconds = ManhuntClientState.respawnSeconds();
        if (seconds < 0) {
            return;
        }
        String text = "§e将在 " + seconds + " 秒后复活…";
        g.text(mc.font, text, (g.guiWidth() - mc.font.width(text)) / 2,
            g.guiHeight() / 2 + 20, 0xFFFFFFFF, true);
    }

    /**
     * 猎人量表：经典=士气（蓝），赏金=赏金（黄，金色观感）。对齐屏幕上方原版 bossbar 位置。
     * 逃跑倒计时期间隐藏。进度 = 距下一档（阈值无封顶）。
     */
    private static void renderMoraleBar(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) {
            return;
        }
        if (!ManhuntClientState.isParticipant() || ManhuntClientState.isRunner()) {
            return; // 仅猎人显示
        }
        if (mc.player.level().dimension() == net.minecraft.world.level.Level.END) {
            return; // 末地阶段：隐藏士气/赏金量表
        }
        boolean bounty = ManhuntClientState.isBountyMode();
        int value = ManhuntClientState.morale();
        int rewards = ManhuntClientState.moraleRewards();
        int next = threshold(rewards, ManhuntClientState.hunters());
        int prev = rewards == 0 ? 0 : threshold(rewards - 1, ManhuntClientState.hunters());
        float progress = net.minecraft.util.Mth.clamp((value - prev) / (float) Math.max(1, next - prev), 0.0F, 1.0F);
        int x = g.guiWidth() / 2 - 91;
        // 与原版 bossbar 同位对齐：存在原版 bossbar（逃跑倒计时等）时像原版一样顺位下移一行
        int y = ManhuntClientState.isEscapePhase() ? 12 + 19 : 12;
        String label = (bounty ? "§6赏金 " : "§b士气 ") + value + " §7· 档 " + (rewards + 1);
        g.text(mc.font, label, (g.guiWidth() - mc.font.width(label)) / 2, y - 10, 0xFFFFFFFF, true);
        // 原版 bossbar 雪碧图（赏金=黄 / 士气=蓝）
        String color = bounty ? "yellow" : "blue";
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            Identifier.withDefaultNamespace("boss_bar/" + color + "_background"), 182, 5, 0, 0, x, y, 182, 5);
        int fill = net.minecraft.util.Mth.lerpDiscrete(progress, 0, 182);
        if (fill > 0) {
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                Identifier.withDefaultNamespace("boss_bar/" + color + "_progress"), 182, 5, 0, 0, x, y, fill, 5);
        }
        // 超级疾跑状态提示（赏金模式，开启中）
        if (bounty && ManhuntClientState.isSprinting()) {
            String sprint = "§b超级疾跑中 §7(饥饿快速消耗)";
            g.text(mc.font, sprint, (g.guiWidth() - mc.font.width(sprint)) / 2, y + 8, 0xFFFFFFFF, true);
        }
    }

    /** 第 rewardsIndex 档阈值：经典=士气表；赏金=按猎人数三套赏金表（与 BountyManager 一致）。 */
    private static int threshold(int rewardsIndex, int hunterCount) {
        int[] t;
        int step;
        if (ManhuntClientState.isBountyMode()) {
            t = hunterCount <= 1 ? com.example.manhunt.GameConfig.BOUNTY_TIERS_1H
                : hunterCount == 2 ? com.example.manhunt.GameConfig.BOUNTY_TIERS_2H
                : com.example.manhunt.GameConfig.BOUNTY_TIERS_3P;
            step = com.example.manhunt.GameConfig.BOUNTY_TIER_STEP_AFTER;
        } else {
            t = com.example.manhunt.GameConfig.MORALE_THRESHOLDS;
            step = com.example.manhunt.GameConfig.MORALE_STEP_AFTER_LAST;
        }
        return rewardsIndex < t.length
            ? t[rewardsIndex]
            : t[t.length - 1] + step * (rewardsIndex - t.length + 1);
    }

    /**
     * 罗盘边框层：逃生者=检查点罗盘（近距金脉冲），猎人=追踪罗盘（偏航红脉冲/近距金脉冲）。
     * （旧"技能槽边框"随技能栏改版移除——技能栏由 Alt 热栏替换层与背包覆盖层呈现。）
     */
    private static void renderSkillSlotBorder(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) {
            return;
        }
        if (ManhuntClientState.isRunner()) {
            renderCompassBorder(g, mc, delta, com.example.manhunt.item.ManhuntItems.CHECKPOINT_COMPASS.get());
        } else {
            renderCompassBorder(g, mc, delta, com.example.manhunt.item.ManhuntItems.TRACKING_COMPASS.get());
        }
    }

    /** 罗盘偏航计时：方向持续偏离罗盘指向的起点（-1 = 未偏离）。 */
    private static long compassDeviateSince = -1;

    /**
     * 罗盘槽位边框：
     * <ul>
     *   <li>玩家朝向明显偏离罗盘指向（&gt;{@code COMPASS_DEVIATION_ANGLE}°）持续 5 秒 → 红色脉冲，修正方向解除；</li>
     *   <li>距离目标（猎人=锁定逃生者 / 逃生者=当前检查点）&lt;50 格 → 金色脉冲；</li>
     *   <li>其余：金色静态边框。</li>
     * </ul>
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
            boolean deviated = false;
            var tracker = stack.get(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER);
            if (tracker != null && tracker.target().isPresent()
                    && tracker.target().get().dimension() == mc.player.level().dimension()) {
                var tpos = tracker.target().get().pos();
                double dx = tpos.getX() + 0.5 - mc.player.getX();
                double dz = tpos.getZ() + 0.5 - mc.player.getZ();
                double d2 = dx * dx + dz * dz;
                close = d2 < 50.0 * 50.0;
                // MC 偏航角：0=+Z，90=-X → 目标方位 yaw = atan2(-dx, dz)
                double bearing = Math.toDegrees(Math.atan2(-dx, dz));
                double diff = mc.player.getYRot() - bearing;
                diff = ((diff + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
                deviated = Math.abs(diff) > com.example.manhunt.GameConfig.COMPASS_DEVIATION_ANGLE;
            }
            // 偏航计时（任一罗盘偏离即计时）
            if (deviated) {
                if (compassDeviateSince < 0) {
                    compassDeviateSince = now;
                }
            } else {
                compassDeviateSince = -1;
            }
            boolean deviatingLong = compassDeviateSince >= 0
                && now - compassDeviateSince >= com.example.manhunt.GameConfig.COMPASS_DEVIATION_TICKS;
            int col;
            if (deviatingLong) {
                // 红色脉冲：方向偏移过久
                col = withAlpha(0xFFE33B3B, 0.55F + 0.45F * (float) Math.abs(Math.sin(t * 0.5)));
            } else if (close) {
                // 金色脉冲：接近目标
                col = withAlpha(0xFFFFC844, 0.55F + 0.45F * (float) Math.abs(Math.sin(t * 0.5)));
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
            (payload, ctx) -> ManhuntClientState.update(payload.participant(), payload.runner(),
                payload.skillReady(), payload.morale(), payload.moraleRewards(),
                payload.skillIds(), payload.skillActive(), payload.escapePhase(),
                payload.bountyMode(), payload.sprinting(),
                payload.hunters(), payload.respawnSeconds()));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientRollManager.tick();
        ClientSkillBar.tick();
        // 超级疾跑开关（按一下切换）
        Minecraft mc = Minecraft.getInstance();
        while (SPRINT_KEY.consumeClick()) {
            if (mc.player != null && mc.level != null && mc.gui.screen() == null) {
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                    new com.example.manhunt.net.SprintTogglePayload(!ManhuntClientState.isSprinting()));
            }
        }
    }

    // ==================== 领取模式输入 ====================

    @SubscribeEvent
    public static void onMouseScrolling(InputEvent.MouseScrollingEvent event) {
        // Alt 技能栏激活时滚轮切换技能（优先于资源抽奖）
        if (ClientSkillBar.onMouseScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
            return;
        }
        if (ClientRollManager.onMouseScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (ClientRollManager.onMouseButton(event.getButton(), event.getAction())) {
            event.setCanceled(true);
            return;
        }
        // Alt 技能栏：右键释放/左键查看详情/脱离等（激活时接管鼠标）
        if (ClientSkillBar.onMouseButton(event.getButton(), event.getAction())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        ClientSkillBar.onKey(event.getKey(), event.getAction());
        int key = event.getKey();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
                || key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT
                || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            ClientRollManager.onKey(key, event.getAction());
        }
    }
}
