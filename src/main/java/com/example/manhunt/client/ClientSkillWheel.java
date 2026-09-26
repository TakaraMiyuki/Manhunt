package com.example.manhunt.client;

import java.util.List;

import com.example.manhunt.GameConfig;
import com.example.manhunt.net.SkillSelectPayload;
import com.example.manhunt.net.SkillSwitchPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import org.lwjgl.glfw.GLFW;

/**
 * 技能轮盘：手持技能卡长按左键呼出，展示技能库全部卡牌，
 * 滑动鼠标选择，松开左键选定切换（Esc 取消）。短按左键仍为循环切换。
 */
public final class ClientSkillWheel {
    private ClientSkillWheel() {}

    /** 左键按下时刻（-1 = 未按下）。 */
    private static long pressTick = -1;

    /** 左键拦截：手持技能卡时长按计时（短按在松开时转为循环切换）。返回 true 表示已消费。 */
    public static boolean onMouseButton(int button, int action) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) {
            return false;
        }
        // 抽奖界面脱离状态下保持正常操作
        if (ClientRollManager.hasClaimSession() && ClientRollManager.isDetached()) {
            return false;
        }
        if (!ManhuntClientState.isParticipant() || !ManhuntClientState.isRunner()) {
            return false;
        }
        if (!com.example.manhunt.cards.SkillCardsBridge.available()
                || !com.example.manhunt.cards.SkillCardsBridge.isSkillCard(mc.player.getMainHandItem())) {
            return false;
        }
        long now = mc.level.getGameTime();
        if (action == GLFW.GLFW_PRESS) {
            pressTick = now;
            return true; // 拦截左键攻击/破坏
        }
        if (action == GLFW.GLFW_RELEASE && pressTick >= 0) {
            long held = now - pressTick;
            pressTick = -1;
            if (held < GameConfig.SKILL_WHEEL_HOLD_TICKS) {
                // 短按：循环切换下一张
                ClientPacketDistributor.sendToServer(new SkillSwitchPayload());
            }
            return true;
        }
        return false;
    }

    /** 客户端 tick：按住达到阈值时呼出轮盘。 */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (pressTick >= 0 && mc.level != null && mc.gui.screen() == null) {
            if (mc.level.getGameTime() - pressTick >= GameConfig.SKILL_WHEEL_HOLD_TICKS) {
                pressTick = -1;
                List<ItemStack> cards = ManhuntClientState.skillStacks();
                if (!cards.isEmpty()) {
                    mc.gui.setScreen(new SkillWheelScreen(cards, ManhuntClientState.skillActive()));
                }
            }
        }
    }

    public static void clear() {
        pressTick = -1;
    }

    // ==================== 轮盘界面 ====================

    public static final class SkillWheelScreen extends Screen {
        private static final int CARD_ICON = 20;
        private static final int DEAD_ZONE_SQ = 20 * 20;

        private final List<ItemStack> cards;
        private final int activeIndex;
        private int selected;
        /** 上次鼠标坐标（用于渲染期重算选中）。 */
        private double lastMouseX;
        private double lastMouseY;

        SkillWheelScreen(List<ItemStack> cards, int activeIndex) {
            super(Component.literal("技能轮盘"));
            this.cards = cards;
            this.activeIndex = activeIndex;
            this.selected = activeIndex >= 0 && activeIndex < cards.size() ? activeIndex : 0;
        }

        private int cardCount() {
            return cards.size();
        }

        private double step() {
            return 360.0 / cardCount();
        }

        private void updateSelection(int mouseX, int mouseY) {
            int cx = width / 2;
            int cy = height / 2;
            double mx = mouseX - cx;
            double my = mouseY - cy;
            if (mx * mx + my * my < DEAD_ZONE_SQ) {
                return; // 中心死区：保持当前选中
            }
            double mouseAngle = Math.toDegrees(Math.atan2(my, mx));
            selected = Math.floorMod((int) Math.round((mouseAngle + 90.0) / step()), cardCount());
        }

        @Override
        public void mouseMoved(double x, double y) {
            lastMouseX = x;
            lastMouseY = y;
            updateSelection((int) x, (int) y);
        }

        @Override
        public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                ClientPacketDistributor.sendToServer(new SkillSelectPayload(selected));
                Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.3F, 0.35F));
                onClose();
                return true;
            }
            return super.mouseReleased(event);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g,
                                       int mouseX, int mouseY, float partialTick) {
            updateSelection(mouseX, mouseY);
            int cx = width / 2;
            int cy = height / 2;
            int n = cardCount();
            int radius = Math.max(60, n * 14);
            long now = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;

            // 背景暗化
            g.fillGradient(0, 0, width, height, 0xA0000000, 0xA0000000);
            // 中心盘
            g.fill(cx - 4, cy - 4, cx + 4, cy + 4, 0xFF3A2E14);

            double step = step();
            for (int i = 0; i < n; i++) {
                double angle = Math.toRadians(-90.0 + i * step);
                int ix = cx + (int) Math.round(Math.cos(angle) * radius) - CARD_ICON / 2;
                int iy = cy + (int) Math.round(Math.sin(angle) * radius) - CARD_ICON / 2;
                g.fill(ix - 1, iy - 1, ix + CARD_ICON + 1, iy + CARD_ICON + 1, 0xFF1E1E1E);
                g.item(cards.get(i), ix, iy);
                int frame;
                if (i == selected) {
                    // 选中：金色脉冲
                    float pulse = 0.6F + 0.4F * (float) Math.abs(Math.sin((now + partialTick) * 0.3));
                    frame = withAlpha(0xFFFFC844, pulse);
                } else if (i == activeIndex) {
                    frame = 0xFF3CE13C; // 当前激活：绿框
                } else {
                    frame = 0xFF555555;
                }
                g.fill(ix - 2, iy - 2, ix + CARD_ICON + 2, iy, frame);
                g.fill(ix - 2, iy + CARD_ICON, ix + CARD_ICON + 2, iy + CARD_ICON + 2, frame);
                g.fill(ix - 2, iy, ix, iy + CARD_ICON, frame);
                g.fill(ix + CARD_ICON, iy, ix + CARD_ICON + 2, iy + CARD_ICON, frame);
                if (i == activeIndex) {
                    g.fill(ix + CARD_ICON / 2 - 1, iy + CARD_ICON + 4, ix + CARD_ICON / 2 + 1, iy + CARD_ICON + 6, 0xFF3CE13C);
                }
            }

            // 中央：选中卡信息
            ItemStack card = cards.get(selected);
            String brief = Component.translatable(
                card.getItem().getDescriptionId() + ".brief").getString();
            String name = "§f" + card.getHoverName().getString() + " §7(" + (selected + 1) + "/" + n + ")";
            g.text(font, name, cx - font.width(name) / 2, cy - radius - 24, 0xFFFFFFFF, true);
            g.text(font, "§b" + brief, cx - font.width("§b" + brief) / 2, cy + radius + 12, 0xFFFFFFFF, true);
            String hint = "§7滑动鼠标选择，松开左键选定，Esc 取消";
            g.text(font, hint, cx - font.width(hint) / 2, cy + radius + 24, 0xFFB8B8B8, true);
        }

        private int withAlpha(int argb, float alpha) {
            int a = (int) ((argb >>> 24) * alpha);
            return (a << 24) | (argb & 0xFFFFFF);
        }
    }
}
