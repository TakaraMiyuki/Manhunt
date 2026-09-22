package com.example.manhunt.client;

import java.util.ArrayList;
import java.util.List;

import com.example.manhunt.GameConfig;
import com.example.manhunt.net.ClaimRewardPayload;
import com.example.manhunt.net.LootRollPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

/**
 * 客户端抽奖 UI：老虎机动画 → 领取模式。
 * 领取模式：滚轮/←→ 选择，中键/回车 领取选中项，右键 一键收取全部；
 * 面板停留在屏幕上方（下移避让 bossbar），不阻挡视野与操作。
 */
public final class ClientRollManager {
    private ClientRollManager() {}

    /** 滚动展示用的装饰物品（客户端本地，仅动画表现）。惰性初始化——类加载早于组件绑定。 */
    private static List<ItemStack> decoys;

    private static List<ItemStack> decoys() {
        if (decoys == null) {
            decoys = List.of(
                new ItemStack(net.minecraft.world.item.Items.COBBLESTONE),
                new ItemStack(net.minecraft.world.item.Items.IRON_INGOT),
                new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT),
                new ItemStack(net.minecraft.world.item.Items.DIAMOND),
                new ItemStack(net.minecraft.world.item.Items.COAL),
                new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL),
                new ItemStack(net.minecraft.world.item.Items.BREAD),
                new ItemStack(net.minecraft.world.item.Items.EMERALD),
                new ItemStack(net.minecraft.world.item.Items.ARROW),
                new ItemStack(net.minecraft.world.item.Items.LEATHER));
        }
        return decoys;
    }

    private static final int INTRO = GameConfig.ROLL_ANIMATION_INTRO_TICKS;
    private static final int DURATION = GameConfig.ROLL_ANIMATION_TICKS;
    private static final int LOCK_STEP = 6;
    private static final int LAST_LOCK_MARGIN = 8;
    /** 全部定格后进入领取模式的延迟（刻）。 */
    private static final int CLAIM_DELAY = 10;

    static final class Session {
        final int type;
        final int accentColor;
        final long startTick;
        List<ItemStack> items;
        boolean claimMode;
        int selected;
        /** 已标记（中键）待领取的物品索引。 */
        final java.util.Set<Integer> marked = new java.util.HashSet<>();
        long nextScrollSound;
        int lockedCount;

        Session(int type, List<ItemStack> items, int accentColor, long startTick) {
            this.type = type;
            this.items = items;
            this.accentColor = accentColor;
            this.startTick = startTick;
            this.nextScrollSound = startTick + INTRO;
        }

        int slotCount() {
            return items.size();
        }

        /** 第 i 个槽的定格时刻。 */
        long lockTime(int i) {
            int lastLock = DURATION - LAST_LOCK_MARGIN;
            int firstLock = lastLock - (slotCount() - 1) * LOCK_STEP;
            return startTick + Math.min(firstLock + i * LOCK_STEP, lastLock);
        }

        long claimStartTime() {
            return slotCount() > 0 ? lockTime(slotCount() - 1) + CLAIM_DELAY : Long.MAX_VALUE;
        }
    }

    /** 主会话：资源/超级抽奖（动画 → 领取）。 */
    private static Session current;
    /** 技能卡动画队列（单卡翻转，自动结束，无领取交互）。 */
    private static final List<Session> cardQueue = new ArrayList<>();
    private static final RandomSource RNG = RandomSource.create();

    // ==================== 生命周期 ====================

    public static void start(LootRollPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (payload.mode() == LootRollPayload.MODE_REFRESH) {
            // 领取刷新：原位更新剩余物品；空列表关闭会话
            if (current != null) {
                if (payload.items().isEmpty()) {
                    current = null;
                } else {
                    current.items = new ArrayList<>(payload.items());
                    current.claimMode = true;
                    current.selected = Mth.clamp(current.selected, 0, current.items.size() - 1);
                }
            }
            return;
        }
        if (payload.items().isEmpty()) {
            return;
        }
        if (payload.rollType() == LootRollPayload.TYPE_CARD) {
            cardQueue.add(new Session(payload.rollType(), new ArrayList<>(payload.items()),
                payload.accentColor(), mc.level.getGameTime()));
            uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.35F, 0.35F);
            return;
        }
        current = new Session(payload.rollType(), new ArrayList<>(payload.items()),
            payload.accentColor(), mc.level.getGameTime());
        uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.35F, 0.35F);
    }

    /** 客户端 tick：推进音效节点、领取模式切换、清理过期动画。 */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            current = null;
            cardQueue.clear();
            return;
        }
        long now = mc.level.getGameTime();
        if (current != null) {
            advance(now, current);
        }
        cardQueue.removeIf(card -> now - card.startTick > DURATION);
    }

    private static void advance(long now, Session roll) {
        // 滚动 tick 音
        long firstLock = roll.slotCount() > 0 ? roll.lockTime(0) : Long.MAX_VALUE;
        while (roll.nextScrollSound <= now && roll.nextScrollSound < firstLock) {
            uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.5F + RNG.nextFloat() * 0.6F, 0.18F);
            roll.nextScrollSound = Math.max(now, roll.nextScrollSound) + 3 + RNG.nextInt(2);
        }
        // 定格音（按已定格数触发）
        while (roll.lockedCount < roll.slotCount() && now >= roll.lockTime(roll.lockedCount)) {
            uiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, lockPitch(roll, roll.lockedCount), 0.35F);
            roll.lockedCount++;
        }
        // 全部定格 → 领取模式
        if (!roll.claimMode && now >= roll.claimStartTime()) {
            roll.claimMode = true;
            uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 0.3F);
        }
    }

    private static float lockPitch(Session roll, int index) {
        if (roll.type == LootRollPayload.TYPE_CARD) {
            return 0.9F + 0.25F * index;
        }
        if (roll.type == LootRollPayload.TYPE_SUPER) {
            return 1.1F + 0.1F * index;
        }
        return 1.0F + 0.12F * index;
    }

    // ==================== 输入（领取模式） ====================

    public static boolean hasClaimSession() {
        return current != null && current.claimMode && !current.items.isEmpty();
    }

    private static void sendClaim(List<Integer> indices) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
            new ClaimRewardPayload(indices));
    }

    /** 中键/回车：标记或取消标记当前选中物品。 */
    private static void toggleMark(Session roll) {
        if (roll.marked.contains(roll.selected)) {
            roll.marked.remove(roll.selected);
            uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.25F);
        } else {
            roll.marked.add(roll.selected);
            uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.3F);
        }
    }

    /** 滚轮选择。返回 true 表示已消费。 */
    public static boolean onMouseScroll(double deltaY) {
        if (!hasClaimSession()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null || current.items.isEmpty()) {
            return false;
        }
        int dir = deltaY < 0 ? 1 : -1;
        current.selected = Math.floorMod(current.selected + dir, current.items.size());
        uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F, 0.15F);
        return true;
    }

    /** 鼠标按键。返回 true 表示已消费。 */
    public static boolean onMouseButton(int button, int action) {
        if (!hasClaimSession() || action != GLFW.GLFW_PRESS) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            // 右键：领取全部标记物品并关闭界面（未标记则领取当前选中项）
            List<Integer> claim = current.marked.isEmpty()
                ? List.of(current.selected)
                : new ArrayList<>(current.marked);
            sendClaim(claim);
            current = null;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            // 中键：标记/取消标记当前选中物品
            toggleMark(current);
            return true;
        }
        return false;
    }

    /** 键盘按键（在原版处理后调用）。返回 true 表示已消费。 */
    public static boolean onKey(int key, int action) {
        if (!hasClaimSession() || action != GLFW.GLFW_PRESS) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            // 回车：标记/取消标记当前选中物品；若回车刚打开了聊天栏则关掉
            if (mc.gui.screen() != null) {
                if (mc.gui.screen() instanceof ChatScreen) {
                    mc.gui.setScreen(null);
                } else {
                    return false; // 其他界面（指令输入等）不接管
                }
            }
            toggleMark(current);
            return true;
        }
        if (mc.gui.screen() != null) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            current.selected = Math.floorMod(current.selected - 1, current.items.size());
            uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F, 0.15F);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            current.selected = Math.floorMod(current.selected + 1, current.items.size());
            uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F, 0.15F);
            return true;
        }
        return false;
    }

    // ==================== 渲染 ====================

    /** HUD 层渲染入口（GuiLayer）。 */
    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gui.hud.isHidden()) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        double nowD = now + partial;
        int y = GameConfig.ROLL_UI_TOP_OFFSET;
        if (current != null) {
            double elapsed = nowD - current.startTick;
            if (elapsed >= 0) {
                y = renderRoll(g, mc, current, elapsed, y);
            }
        }
        for (Session card : cardQueue) {
            double elapsed = nowD - card.startTick;
            if (elapsed >= 0 && elapsed <= DURATION) {
                renderCard(g, mc, card, elapsed, y);
            }
        }
    }

    private static int renderRoll(GuiGraphicsExtractor g, Minecraft mc, Session roll, double elapsed, int y) {
        // 动画结束即进入领取模式，面板保持可见（无淡出），直至领取完成或被新一轮替换
        float alpha = roll.claimMode ? 1.0F : Mth.clamp((float) (elapsed / INTRO), 0.0F, 1.0F);
        if (alpha <= 0.01F) {
            return y;
        }
        int slots = roll.slotCount();
        int icon = 16;
        int gap = 2;
        int pad = 3;
        int width = slots * (icon + gap) - gap + pad * 2;
        int x0 = (g.guiWidth() - width) / 2;
        int barY = y + 10;

        int border = withAlpha(roll.accentColor != 0 ? roll.accentColor : 0xFF8B8B8B, alpha);
        g.fillGradient(x0 - 1, barY - 1, x0 + width + 1, barY + icon + 1,
            withAlpha(0xB4000000, alpha), withAlpha(0xB4000000, alpha));
        g.fill(x0 - 2, barY - 2, x0 + width + 2, barY - 1, border);
        g.fill(x0 - 2, barY + icon + 1, x0 + width + 2, barY + icon + 2, border);
        g.fill(x0 - 2, barY - 1, x0 - 1, barY + icon + 1, border);
        g.fill(x0 + width + 1, barY - 1, x0 + width + 2, barY + icon + 1, border);

        String title = roll.type == LootRollPayload.TYPE_SUPER ? "超级抽奖" : "资源抽奖";
        int textX = (g.guiWidth() - mc.font.width(title)) / 2;
        g.text(mc.font, title, textX, barY - 9, withAlpha(0xFFE8C844, alpha), true);

        float pulse = 0.6F + 0.4F * (float) Math.abs(Math.sin(elapsed * 0.3));
        for (int i = 0; i < slots; i++) {
            int sx = x0 + pad + i * (icon + gap);
            g.fill(sx - 1, barY - 1, sx + icon + 1, barY + icon + 1, withAlpha(0xFF1E1E1E, alpha));
            if (roll.claimMode) {
                // 已标记：金色常驻边框；选中：白色细框游标；其余：微暗
                if (roll.marked.contains(i)) {
                    int mark = withAlpha(0xFFFFC844, alpha);
                    g.fill(sx - 2, barY - 2, sx + icon + 2, barY - 1, mark);
                    g.fill(sx - 2, barY + icon + 1, sx + icon + 2, barY + icon + 2, mark);
                    g.fill(sx - 2, barY - 1, sx - 1, barY + icon + 1, mark);
                    g.fill(sx + icon + 1, barY - 1, sx + icon + 2, barY + icon + 1, mark);
                }
                if (i == roll.selected) {
                    int glow = withAlpha(mixAlpha(0xFFFFFF00, pulse), alpha);
                    g.fill(sx - 1, barY - 1, sx + icon + 1, barY, glow);
                    g.fill(sx - 1, barY + icon, sx + icon + 1, barY + icon + 1, glow);
                    g.fill(sx - 1, barY, sx, barY + icon, glow);
                    g.fill(sx + icon, barY, sx + icon + 1, barY + icon, glow);
                }
                if (!roll.marked.contains(i) && i != roll.selected) {
                    g.fill(sx, barY, sx + icon, barY + icon, withAlpha(0x50000000, alpha));
                }
                g.item(roll.items.get(i), sx, barY);
            } else if (elapsed >= roll.lockTime(i) - roll.startTick) {
                // 已定格：缩放弹出动画
                double since = elapsed - (roll.lockTime(i) - roll.startTick);
                float pop = since < 6 ? (float) (1.0 + 0.3 * (1.0 - since / 6.0)) : 1.0F;
                drawItemCentered(g, roll.items.get(i), sx + icon / 2, barY + icon / 2, pop);
            } else {
                // 滚动中：装饰物品快速轮换
                List<ItemStack> pool = decoys();
                int decoy = (int) ((elapsed / 1.5) + i * 4) % pool.size();
                ItemStack d = pool.get(Math.floorMod(decoy, pool.size()));
                g.item(d, sx, barY);
            }
        }

        if (roll.claimMode) {
            // 选中物品名称（附魔书附上附魔信息便于区分，复数物品显示数量）
            Component label = selectionLabel(roll.items.get(roll.selected));
            int labelX = (g.guiWidth() - mc.font.width(label)) / 2;
            g.text(mc.font, label, labelX, barY + icon + 4, withAlpha(0xFFFFF0C0, alpha), true);

            // 操作提示：仅滚轮/中键/右键，小字号
            String hint = "§f滚轮 选择   §f中键 标记   §f右键 领取标记";
            float hintScale = 0.75F;
            int hintW = mc.font.width(hint);
            int hintX = (int) ((g.guiWidth() - hintW * hintScale) / 2);
            g.pose().pushMatrix();
            g.pose().translate(hintX, barY + icon + 14);
            g.pose().scale(hintScale, hintScale);
            g.text(mc.font, hint, 0, 0, withAlpha(0xFFB8B8B8, alpha), true);
            g.pose().popMatrix();
        }
        return y + icon + (roll.claimMode ? 30 : 16);
    }

    private static void renderCard(GuiGraphicsExtractor g, Minecraft mc, Session roll, double elapsed, int y) {
        float alpha = fadeInOut(elapsed);
        if (alpha <= 0.01F) {
            return;
        }
        ItemStack card = roll.items.get(0);
        int size = 24;
        int cx = g.guiWidth() / 2;
        int cy = y + 14;
        double flipT = Math.min(1.0, elapsed / (DURATION * 0.35));
        float scaleX = (float) Math.max(0.08, Math.abs(Math.cos(flipT * Math.PI))) * (flipT < 1.0 ? 1.6F : 1.0F);
        float glow = flipT >= 1.0 ? 0.6F + 0.4F * (float) Math.sin(elapsed * 0.35) : 0.4F;
        int glowColor = withAlpha(mixAlpha(roll.accentColor != 0 ? roll.accentColor : 0xFFFF55FF, glow), alpha);

        g.fillGradient(cx - size / 2 - 3, cy - size / 2 - 3, cx + size / 2 + 3, cy + size / 2 + 3,
            glowColor, withAlpha(0x00000000, alpha));
        g.fillGradient(cx - size / 2 - 1, cy - size / 2 - 1, cx + size / 2 + 1, cy + size / 2 + 1,
            withAlpha(0xE4201010, alpha), withAlpha(0xE4201010, alpha));

        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scaleX, 1.0F);
        g.item(card, -8, -8);
        g.pose().popMatrix();
    }

    /** 选中物品的展示名称：附魔书附附魔信息，复数物品显示数量。 */
    private static Component selectionLabel(ItemStack stack) {
        var stored = stack.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        Component label;
        if (stored != null && !stored.isEmpty() && stack.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) {
            StringBuilder sb = new StringBuilder("§e附魔书 §7(");
            boolean first = true;
            for (net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder : stored.keySet()) {
                if (!first) {
                    sb.append("、");
                }
                first = false;
                sb.append(holder.value().description().getString()).append(" ").append(stored.getLevel(holder));
            }
            sb.append("§r§e)");
            label = Component.literal(sb.toString());
        } else {
            label = stack.getHoverName().copy();
        }
        if (stack.getCount() > 1) {
            label = label.copy().append("§r§e ×" + stack.getCount());
        }
        return label;
    }

    // ==================== 工具 ====================

    private static void drawItemCentered(GuiGraphicsExtractor g, ItemStack stack, int cx, int cy, float scale) {
        if (scale == 1.0F) {
            g.item(stack, cx - 8, cy - 8);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.item(stack, -8, -8);
        g.pose().popMatrix();
    }

    private static float fadeInOut(double elapsed) {
        float in = (float) Math.min(1.0, elapsed / INTRO);
        float out = (float) Math.min(1.0, (DURATION - elapsed) / 8.0);
        return Mth.clamp(Math.min(in, out), 0.0F, 1.0F);
    }

    private static int withAlpha(int argb, float alpha) {
        int a = (int) ((argb >>> 24) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int mixAlpha(int argb, float extraAlpha) {
        int a = (int) ((argb >>> 24) * extraAlpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** 技能槽边框使用的金色（供 ManhuntClient 复用）。 */
    static int accentGold() {
        return 0xFFFFC844;
    }

    private static void uiSound(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    public static void clear() {
        current = null;
        cardQueue.clear();
    }
}
