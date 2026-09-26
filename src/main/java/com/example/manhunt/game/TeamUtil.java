package com.example.manhunt.game;

import com.example.manhunt.GameConfig;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * 阵营判定、属性与增益应用（性能随 {@link TierSystem} 动态档位变化）。
 */
public final class TeamUtil {
    private TeamUtil() {}

    public static boolean isHunter(ServerPlayer p) {
        return ManhuntGame.isHunter(p.getUUID());
    }

    public static boolean isRunner(ServerPlayer p) {
        return ManhuntGame.isRunner(p.getUUID());
    }

    /** 按阵营与当前档位设置最大生命值（重生/登录后属性会重置，需要重新应用）。 */
    public static void applyBaseAttributes(ServerPlayer p) {
        double target;
        if (isRunner(p)) {
            TierSystem.RunnerStats s = TierSystem.runner();
            target = p.level().dimension() == Level.END ? s.endMaxHealth() : s.maxHealth();
        } else {
            target = TierSystem.hunter().maxHealth();
        }
        var attr = p.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null && attr.getBaseValue() != target) {
            attr.setBaseValue(target);
        }
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    /** 周期性刷新增益（防止被死亡/牛奶清除后失效），并按档位与维度切换增益组。 */
    public static void refreshBuffs(ServerPlayer p) {
        if (!ManhuntGame.isParticipant(p.getUUID())) {
            return;
        }
        applySwimSpeed(p);
        applyBaseAttributes(p);
        // 时长大于刷新间隔，ambient=true 不显示粒子
        int dur = GameConfig.BUFF_REFRESH_INTERVAL_TICKS * 3;
        if (isRunner(p)) {
            add(p, MobEffects.GLOWING, dur, 0); // 常驻发光：描边颜色由发光分色队伍决定
            TierSystem.RunnerStats s = TierSystem.runner();
            if (p.level().dimension() == Level.END) {
                add(p, MobEffects.RESISTANCE, dur, s.endResistanceLevel() - 1);
                add(p, MobEffects.SPEED, dur, s.endSpeedLevel() - 1);
                add(p, MobEffects.JUMP_BOOST, dur, s.endJumpLevel() - 1);
                add(p, MobEffects.SATURATION, dur, 0);
                add(p, MobEffects.HASTE, dur, s.hasteLevel() - 1);
            } else {
                add(p, MobEffects.RESISTANCE, dur, s.resistanceLevel() - 1);
                add(p, MobEffects.HASTE, dur, s.hasteLevel() - 1);
            }
        } else {
            if (ManhuntGame.phase() == ManhuntGame.Phase.ESCAPE) {
                // 逃跑时间：猎人被定身
                add(p, MobEffects.BLINDNESS, dur, 0);
                add(p, MobEffects.SLOWNESS, dur, 5);
                add(p, MobEffects.WEAKNESS, dur, 1);
            } else {
                TierSystem.HunterStats s = TierSystem.hunter();
                add(p, MobEffects.SPEED, dur, s.speedLevel() - 1);
                add(p, MobEffects.HASTE, dur, s.hasteLevel() - 1);
            }
        }
    }

    private static void add(ServerPlayer p, Holder<MobEffect> effect, int dur, int amp) {
        p.addEffect(new MobEffectInstance(effect, dur, Math.max(0, amp), true, false), null);
    }

    /** 追逐开始时清除猎人身上的定身减益。 */
    public static void clearEscapeDebuffs(ServerPlayer p) {
        p.removeEffect(MobEffects.BLINDNESS);
        p.removeEffect(MobEffects.SLOWNESS);
        p.removeEffect(MobEffects.WEAKNESS);
    }

    private static final net.minecraft.resources.Identifier SWIM_ID =
        net.minecraft.resources.Identifier.fromNamespaceAndPath("manhunt", "swim_speed");

    /** 全体参与者的游泳速度等同于深海探索者 III（水下移速不受惩罚）。 */
    private static void applySwimSpeed(ServerPlayer p) {
        var swim = p.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
        if (swim != null) {
            swim.removeModifier(SWIM_ID);
            swim.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                SWIM_ID, 1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** 开局补满全部状态：灭火、清效果、满血、满饥饿与饱和。 */
    public static void fullyRestore(ServerPlayer p) {
        p.clearFire();
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(5.0F);
    }

    /** 逃生者初始装备：石剑、石镐、石斧、泥土 x64。 */
    public static void giveInitialKit(ServerPlayer p) {
        give(p, new ItemStack(Items.STONE_SWORD));
        give(p, new ItemStack(Items.STONE_PICKAXE));
        give(p, new ItemStack(Items.STONE_AXE));
        give(p, new ItemStack(Items.DIRT, 64));
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!com.example.manhunt.util.InvUtil.safeAdd(p, stack)) {
            p.drop(stack, false);
        }
    }

    /** 游戏结束时把玩家恢复到默认状态。 */
    public static void resetToDefault(ServerPlayer p) {
        var attr = p.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(20.0);
        }
        var swim = p.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
        if (swim != null) {
            swim.removeModifier(SWIM_ID);
        }
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.experienceLevel = 0;
        p.experienceProgress = 0.0F;
        p.totalExperience = 0;
        p.sendSystemMessage(Component.literal("§7[猎人游戏] 属性已恢复默认。"));
    }
}
