package com.example.manhunt.client;

import com.example.manhunt.ManhuntMod;
import com.example.manhunt.net.LootRollPayload;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
     * 猎人士气条：屏幕上方的独立条状 UI（蓝条 + 档数标注），与原版 bossbar 位置错开。
     * 进度 = 距下一档士气（达到 1000 封顶后恒满）。
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
        int[] thresholds = com.example.manhunt.GameConfig.MORALE_THRESHOLDS;
        float progress;
        if (rewards >= thresholds.length) {
            progress = 1.0F;
        } else {
            int next = thresholds[rewards];
            int prev = rewards == 0 ? 0 : thresholds[rewards - 1];
            progress = net.minecraft.util.Mth.clamp((morale - prev) / (float) Math.max(1, next - prev), 0.0F, 1.0F);
        }
        int w = 182;
        int x = (g.guiWidth() - w) / 2;
        int y = 28;
        // 标注：士气值 + 档数
        String label = "§b士气 " + morale + " §7· 档 " + rewards + "/" + thresholds.length;
        g.text(mc.font, label, (g.guiWidth() - mc.font.width(label)) / 2, y - 10, 0xFFFFFFFF, true);
        // 底板 + 蓝色填充 + 细边框
        g.fill(x - 1, y - 1, x + w + 1, y + 6, 0xC0101010);
        g.fill(x, y, x + w, y + 5, 0xFF2A2A2A);
        int fill = (int) (w * progress);
        if (fill > 0) {
            g.fill(x, y, x + fill, y + 5, 0xFF0078D4);
        }
        g.fill(x - 1, y - 1, x, y + 6, 0xFF3C3C3C);
        g.fill(x + w, y - 1, x + w + 1, y + 6, 0xFF3C3C3C);
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
            return; // 仅逃生者显示固定技能槽
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
    }

    private static int withAlpha(int argb, float alpha) {
        int a = (int) ((argb >>> 24) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
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
                || key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            ClientRollManager.onKey(key, event.getAction());
        }
    }
}
