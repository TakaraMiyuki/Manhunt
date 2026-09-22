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
        // 有卡且不在冷却：金色脉冲；冷却中或技能库为空：静态金框
        boolean cooling = hasCard && mc.player.getCooldowns().isOnCooldown(slot);
        float alpha;
        if (hasCard && !cooling) {
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
            (payload, ctx) -> ManhuntClientState.update(payload.participant(), payload.runner()));
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
