package com.example.manhunt.client;

import com.example.manhunt.ManhuntMod;
import com.example.manhunt.net.LootRollPayload;

import net.minecraft.client.DeltaTracker;
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
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

import org.lwjgl.glfw.GLFW;

/**
 * 客户端事件订阅：HUD 抽奖层 + 技能槽边框 + S2C 动画包 handler + 动画 tick + 领取模式输入。
 * （NeoForge 按 IModBusEvent 自动路由 mod 总线 / 游戏总线）
 */
@EventBusSubscriber(modid = ManhuntMod.MODID, value = Dist.CLIENT)
public final class ManhuntClient {
    private ManhuntClient() {}

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
        ItemStack slot = mc.player.getInventory().getItem(8);
        if (!com.example.manhunt.cards.SkillCardsBridge.available()
                || !com.example.manhunt.cards.SkillCardsBridge.isSkillCard(slot)) {
            return;
        }
        // 原版槽位物品坐标：x = guiWidth/2 - 90 + i*20 + 2，y = guiHeight - 19
        int x = g.guiWidth() / 2 - 90 + 8 * 20 + 2;
        int y = g.guiHeight() - 19;
        long now = mc.level.getGameTime();
        float pulse = 0.55F + 0.45F * (float) Math.abs(Math.sin((now + delta.getGameTimeDeltaPartialTick(false)) * 0.25));
        int col = withAlpha(ClientRollManager.accentGold(), pulse);
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
