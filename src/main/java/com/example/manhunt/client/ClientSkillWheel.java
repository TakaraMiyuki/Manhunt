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

    /** 技能选择条：横排展示在经验条上方，背景模糊，鼠标滑动引导选择，松开左键选定。 */
    public static final class SkillWheelScreen extends Screen {
        private static final int CARD_ICON = 24;
        private static final int CARD_GAP = 4;

        private final List<ItemStack> cards;
        private final int activeIndex;
        private int selected;

        SkillWheelScreen(List<ItemStack> cards, int activeIndex) {
            super(Component.literal("技能选择"));
            this.cards = cards;
            this.activeIndex = activeIndex >= 0 && activeIndex < cards.size() ? activeIndex : 0;
            this.selected = this.activeIndex;
        }

        private int rowCount() {
            return cards.size();
        }

        /** 卡牌行几何：横向居中，位于经验条上方。 */
        private int rowWidth() {
            return rowCount() * (CARD_ICON + CARD_GAP) - CARD_GAP + 8;
        }

        private int rowLeft() {
            return (width - rowWidth()) / 2;
        }

        private int rowTop() {
            return height - 59; // 经验条上方
        }

        private void updateSelection(double mouseX) {
            int rel = (int) mouseX - rowLeft() - 4;
            if (rel < 0) {
                rel = 0;
            }
            int cell = CARD_ICON + CARD_GAP;
            selected = Math.min(rowCount() - 1, rel / cell);
            if (selected < 0) {
                selected = 0;
            }
        }

        @Override
        public void mouseMoved(double x, double y) {
            updateSelection(x);
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
            // 与原版界面一致的背景模糊
            this.extractBlurredBackground(g);
            updateSelection(mouseX);

            int left = rowLeft();
            int top = rowTop();
            long now = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;

            // 选中卡信息：显示在卡牌行上方
            ItemStack card = cards.get(selected);
            String brief = Component.translatable(
                card.getItem().getDescriptionId() + ".brief").getString();
            String name = "§f" + card.getHoverName().getString()
                + " §7(" + (selected + 1) + "/" + rowCount() + ")";
            g.text(font, name, (width - font.width(name)) / 2, top - 24, 0xFFFFFFFF, true);
            g.text(font, "§b" + brief, (width - font.width("§b" + brief)) / 2, top - 14, 0xFFFFFFFF, true);

            // 卡牌行
            for (int i = 0; i < rowCount(); i++) {
                int ix = left + 4 + i * (CARD_ICON + CARD_GAP);
                g.fill(ix - 1, top - 1, ix + CARD_ICON + 1, top + CARD_ICON + 1, 0xFF1E1E1E);
                // 16×16 物品在 24×24 框内居中
                g.item(cards.get(i), ix + (CARD_ICON - 16) / 2, top + (CARD_ICON - 16) / 2);
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
                g.fill(ix - 2, top - 2, ix + CARD_ICON + 2, top, frame);
                g.fill(ix - 2, top + CARD_ICON, ix + CARD_ICON + 2, top + CARD_ICON + 2, frame);
                g.fill(ix - 2, top, ix, top + CARD_ICON, frame);
                g.fill(ix + CARD_ICON, top, ix + CARD_ICON + 2, top + CARD_ICON, frame);
                if (i == activeIndex) {
                    g.fill(ix + CARD_ICON / 2 - 1, top + CARD_ICON + 4,
                        ix + CARD_ICON / 2 + 1, top + CARD_ICON + 6, 0xFF3CE13C);
                }
            }

            String hint = "§7滑动鼠标选择，松开左键选定，Esc 取消";
            g.text(font, hint, (width - font.width(hint)) / 2, top + CARD_ICON + 10, 0xFFB8B8B8, true);
        }

        private int withAlpha(int argb, float alpha) {
            int a = (int) ((argb >>> 24) * alpha);
            return (a << 24) | (argb & 0xFFFFFF);
        }
    }
}
