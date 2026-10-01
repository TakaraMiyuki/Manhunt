package com.example.manhunt.client;

import java.util.List;

import com.example.manhunt.GameConfig;
import com.example.manhunt.ManhuntMod;
import com.example.manhunt.net.SkillBarEditPayload;
import com.example.manhunt.net.SkillBarMovePayload;
import com.example.manhunt.net.SkillSelectPayload;
import com.example.manhunt.net.SkillUsePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.event.ScreenEvent;

import org.lwjgl.glfw.GLFW;

/**
 * Alt 技能栏：按住左 Alt 时玩家热栏临时替换为 9 格技能栏（隐藏快捷栏的化身）。
 * 交互与原版快捷栏一致——滚轮/数字键切换选中格，右键释放选中技能，按住左键查看技能详情。
 * 背包界面下方常驻显示暗金边框技能栏：可调整顺序；仅调试/未开局可放入/取出。
 */
public final class ClientSkillBar {
    private ClientSkillBar() {}

    /** Alt 技能栏是否激活（替换原版热栏中）。 */
    private static boolean active;
    /** 激活时冻结的原版选中槽（防止数字键/滚轮漂移真实快捷栏）。 */
    private static int savedSelected = -1;
    /** 按住左键 = 查看详情。 */
    private static boolean viewingDetails;
    /** 背包编辑：已点选的源格（-1 = 无）。 */
    private static int editPick = -1;

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Alt 技能栏是否激活（热栏替换态）。 */
    public static boolean isActive() {
        return active;
    }

    /** 是否处于查看详情态。 */
    public static boolean isViewingDetails() {
        return viewingDetails && active;
    }

    /** Alt 是否按住且可激活（参与逃生者、无界面）。 */
    private static boolean wants(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.gui.screen() == null
            && ManhuntClientState.isParticipant() && ManhuntClientState.isRunner()
            && ManhuntClient.SKILL_BAR_KEY.isDown();
    }

    /** 每刻：维护激活态 + 冻结原版选中槽。 */
    public static void tick() {
        Minecraft mc = mc();
        if (!active && wants(mc)) {
            active = true;
            viewingDetails = false;
            savedSelected = mc.player.getInventory().getSelectedSlot();
            uiSound(0.8F, 0.4F);
        } else if (active && !wants(mc)) {
            deactivate(mc);
        }
        // 冻结原版选中槽：技能模式下的滚轮/数字键只作用于技能栏
        if (active && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(savedSelected);
        }
    }

    private static void deactivate(Minecraft mc) {
        active = false;
        viewingDetails = false;
        editPick = -1;
        if (mc.player != null && savedSelected >= 0) {
            mc.player.getInventory().setSelectedSlot(savedSelected);
        }
        uiSound(0.6F, 0.3F);
    }

    // ==================== HUD 渲染（热栏替换） ====================

    /** 原版热栏层取消（游戏总线）。 */
    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.HOTBAR) && active) {
            event.setCanceled(true);
        }
    }

    /** 技能栏热栏渲染（HUD 层 manhunt:skill_hotbar，原版几何 + 暗金边框）。 */
    public static void renderHotbar(net.minecraft.client.gui.GuiGraphicsExtractor g,
                                    net.minecraft.client.DeltaTracker delta) {
        if (!active) {
            return;
        }
        Minecraft mc = mc();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        int x0 = w / 2 - 91;
        int y = h - 22;
        float partial = delta.getGameTimeDeltaPartialTick(true);

        // 原版热栏背景与选中框（同几何）
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            net.minecraft.resources.Identifier.withDefaultNamespace("hud/hotbar"), x0, y, 182, 22);
        int selected = Math.floorMod(ManhuntClientState.skillActive(), GameConfig.SKILL_BAR_SLOTS);
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            net.minecraft.resources.Identifier.withDefaultNamespace("hud/hotbar_selection"),
            x0 - 1 + selected * 20, y - 1, 24, 23);

        // 未选槽微暗
        for (int i = 0; i < GameConfig.SKILL_BAR_SLOTS; i++) {
            if (i != selected) {
                int sx = w / 2 - 90 + i * 20 + 2;
                g.fill(sx, h - 19, sx + 16, h - 3, 0x50000000);
            }
        }

        // 卡面（含原版冷却灰罩/数量）
        List<ItemStack> stacks = ManhuntClientState.skillStacks();
        for (int i = 0; i < GameConfig.SKILL_BAR_SLOTS; i++) {
            int sx = w / 2 - 90 + i * 20 + 2;
            ItemStack stack = i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                g.item(stack, sx, h - 19);
                g.itemDecorations(mc.font, stack, sx, h - 19);
            }
        }

        // 暗金色边框
        int frame = 0xFFC89B3C;
        int dark = 0xFF6B5316;
        g.fill(x0 - 2, y - 2, x0 + 184, y, dark);
        g.fill(x0 - 2, y + 22, x0 + 184, y + 24, dark);
        g.fill(x0 - 2, y, x0, y + 22, dark);
        g.fill(x0 + 184, y, x0 + 186, y + 22, dark);
        g.fill(x0 - 1, y - 1, x0 + 183, y, frame);
        g.fill(x0 - 1, y + 22, x0 + 183, y + 23, frame);
        g.fill(x0 - 1, y, x0, y + 22, frame);
        g.fill(x0 + 183, y, x0 + 184, y + 22, frame);

        // 标题提示
        String title = "§6技能栏 §7(右键释放选中技能)";
        g.text(mc.font, title, (w - mc.font.width(title)) / 2, y - 26, 0xFFFFFFFF, true);

        // 按住左键：详情面板
        if (viewingDetails && !stacks.isEmpty() && selected < stacks.size()) {
            ItemStack sel = stacks.get(selected);
            if (!sel.isEmpty()) {
                renderDetails(g, mc, sel, h);
            }
        }
    }

    /** 详情面板：卡名（品级色）+ 概述 + 全文 + 冷却。 */
    private static void renderDetails(net.minecraft.client.gui.GuiGraphicsExtractor g,
                                      Minecraft mc, ItemStack card, int h) {
        if (!(card.getItem() instanceof com.example.skillcards.item.SkillCardItem cardItem)) {
            return;
        }
        var cardDef = cardItem.card();
        String base = card.getItem().getDescriptionId();
        String name = "§f" + card.getHoverName().getString();
        String brief = "§b" + Component.translatable(base + ".brief").getString();
        String desc = "§7" + Component.translatable(base + ".desc").getString();
        float percent = player() != null
            ? player().getCooldowns().getCooldownPercent(card, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true))
            : 0F;
        String cd = percent > 0F ? "§c冷却中 " + String.format("%.1f", percent * cardDef.cooldownTicks() / 20F) + "s" : "§a就绪";

        int maxW = Math.max(mc.font.width(desc), Math.max(mc.font.width(brief), mc.font.width(name)));
        maxW = Math.max(maxW, mc.font.width(cd));
        int x0 = (g.guiWidth() - maxW) / 2 - 6;
        int y0 = h - 92;
        g.fill(x0 - 4, y0 - 4, x0 + maxW + 4, y0 + 42, 0xC0101010);
        g.text(mc.font, name, x0, y0, 0xFFFFFFFF, true);
        g.text(mc.font, brief, x0, y0 + 11, 0xFFFFFFFF, true);
        g.text(mc.font, desc, x0, y0 + 22, 0xFFFFFFFF, true);
        g.text(mc.font, cd, x0, y0 + 33, 0xFFFFFFFF, true);
    }

    private static Player player() {
        return mc().player;
    }

    // ==================== 输入 ====================

    private static void select(int index) {
        index = Math.floorMod(index, GameConfig.SKILL_BAR_SLOTS);
        ManhuntClientState.selectLocal(index);
        ClientPacketDistributor.sendToServer(new SkillSelectPayload(index));
        uiSound(1.4F, 0.2F);
    }

    /** 鼠标按键（游戏总线，在资源抽奖处理之前调用）。返回 true 表示已消费。 */
    public static boolean onMouseButton(int button, int action) {
        if (!active) {
            return false;
        }
        if (action == GLFW.GLFW_PRESS) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                ClientPacketDistributor.sendToServer(new SkillUsePayload());
                return true; // 技能接管右键
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                viewingDetails = true;
                return true; // 拦截攻击，转为查看详情
            }
        } else if (action == GLFW.GLFW_RELEASE && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            viewingDetails = false;
            return true;
        }
        return false;
    }

    /** 滚轮（游戏总线）。返回 true 表示已消费。 */
    public static boolean onMouseScroll(double deltaY) {
        if (!active) {
            return false;
        }
        select(ManhuntClientState.skillActive() + (deltaY < 0 ? 1 : -1));
        return true;
    }

    /** 数字键 1-9 直接选中（InputEvent.Key 不可取消，原版选中槽由 tick 冻结兜底）。 */
    public static void onKey(int key, int action) {
        if (!active || action != GLFW.GLFW_PRESS) {
            return;
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            select(key - GLFW.GLFW_KEY_1);
        }
    }

    private static void uiSound(float pitch, float volume) {
        mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
    }

    // ==================== 背包界面技能栏 ====================

    /** 背包界面技能栏几何：背包热栏行下方一行。 */
    private static int[] invRowGeometry(InventoryScreen screen) {
        int left = screen.getLeftPos();
        int top = screen.getTopPos();
        int x0 = left + 7;
        int y = top + 168; // 原版背包热栏行(top+142)+26
        return new int[]{x0, y};
    }

    private static boolean inInvRow(int mouseX, int mouseY, int[] geo) {
        return mouseX >= geo[0] && mouseX < geo[0] + 9 * 18 + 2
            && mouseY >= geo[1] && mouseY < geo[1] + 18;
    }

    private static int invSlotAt(int mouseX, int mouseY, int[] geo) {
        return Math.min(8, Math.max(0, (mouseX - geo[0] - 1) / 18));
    }

    /** 背包界面渲染（游戏总线 ScreenEvent.Render.Post）。 */
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)
                || !ManhuntClientState.isParticipant() || !ManhuntClientState.isRunner()
                || !com.example.manhunt.cards.SkillCardsBridge.available()) {
            return;
        }
        int[] geo = invRowGeometry(screen);
        int x0 = geo[0];
        int y = geo[1];
        List<ItemStack> stacks = ManhuntClientState.skillStacks();

        // 槽位底 + 物品
        for (int i = 0; i < 9; i++) {
            int sx = x0 + 1 + i * 18;
            event.getGuiGraphics().fill(sx - 1, y - 1, sx + 17, y + 17, 0xFF1A1408);
            ItemStack stack = i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                event.getGuiGraphics().item(stack, sx, y);
                event.getGuiGraphics().itemDecorations(mc().font, stack, sx, y);
            }
        }
        // 暗金边框
        int frame = 0xFFC89B3C;
        int dark = 0xFF6B5316;
        event.getGuiGraphics().fill(x0 - 2, y - 2, x0 + 9 * 18 + 1, y, dark);
        event.getGuiGraphics().fill(x0 - 2, y + 17, x0 + 9 * 18 + 1, y + 19, dark);
        event.getGuiGraphics().fill(x0 - 2, y, x0, y + 17, dark);
        event.getGuiGraphics().fill(x0 + 9 * 18, y, x0 + 9 * 18 + 1, y + 17, dark);
        event.getGuiGraphics().fill(x0 - 1, y - 1, x0 + 9 * 18, y, frame);
        event.getGuiGraphics().fill(x0 - 1, y + 17, x0 + 9 * 18, y + 18, frame);
        event.getGuiGraphics().fill(x0 - 1, y, x0, y + 17, frame);
        event.getGuiGraphics().fill(x0 + 9 * 18, y, x0 + 9 * 18 + 1, y + 17, frame);

        // 选中高亮
        int selected = Math.floorMod(ManhuntClientState.skillActive(), 9);
        int sx = x0 + 1 + selected * 18;
        event.getGuiGraphics().outline(sx - 1, y - 1, 18, 18, 0xFFFFD700);

        // 编辑态视觉：已点选源格白色框
        if (editPick >= 0) {
            int px = x0 + 1 + editPick * 18;
            event.getGuiGraphics().outline(px - 1, y - 1, 18, 18, 0xFFFFFFFF);
        }

        // 悬停高亮 + 卡名
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        int hovered = -1;
        if (inInvRow(mouseX, mouseY, geo)) {
            hovered = invSlotAt(mouseX, mouseY, geo);
            event.getGuiGraphics().outline(x0 + 1 + hovered * 18, y, 17, 17, 0xFFFFD700);
            ItemStack stack = hovered < stacks.size() ? stacks.get(hovered) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                String hover = "§f" + stack.getHoverName().getString();
                event.getGuiGraphics().text(mc().font, hover, mouseX + 8, mouseY - 8, 0xFFFFFFFF);
            }
        }
        // 已点选源格：白色常亮框 + 卡牌跟随鼠标（拖动观感）
        if (editPick >= 0) {
            event.getGuiGraphics().outline(x0 + 1 + editPick * 18, y, 17, 17, 0xFFFFFFFF);
            ItemStack picked = editPick < stacks.size() ? stacks.get(editPick) : ItemStack.EMPTY;
            if (!picked.isEmpty()) {
                event.getGuiGraphics().item(picked, mouseX - 8, mouseY - 8);
            }
        }
    }

    /** 背包界面点击（游戏总线 ScreenEvent.MouseButtonPressed.Pre）。返回 true 表示已消费。 */
    public static boolean onScreenClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)
                || !ManhuntClientState.isParticipant() || !ManhuntClientState.isRunner()
                || !com.example.manhunt.cards.SkillCardsBridge.available()
                || event.getButton() != 0) {
            return false;
        }
        int[] geo = invRowGeometry(screen);
        if (!inInvRow((int) event.getMouseX(), (int) event.getMouseY(), geo)) {
            return false;
        }
        int slot = invSlotAt((int) event.getMouseX(), (int) event.getMouseY(), geo);
        boolean running = com.example.manhunt.game.ManhuntGame.isRunning();
        if (running) {
            // 对局中：仅调整顺序（两次点击移动/交换，不可取出）
            if (editPick < 0) {
                editPick = slot;
            } else {
                ClientPacketDistributor.sendToServer(new SkillBarMovePayload(editPick, slot));
                editPick = -1;
            }
        } else {
            // 调试/未开局：与鼠标携带物品自由交换（服务器同步处理 carried）
            var carried = screen.getMenu().getCarried();
            ClientPacketDistributor.sendToServer(new SkillBarEditPayload(slot, carried));
            editPick = -1;
        }
        uiSound(1.1F, 0.25F);
        event.setCanceled(true);
        return true;
    }
}
