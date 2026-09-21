package com.example.manhunt.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.example.manhunt.GameConfig;
import com.example.manhunt.net.LootRollPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * 客户端抽奖动画管理：老虎机式滚动物品，依次定格，不阻挡视野与操作。
 * 动画分为：淡入(12t) → 滚动 → 各槽依次定格(缓动+定格音) → 淡出(8t)。
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

    static final class ActiveRoll {
        final int type;
        final List<ItemStack> items;
        final int accentColor;
        final long startTick;
        long nextScrollSound;
        int lockedCount;

        ActiveRoll(int type, List<ItemStack> items, int accentColor, long startTick) {
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
    }

    private static final List<ActiveRoll> ACTIVE = new ArrayList<>();
    private static final RandomSource RNG = RandomSource.create();

    // ==================== 生命周期 ====================

    public static void start(LootRollPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || payload.items().isEmpty()) {
            return;
        }
        ACTIVE.add(new ActiveRoll(payload.rollType(), new ArrayList<>(payload.items()),
            payload.accentColor(), mc.level.getGameTime()));
        uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.35F, 0.35F);
    }

    /** 客户端 tick：推进音效节点、清理过期动画。 */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        long now = mc.level.getGameTime();
        Iterator<ActiveRoll> it = ACTIVE.iterator();
        while (it.hasNext()) {
            ActiveRoll roll = it.next();
            long elapsed = now - roll.startTick;
            if (elapsed > DURATION) {
                it.remove();
                continue;
            }
            // 滚动 tick 音
            while (elapsed >= 0 && roll.nextScrollSound <= now && roll.nextScrollSound < firstLockTime(roll)) {
                uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.5F + RNG.nextFloat() * 0.6F, 0.18F);
                roll.nextScrollSound = Math.max(now, roll.nextScrollSound) + 3 + RNG.nextInt(2);
            }
            // 定格音（按已定格数触发）
            while (roll.lockedCount < roll.slotCount() && now >= roll.lockTime(roll.lockedCount)) {
                float pitch = lockPitch(roll, roll.lockedCount);
                uiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch, 0.35F);
                roll.lockedCount++;
            }
        }
    }

    private static long firstLockTime(ActiveRoll roll) {
        return roll.slotCount() > 0 ? roll.lockTime(0) : Long.MAX_VALUE;
    }

    private static float lockPitch(ActiveRoll roll, int index) {
        if (roll.type == LootRollPayload.TYPE_CARD) {
            return 0.9F + 0.25F * index;
        }
        if (roll.type == LootRollPayload.TYPE_SUPER) {
            return 1.1F + 0.1F * index;
        }
        return 1.0F + 0.12F * index;
    }

    // ==================== 渲染 ====================

    /** HUD 层渲染入口（GuiLayer）。 */
    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gui.hud.isHidden()) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        double nowD = now + partial;
        int stackY = 10;
        for (ActiveRoll roll : ACTIVE) {
            double elapsed = nowD - roll.startTick;
            if (elapsed < 0 || elapsed > DURATION) {
                continue;
            }
            stackY = renderRoll(g, mc, roll, elapsed, stackY);
        }
    }

    private static int renderRoll(GuiGraphicsExtractor g, Minecraft mc, ActiveRoll roll, double elapsed, int y) {
        float alpha = fadeInOut(elapsed);
        if (alpha <= 0.01F) {
            return y;
        }
        if (roll.type == LootRollPayload.TYPE_CARD) {
            return renderCard(g, mc, roll, elapsed, y, alpha);
        }
        int slots = roll.slotCount();
        int icon = 16;
        int gap = 2;
        int pad = 3;
        int width = slots * (icon + gap) - gap + pad * 2;
        int x0 = (g.guiWidth() - width) / 2;
        int barY = y + 10;

        // 面板与边框
        int border = withAlpha(roll.accentColor != 0 ? roll.accentColor : 0xFF8B8B8B, alpha);
        g.fillGradient(x0 - 1, barY - 1, x0 + width + 1, barY + icon + 1,
            withAlpha(0xB4000000, alpha), withAlpha(0xB4000000, alpha));
        g.fill(x0 - 2, barY - 2, x0 + width + 2, barY - 1, border);
        g.fill(x0 - 2, barY + icon + 1, x0 + width + 2, barY + icon + 2, border);
        g.fill(x0 - 2, barY - 1, x0 - 1, barY + icon + 1, border);
        g.fill(x0 + width + 1, barY - 1, x0 + width + 2, barY + icon + 1, border);

        // 标题
        String title = roll.type == LootRollPayload.TYPE_SUPER ? "超级抽奖" : "资源抽奖";
        int textX = (g.guiWidth() - mc.font.width(title)) / 2;
        g.text(mc.font, title, textX, barY - 9, withAlpha(0xFFE8C844, alpha), true);

        for (int i = 0; i < slots; i++) {
            int sx = x0 + pad + i * (icon + gap);
            g.fill(sx - 1, barY - 1, sx + icon + 1, barY + icon + 1, withAlpha(0xFF1E1E1E, alpha));
            long lockAt = roll.lockTime(i);
            if (elapsed >= lockAt - roll.startTick) {
                // 已定格：缩放弹出动画
                double since = elapsed - (lockAt - roll.startTick);
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
        return y + icon + 16;
    }

    private static int renderCard(GuiGraphicsExtractor g, Minecraft mc, ActiveRoll roll, double elapsed, int y, float alpha) {
        ItemStack card = roll.items.get(0);
        int size = 24;
        int cx = g.guiWidth() / 2;
        int cy = y + 14;
        // 翻转进度（前 35% 时间）
        double flipT = Math.min(1.0, elapsed / (DURATION * 0.35));
        float scaleX = (float) Math.max(0.08, Math.abs(Math.cos(flipT * Math.PI))) * (flipT < 1.0 ? 1.6F : 1.0F);
        // 定格后轻微脉冲
        float glow = flipT >= 1.0 ? 0.6F + 0.4F * (float) Math.sin(elapsed * 0.35) : 0.4F;
        int glowColor = withAlpha(mixAlpha(roll.accentColor != 0 ? roll.accentColor : 0xFFFF55FF, glow), alpha);

        // 品质光晕
        g.fillGradient(cx - size / 2 - 3, cy - size / 2 - 3, cx + size / 2 + 3, cy + size / 2 + 3,
            glowColor, withAlpha(0x00000000, alpha));
        g.fillGradient(cx - size / 2 - 1, cy - size / 2 - 1, cx + size / 2 + 1, cy + size / 2 + 1,
            withAlpha(0xE4201010, alpha), withAlpha(0xE4201010, alpha));

        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scaleX, 1.0F);
        g.item(card, -8, -8);
        g.pose().popMatrix();
        return y + size + 14;
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

    private static void uiSound(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    public static boolean hasActive() {
        return !ACTIVE.isEmpty();
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
