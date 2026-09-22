package com.example.manhunt.cards;

import java.util.ArrayList;
import java.util.List;

import com.example.manhunt.GameConfig;
import com.example.manhunt.ManhuntMod;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import net.neoforged.fml.ModList;

/**
 * 技能卡模组（skillcards）桥接层。全部对 skillcards 类的直接引用都收在本类内，
 * 未安装技能卡时（{@link #available()} 为 false）不会触发这些类的加载。
 */
public final class SkillCardsBridge {
    private SkillCardsBridge() {}

    private static Boolean loaded;

    public static boolean available() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("skillcards");
            if (loaded) {
                ManhuntMod.LOGGER.info("[Manhunt] 检测到技能卡模组，抽卡联动已启用");
            }
        }
        return loaded;
    }

    public record CardDraw(Object card, ItemStack stack) {
        /** 品级显示等级：0 普通 / 1 稀有 / 2 彩卡 / 3 黑卡（对应动画表现）。 */
        public int animationType() {
            return animationTypeOf(card);
        }
    }

    /** 按权重随机抽一张卡。 */
    public static CardDraw drawRandom(RandomSource rng) {
        int total = GameConfig.CARD_WEIGHT_COMMON + GameConfig.CARD_WEIGHT_RARE
            + GameConfig.CARD_WEIGHT_BLACK + GameConfig.CARD_WEIGHT_RAINBOW;
        int roll = rng.nextInt(total);
        if (roll < GameConfig.CARD_WEIGHT_RAINBOW) {
            return drawOfGrade(com.example.skillcards.registry.Card.Grade.RAINBOW, rng);
        }
        if (roll < GameConfig.CARD_WEIGHT_RAINBOW + GameConfig.CARD_WEIGHT_BLACK) {
            return drawOfGrade(com.example.skillcards.registry.Card.Grade.BLACK, rng);
        }
        if (roll < GameConfig.CARD_WEIGHT_RAINBOW + GameConfig.CARD_WEIGHT_BLACK + GameConfig.CARD_WEIGHT_RARE) {
            return drawOfGrade(com.example.skillcards.registry.Card.Grade.RARE, rng);
        }
        return drawOfGrade(com.example.skillcards.registry.Card.Grade.COMMON, rng);
    }

    /** 指定品级随机抽一张（要塞检查点固定彩卡）。 */
    public static CardDraw drawOfGrade(com.example.skillcards.registry.Card.Grade grade, RandomSource rng) {
        List<com.example.skillcards.registry.Card> pool = new ArrayList<>();
        for (com.example.skillcards.registry.Card card : com.example.skillcards.registry.Card.values()) {
            if (card.grade() == grade) {
                pool.add(card);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        com.example.skillcards.registry.Card picked = pool.get(rng.nextInt(pool.size()));
        ItemStack stack = new ItemStack(com.example.skillcards.registry.ModItems.itemOf(picked));
        return new CardDraw(picked, stack);
    }

    /** 固定抽彩卡（要塞检查点）。 */
    public static CardDraw drawRainbow(RandomSource rng) {
        return drawOfGrade(com.example.skillcards.registry.Card.Grade.RAINBOW, rng);
    }

    /** 按品级 id 抽卡（调试指令用：common/rare/rainbow/black，null 表示全权重随机）。 */
    public static CardDraw drawByGradeId(String gradeId, RandomSource rng) {
        if (gradeId == null) {
            return drawRandom(rng);
        }
        var grade = switch (gradeId) {
            case "common" -> com.example.skillcards.registry.Card.Grade.COMMON;
            case "rare" -> com.example.skillcards.registry.Card.Grade.RARE;
            case "rainbow" -> com.example.skillcards.registry.Card.Grade.RAINBOW;
            case "black" -> com.example.skillcards.registry.Card.Grade.BLACK;
            default -> null;
        };
        return grade == null ? null : drawOfGrade(grade, rng);
    }

    /** 每局开始/结束时重置技能卡的永久加成（如赤鳞跃动的生命/饥饿上限修改）。 */
    public static void resetPersistentBonuses(net.minecraft.server.level.ServerPlayer player) {
        if (!available()) {
            return;
        }
        com.example.skillcards.card.impl.ChiLinCard.reset(player);
    }

    /** 卡牌动画强调色（品级颜色）。 */
    public static int cardAccent(CardDraw draw) {
        var grade = gradeOf(draw.stack());
        return grade == null ? 0xFFFF55FF : grade.color();
    }

    /** 物品是否为技能卡。 */
    public static boolean isSkillCard(ItemStack stack) {
        return stack.getItem() instanceof com.example.skillcards.item.SkillCardItem;
    }

    /** 物品的卡牌品级；非技能卡返回 null。 */
    public static com.example.skillcards.registry.Card.Grade gradeOf(ItemStack stack) {
        if (stack.getItem() instanceof com.example.skillcards.item.SkillCardItem cardItem) {
            return cardItem.card().grade();
        }
        return null;
    }

    /** 品级名称颜色（ARGB）。 */
    public static int gradeColor(com.example.skillcards.registry.Card.Grade grade) {
        return grade.color();
    }

    /** 动画表现分类：0 普通 / 1 稀有 / 2 彩卡 / 3 黑卡。 */
    public static int animationTypeOf(Object card) {
        if (card instanceof com.example.skillcards.registry.Card c) {
            return switch (c.grade()) {
                case COMMON -> 0;
                case RARE -> 1;
                case RAINBOW -> 2;
                case BLACK -> 3;
            };
        }
        return 0;
    }
}
