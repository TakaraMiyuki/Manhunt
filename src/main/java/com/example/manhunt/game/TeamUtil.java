package com.example.manhunt.game;

import com.example.manhunt.GameConfig;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 阵营判定、属性与增益应用。
 */
public final class TeamUtil {
    private TeamUtil() {}

    public static boolean isHunter(ServerPlayer p) {
        return ManhuntGame.isHunter(p.getUUID());
    }

    public static boolean isRunner(ServerPlayer p) {
        return ManhuntGame.isRunner(p.getUUID());
    }

    /** 按阵营设置最大生命值等基础属性（重生/登录后属性会重置，需要重新应用）。 */
    public static void applyBaseAttributes(ServerPlayer p) {
        var attr = p.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            double target = isRunner(p) ? GameConfig.RUNNER_MAX_HEALTH : GameConfig.HUNTER_MAX_HEALTH;
            if (attr.getBaseValue() != target) {
                attr.setBaseValue(target);
            }
        }
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    /** 周期性刷新增益（防止被死亡/牛奶清除后失效），并按阶段与维度切换增益组。 */
    public static void refreshBuffs(ServerPlayer p) {
        if (!ManhuntGame.isParticipant(p.getUUID())) {
            return;
        }
        applyBaseAttributes(p);
        // 时长大于刷新间隔，ambient=true 不显示粒子
        int dur = GameConfig.BUFF_REFRESH_INTERVAL_TICKS * 3;
        if (isRunner(p)) {
            if (p.level().dimension() == net.minecraft.world.level.Level.END) {
                // 末地强化：抗性提升 2、速度 1、跳跃提升 2、饱和 1、急迫 2
                add(p, MobEffects.RESISTANCE, dur, 1);
                add(p, MobEffects.SPEED, dur, 0);
                add(p, MobEffects.JUMP_BOOST, dur, 1);
                add(p, MobEffects.SATURATION, dur, 0);
                add(p, MobEffects.HASTE, dur, 1);
            } else {
                // 常规：抗性提升 1、急迫 1
                add(p, MobEffects.RESISTANCE, dur, 0);
                add(p, MobEffects.HASTE, dur, 0);
            }
        } else {
            if (ManhuntGame.phase() == ManhuntGame.Phase.ESCAPE) {
                // 逃跑时间：猎人被定身
                add(p, MobEffects.BLINDNESS, dur, 0);
                add(p, MobEffects.SLOWNESS, dur, 5);
                add(p, MobEffects.WEAKNESS, dur, 1);
            } else {
                // 猎人常规：速度 1、急迫 1
                add(p, MobEffects.SPEED, dur, 0);
                add(p, MobEffects.HASTE, dur, 0);
            }
        }
    }

    private static void add(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int dur, int amp) {
        p.addEffect(new MobEffectInstance(effect, dur, amp, true, false), null);
    }

    /** 追逐开始时清除猎人身上的定身减益。 */
    public static void clearEscapeDebuffs(ServerPlayer p) {
        p.removeEffect(MobEffects.BLINDNESS);
        p.removeEffect(MobEffects.SLOWNESS);
        p.removeEffect(MobEffects.WEAKNESS);
    }

    /** 逃生者初始装备：石剑、石镐、石斧、泥土 x64。 */
    public static void giveInitialKit(ServerPlayer p) {
        give(p, new ItemStack(Items.STONE_SWORD));
        give(p, new ItemStack(Items.STONE_PICKAXE));
        give(p, new ItemStack(Items.STONE_AXE));
        give(p, new ItemStack(Items.DIRT, 64));
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) {
            p.drop(stack, false);
        }
    }

    /** 游戏结束时把玩家恢复到默认状态。 */
    public static void resetToDefault(ServerPlayer p) {
        var attr = p.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(20.0);
        }
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.sendSystemMessage(Component.literal("§7[猎人游戏] 属性已恢复默认。"));
    }
}
