package com.example.manhunt.loot;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 抽奖奖池：四档按里程解锁（<1000 / <2000 / <3000 / 3000+，阈值见 GameConfig.MILEAGE_TIERS）。
 * 数值与权重以《里程奖池一览》修订版为准（2026-09-27 玩家修订）：
 * 一档生存补给 59 条 / 二档冒险补给 64 条 / 三档精良补给 76 条 / 四档顶级补给 66 条。
 * 原版无凋零药水，凋零箭矢通过自定义效果箭实现（悬浮提示正常显示效果）。
 */
public final class RewardPools {
    private RewardPools() {}

    private static final RandomSource RNG = RandomSource.create();

    /** 常用正面附魔，用于随机附魔书条目。 */
    private static final String[] ENCHANT_POOL = {
        "sharpness", "smite", "efficiency", "unbreaking", "protection", "power", "punch",
        "feather_falling", "looting", "fortune", "silk_touch", "mending", "depth_strider",
        "soul_speed", "swift_sneak", "quick_charge", "impaling", "loyalty", "thorns",
        "respiration", "aqua_affinity", "frost_walker", "projectile_protection", "fire_protection",
        "infinity", "flame", "knockback", "fire_aspect", "sweeping_edge"
    };
    private static final List<String> SINGLE_LEVEL = List.of("silk_touch", "mending", "infinity", "aqua_affinity");

    /** 奖池条目：roll 时生成一份物品（需发放对象的注册表——数据包注册表条目必须来自活动注册表）。 */
    public interface Entry {
        ItemStack roll(HolderLookup.Provider registries);

        /** 抽取权重（默认 1）。 */
        default int weight() {
            return 1;
        }
    }

    /** 固定物品 + 随机数量区间 + 权重。 */
    public record Simple(Item item, int min, int max, int weight) implements Entry {
        public Simple(Item item, int min, int max) {
            this(item, min, max, 1);
        }

        @Override
        public ItemStack roll(HolderLookup.Provider registries) {
            return new ItemStack(item, min + RNG.nextInt(max - min + 1));
        }
    }

    /** 随机附魔书（等级受上限约束）。 */
    public record EnchantedBook(int maxLevelCap, int weight) implements Entry {
        public EnchantedBook(int maxLevelCap) {
            this(maxLevelCap, 1);
        }

        @Override
        public ItemStack roll(HolderLookup.Provider registries) {
            return randomBook(registries, maxLevelCap, false);
        }
    }

    /** 高级附魔书：直接给该附魔的最高等级。 */
    public record AdvancedBook(int weight) implements Entry {
        @Override
        public ItemStack roll(HolderLookup.Provider registries) {
            return randomBook(registries, 255, true);
        }
    }

    /** 固定药水（可指定瓶型：POTION / SPLASH_POTION / LINGERING_POTION）。 */
    public record PotionItem(Item item, Holder<Potion> potion, int weight) implements Entry {
        public PotionItem(Holder<Potion> potion, int weight) {
            this(Items.POTION, potion, weight);
        }

        public PotionItem(Holder<Potion> potion) {
            this(Items.POTION, potion, 1);
        }

        @Override
        public ItemStack roll(HolderLookup.Provider registries) {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
            return stack;
        }
    }

    /** 药箭（原版药水，如剧毒/迟缓/虚弱/神龟）。 */
    public record TippedArrow(Holder<Potion> potion, int min, int max, int weight) implements Entry {
        @Override
        public ItemStack roll(HolderLookup.Provider registries) {
            ItemStack stack = new ItemStack(Items.TIPPED_ARROW, min + RNG.nextInt(max - min + 1));
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
            return stack;
        }
    }

    /** 自定义效果箭（原版无对应药水时使用，如凋零Ⅱ；悬浮提示显示效果）。 */
    public record EffectArrow(Holder<MobEffect> effect, int duration, int amplifier,
                              int min, int max, int weight) implements Entry {
        @Override
        public ItemStack roll(HolderLookup.Provider registries) {
            ItemStack stack = new ItemStack(Items.TIPPED_ARROW, min + RNG.nextInt(max - min + 1));
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
                Optional.empty(), Optional.empty(),
                List.of(new MobEffectInstance(effect, duration, amplifier)), Optional.empty()));
            return stack;
        }
    }

    // ==================== 一档 <1000：生存补给 ====================

    private static final List<Entry> TIER_1 = List.of(
        // 食物
        new Simple(Items.BREAD, 1, 8, 7), new Simple(Items.CARROT, 3, 6, 3),
        new Simple(Items.COOKIE, 2, 6, 2), new Simple(Items.POTATO, 3, 6, 3),
        new Simple(Items.APPLE, 2, 4, 2), new Simple(Items.BAKED_POTATO, 3, 6, 1),
        new Simple(Items.BEEF, 2, 4, 2), new Simple(Items.DRIED_KELP, 2, 6, 2),
        new Simple(Items.MELON_SLICE, 2, 6, 2), new Simple(Items.PORKCHOP, 2, 4, 2),
        new Simple(Items.SWEET_BERRIES, 8, 20, 2), new Simple(Items.WHEAT, 6, 12, 2),
        new Simple(Items.PUMPKIN, 3, 14, 1), new Simple(Items.PUMPKIN_PIE, 1, 2, 1),
        // 材料
        new Simple(Items.IRON_NUGGET, 10, 22, 2), new Simple(Items.COAL, 4, 8, 2),
        new Simple(Items.COBBLESTONE, 10, 18, 5), new Simple(Items.OAK_LOG, 3, 5, 4),
        new Simple(Items.EGG, 12, 16, 2), new Simple(Items.FLINT, 2, 4, 2),
        new Simple(Items.GRAVEL, 24, 48, 2), new Simple(Items.LEATHER, 1, 3, 2),
        new Simple(Items.OAK_PLANKS, 8, 18, 5), new Simple(Items.SAND, 24, 48, 2),
        new Simple(Items.SNOW_BLOCK, 16, 24, 2), new Simple(Items.STICK, 8, 16, 2),
        new Simple(Items.STRING, 2, 3, 2), new Simple(Items.SUGAR_CANE, 7, 11, 1),
        new Simple(Items.BAMBOO, 8, 20, 1), new Simple(Items.SUGAR, 2, 3, 1),
        new Simple(Items.ROTTEN_FLESH, 7, 9, 3), new Simple(Items.SPIDER_EYE, 1, 3, 1),
        // 道具
        new Simple(Items.TORCH, 24, 64, 1), new Simple(Items.CHEST, 10, 10, 1),
        new Simple(Items.FURNACE, 1, 1, 1), new Simple(Items.OAK_SAPLING, 5, 20, 1),
        new Simple(Items.BOWL, 1, 1, 1),
        // 消耗品
        new Simple(Items.ARROW, 3, 9, 1), new Simple(Items.FIRE_CHARGE, 1, 2, 1),
        new Simple(Items.COBWEB, 1, 3, 1),
        // 装备
        new Simple(Items.BOW, 1, 1, 1), new Simple(Items.SHEARS, 1, 1, 2),
        new Simple(Items.LEATHER_BOOTS, 1, 1, 1), new Simple(Items.LEATHER_CHESTPLATE, 1, 1, 1),
        new Simple(Items.LEATHER_HELMET, 1, 1, 1), new Simple(Items.LEATHER_LEGGINGS, 1, 1, 1),
        new Simple(Items.GOLDEN_HELMET, 1, 1, 1), new Simple(Items.CHAINMAIL_HELMET, 1, 1, 1),
        new Simple(Items.COPPER_HELMET, 1, 1, 1), new Simple(Items.IRON_BOOTS, 5, 5, 1),
        new Simple(Items.IRON_HOE, 1, 1, 1),
        new Simple(Items.STONE_PICKAXE, 1, 1, 1), new Simple(Items.STONE_AXE, 1, 1, 1),
        new Simple(Items.STONE_SHOVEL, 1, 1, 1), new Simple(Items.STONE_SWORD, 1, 1, 1),
        new Simple(Items.WOODEN_PICKAXE, 1, 1, 1), new Simple(Items.WOODEN_AXE, 1, 1, 1),
        new Simple(Items.WOODEN_SHOVEL, 1, 1, 1), new Simple(Items.WOODEN_SWORD, 1, 1, 1));

    // ==================== 二档 <2000：冒险补给 ====================

    private static final List<Entry> TIER_2 = List.of(
        // 材料
        new Simple(Items.COPPER_INGOT, 4, 12, 5), new Simple(Items.IRON_INGOT, 2, 5, 4),
        new Simple(Items.GOLD_INGOT, 3, 5, 3), new Simple(Items.COAL, 6, 12, 2),
        new Simple(Items.GLASS, 40, 64, 2), new Simple(Items.LAPIS_LAZULI, 2, 6, 2),
        new Simple(Items.PAPER, 3, 6, 2), new Simple(Items.BONE, 7, 14, 2),
        new Simple(Items.REDSTONE, 4, 12, 1), new Simple(Items.STRING, 3, 6, 2),
        new Simple(Items.SUGAR, 2, 4, 2), new Simple(Items.COBBLESTONE, 15, 25, 4),
        new Simple(Items.OAK_PLANKS, 16, 32, 3),
        // 食物
        new Simple(Items.BREAD, 2, 14, 7), new Simple(Items.COOKED_BEEF, 3, 5, 2),
        new Simple(Items.BAKED_POTATO, 6, 8, 4), new Simple(Items.COOKED_MUTTON, 3, 5, 2),
        new Simple(Items.COOKED_PORKCHOP, 3, 5, 2), new Simple(Items.GOLDEN_CARROT, 2, 4, 1),
        new Simple(Items.CARROT, 3, 6, 1), new Simple(Items.PUMPKIN_PIE, 4, 7, 1),
        new Simple(Items.DRIED_KELP_BLOCK, 5, 5, 1),
        // 消耗品
        new Simple(Items.ARROW, 6, 12, 3), new Simple(Items.EGG, 16, 16, 1),
        new Simple(Items.HONEY_BOTTLE, 1, 2, 1), new Simple(Items.COBWEB, 4, 8, 1),
        new Simple(Items.FIRE_CHARGE, 5, 7, 2),
        new TippedArrow(Potions.SLOWNESS, 2, 2, 1), new TippedArrow(Potions.WEAKNESS, 2, 2, 1),
        new TippedArrow(Potions.POISON, 2, 2, 1),
        new PotionItem(Potions.STRONG_SWIFTNESS, 1), new PotionItem(Potions.STRONG_LEAPING, 1),
        // 道具
        new Simple(Items.BUCKET, 1, 1, 2), new Simple(Items.MILK_BUCKET, 1, 1, 1),
        new Simple(Items.GLOWSTONE, 8, 20, 1), new Simple(Items.LADDER, 15, 40, 2),
        new Simple(Items.SHEARS, 1, 1, 2), new Simple(Items.CAMPFIRE, 1, 1, 1),
        new Simple(Items.SPYGLASS, 1, 1, 1), new Simple(Items.CLOCK, 1, 1, 1),
        new Simple(Items.FISHING_ROD, 1, 1, 1), new Simple(Items.ITEM_FRAME, 1, 1, 1),
        new Simple(Items.CHERRY_BOAT, 1, 1, 2), new Simple(Items.DEAD_BUSH, 1, 1, 1),
        // 装备
        new Simple(Items.BOW, 1, 1, 2), new Simple(Items.SHIELD, 1, 1, 2),
        new Simple(Items.CHAINMAIL_HELMET, 1, 1, 1), new Simple(Items.CHAINMAIL_CHESTPLATE, 1, 1, 1),
        new Simple(Items.CHAINMAIL_LEGGINGS, 1, 1, 1), new Simple(Items.CHAINMAIL_BOOTS, 1, 1, 1),
        new Simple(Items.COPPER_HELMET, 1, 1, 1), new Simple(Items.COPPER_CHESTPLATE, 1, 1, 1),
        new Simple(Items.COPPER_LEGGINGS, 1, 1, 1), new Simple(Items.COPPER_BOOTS, 1, 1, 1),
        new Simple(Items.IRON_AXE, 1, 1, 1), new Simple(Items.IRON_PICKAXE, 1, 1, 1),
        new Simple(Items.IRON_SHOVEL, 1, 1, 1), new Simple(Items.IRON_SWORD, 1, 1, 1),
        new Simple(Items.IRON_SPEAR, 1, 1, 1),
        new Simple(Items.COPPER_AXE, 1, 1, 2), new Simple(Items.COPPER_PICKAXE, 1, 1, 1),
        new Simple(Items.COPPER_SHOVEL, 1, 1, 1), new Simple(Items.COPPER_SWORD, 1, 1, 2),
        new Simple(Items.COPPER_SPEAR, 1, 1, 2));

    // ==================== 三档 <3000：精良补给 ====================

    private static final List<Entry> TIER_3 = List.of(
        // 附魔书
        new EnchantedBook(3, 5), new EnchantedBook(3, 5),
        // 食物
        new Simple(Items.PUMPKIN_PIE, 5, 9, 4), new Simple(Items.BREAD, 5, 15, 5),
        new Simple(Items.COOKED_PORKCHOP, 5, 7, 1), new Simple(Items.SUSPICIOUS_STEW, 1, 1, 2),
        new Simple(Items.COOKED_BEEF, 5, 7, 1), new Simple(Items.GOLDEN_APPLE, 1, 1, 1),
        new Simple(Items.CAKE, 1, 1, 2),
        // 材料
        new Simple(Items.DIAMOND, 1, 2, 3), new Simple(Items.EMERALD, 10, 25, 3),
        new Simple(Items.AMETHYST_SHARD, 1, 3, 2), new Simple(Items.COAL, 10, 24, 2),
        new Simple(Items.IRON_BLOCK, 1, 2, 2), new Simple(Items.GOLD_INGOT, 9, 12, 2),
        new Simple(Items.OBSIDIAN, 10, 15, 2), new Simple(Items.PRISMARINE_SHARD, 2, 6, 1),
        new Simple(Items.REDSTONE, 10, 20, 2), new Simple(Items.RAW_GOLD, 30, 45, 1),
        new Simple(Items.RAW_IRON, 30, 45, 1), new Simple(Items.COBBLED_DEEPSLATE, 20, 30, 3),
        new Simple(Items.GLASS, 48, 64, 2),
        // 消耗品
        new Simple(Items.EXPERIENCE_BOTTLE, 4, 8, 1), new Simple(Items.ARROW, 16, 32, 3),
        new Simple(Items.ENDER_PEARL, 1, 1, 2), new Simple(Items.WIND_CHARGE, 3, 5, 2),
        new Simple(Items.FIRE_CHARGE, 24, 36, 2), new Simple(Items.FLINT_AND_STEEL, 1, 1, 1),
        new Simple(Items.COBWEB, 8, 12, 1), new Simple(Items.TNT, 2, 4, 1),
        new TippedArrow(Potions.STRONG_TURTLE_MASTER, 2, 2, 1),
        new TippedArrow(Potions.POISON, 5, 5, 1), new TippedArrow(Potions.SLOWNESS, 5, 5, 1),
        new TippedArrow(Potions.WEAKNESS, 5, 5, 1),
        new EffectArrow(MobEffects.WITHER, 200, 1, 2, 2, 1),
        // 道具
        new Simple(Items.ANVIL, 1, 1, 1), new Simple(Items.BOOKSHELF, 5, 15, 1),
        new Simple(Items.ENCHANTING_TABLE, 1, 1, 1), new Simple(Items.BLAST_FURNACE, 5, 5, 1),
        new Simple(Items.SPONGE, 3, 5, 1), new Simple(Items.MAP, 1, 1, 1),
        new Simple(Items.LODESTONE, 1, 1, 1), new Simple(Items.NAME_TAG, 1, 1, 1),
        new Simple(Items.SADDLE, 1, 1, 2), new Simple(Items.CREEPER_HEAD, 1, 1, 1),
        // 药水
        new PotionItem(Potions.STRONG_REGENERATION, 1), new PotionItem(Potions.STRONG_LEAPING, 1),
        new PotionItem(Potions.STRONG_SWIFTNESS, 1),
        new PotionItem(Items.SPLASH_POTION, Potions.STRONG_HARMING, 1),
        new PotionItem(Items.LINGERING_POTION, Potions.STRONG_TURTLE_MASTER, 1),
        // 装备
        new Simple(Items.BOW, 1, 1, 2), new Simple(Items.CROSSBOW, 1, 1, 1),
        new Simple(Items.SHIELD, 1, 1, 2), new Simple(Items.TURTLE_HELMET, 1, 1, 1),
        new Simple(Items.CHAINMAIL_BOOTS, 1, 1, 1), new Simple(Items.CHAINMAIL_CHESTPLATE, 1, 1, 1),
        new Simple(Items.CHAINMAIL_HELMET, 1, 1, 1), new Simple(Items.CHAINMAIL_LEGGINGS, 1, 1, 1),
        new Simple(Items.IRON_BOOTS, 1, 1, 2), new Simple(Items.IRON_CHESTPLATE, 1, 1, 2),
        new Simple(Items.IRON_HELMET, 1, 1, 2), new Simple(Items.IRON_LEGGINGS, 1, 1, 2),
        new Simple(Items.GOLDEN_BOOTS, 1, 1, 1), new Simple(Items.GOLDEN_CHESTPLATE, 1, 1, 1),
        new Simple(Items.GOLDEN_HELMET, 1, 1, 1), new Simple(Items.GOLDEN_LEGGINGS, 1, 1, 1),
        new Simple(Items.IRON_SWORD, 1, 1, 2), new Simple(Items.IRON_AXE, 1, 1, 2),
        new Simple(Items.IRON_SPEAR, 1, 1, 1), new Simple(Items.IRON_PICKAXE, 1, 1, 2),
        new Simple(Items.IRON_SHOVEL, 1, 1, 1),
        new Simple(Items.GOLDEN_SWORD, 1, 1, 1), new Simple(Items.GOLDEN_AXE, 1, 1, 1),
        new Simple(Items.GOLDEN_SPEAR, 1, 1, 1), new Simple(Items.GOLDEN_PICKAXE, 1, 1, 1),
        new Simple(Items.GOLDEN_SHOVEL, 1, 1, 1));

    // ==================== 四档 3000+：顶级补给 ====================

    private static final List<Entry> TIER_4 = List.of(
        // 附魔书
        new AdvancedBook(6), new AdvancedBook(6),
        // 材料
        new Simple(Items.DIAMOND, 3, 4, 6), new Simple(Items.PUMPKIN_PIE, 6, 12, 7),
        new Simple(Items.REDSTONE, 24, 36, 1), new Simple(Items.OBSIDIAN, 24, 32, 2),
        new Simple(Items.EMERALD_BLOCK, 64, 64, 4), new Simple(Items.END_CRYSTAL, 1, 3, 2),
        // 消耗品
        new Simple(Items.ENDER_EYE, 3, 6, 5), new Simple(Items.ARROW, 32, 48, 4),
        new Simple(Items.ENDER_PEARL, 1, 2, 4), new Simple(Items.WIND_CHARGE, 12, 16, 3),
        new Simple(Items.FLINT_AND_STEEL, 1, 1, 2), new Simple(Items.COBWEB, 8, 12, 2),
        new Simple(Items.TNT, 12, 24, 2), new Simple(Items.LAVA_BUCKET, 1, 1, 3),
        new Simple(Items.EGG, 16, 16, 3),
        new TippedArrow(Potions.LONG_TURTLE_MASTER, 5, 5, 1),
        new TippedArrow(Potions.HARMING, 3, 3, 1),
        new EffectArrow(MobEffects.WEAKNESS, 900, 1, 10, 10, 2), // 原版无虚弱Ⅱ药水，用自定义效果箭
        new EffectArrow(MobEffects.WITHER, 200, 1, 10, 10, 2),
        // 食物
        new Simple(Items.CHORUS_FRUIT, 4, 10, 2), new Simple(Items.GOLDEN_CARROT, 4, 8, 5),
        new Simple(Items.GOLDEN_APPLE, 2, 3, 3), new Simple(Items.ENCHANTED_GOLDEN_APPLE, 1, 1, 1),
        // 药水
        new PotionItem(Potions.LONG_FIRE_RESISTANCE, 2), new PotionItem(Potions.LONG_SWIFTNESS, 2),
        new PotionItem(Potions.STRONG_STRENGTH, 2), new PotionItem(Potions.LONG_NIGHT_VISION, 1),
        new PotionItem(Potions.STRONG_SWIFTNESS, 1), new PotionItem(Potions.SLOW_FALLING, 2),
        new PotionItem(Items.SPLASH_POTION, Potions.STRONG_SLOWNESS, 1),
        new PotionItem(Items.SPLASH_POTION, Potions.STRONG_HARMING, 3),
        // 道具
        new Simple(Items.ENDER_CHEST, 1, 1, 1), new Simple(Items.SPONGE, 10, 24, 2),
        new Simple(Items.SMITHING_TABLE, 1, 1, 2), new Simple(Items.SADDLE, 1, 1, 1),
        new Simple(Items.SHULKER_BOX, 1, 1, 1),
        // 装备
        new Simple(Items.BOW, 1, 1, 3), new Simple(Items.CROSSBOW, 1, 1, 2),
        new Simple(Items.TRIDENT, 1, 1, 2), new Simple(Items.MACE, 1, 1, 1),
        new Simple(Items.ELYTRA, 1, 1, 1), new Simple(Items.FISHING_ROD, 1, 1, 3),
        new Simple(Items.TOTEM_OF_UNDYING, 1, 1, 1),
        new Simple(Items.NETHERITE_INGOT, 1, 1, 2), new Simple(Items.NETHERITE_SWORD, 1, 1, 1),
        new Simple(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 1, 2),
        new Simple(Items.DIAMOND_BOOTS, 1, 1, 2), new Simple(Items.DIAMOND_CHESTPLATE, 1, 1, 2),
        new Simple(Items.DIAMOND_HELMET, 1, 1, 2), new Simple(Items.DIAMOND_LEGGINGS, 1, 1, 2),
        new Simple(Items.IRON_BOOTS, 1, 1, 1), new Simple(Items.IRON_CHESTPLATE, 1, 1, 1),
        new Simple(Items.IRON_HELMET, 1, 1, 1), new Simple(Items.IRON_LEGGINGS, 1, 1, 1),
        new Simple(Items.DIAMOND_AXE, 1, 1, 2), new Simple(Items.DIAMOND_PICKAXE, 1, 1, 2),
        new Simple(Items.DIAMOND_SWORD, 1, 1, 2), new Simple(Items.DIAMOND_SHOVEL, 1, 1, 2),
        new Simple(Items.DIAMOND_SPEAR, 1, 1, 2),
        new Simple(Items.IRON_AXE, 1, 1, 1), new Simple(Items.IRON_PICKAXE, 1, 1, 1),
        new Simple(Items.IRON_SWORD, 1, 1, 2), new Simple(Items.IRON_SHOVEL, 1, 1, 1),
        new Simple(Items.IRON_SPEAR, 1, 1, 1));

    private static final List<Entry> ALL = new java.util.ArrayList<>();

    static {
        ALL.addAll(TIER_1);
        ALL.addAll(TIER_2);
        ALL.addAll(TIER_3);
        ALL.addAll(TIER_4);
    }

    /** 按里程取奖池档位（0~3）。 */
    public static int tierOf(long mileage) {
        int[] thresholds = com.example.manhunt.GameConfig.MILEAGE_TIERS;
        for (int i = 0; i < thresholds.length; i++) {
            if (mileage < thresholds[i]) {
                return i;
            }
        }
        return thresholds.length;
    }

    public static List<Entry> pool(int tier) {
        return switch (Math.max(0, Math.min(3, tier))) {
            case 0 -> TIER_1;
            case 1 -> TIER_2;
            case 2 -> TIER_3;
            default -> TIER_4;
        };
    }

    /** 全档位合并池。 */
    public static List<Entry> allPools() {
        return ALL;
    }

    /** 合并 tier（0~3）及以上档位的池——超级抽奖不出现低于当前里程档的物资。 */
    public static List<Entry> poolsFrom(int tier) {
        List<Entry> out = new java.util.ArrayList<>();
        for (int i = Math.max(0, tier); i <= 3; i++) {
            out.addAll(pool(i));
        }
        return out;
    }

    /** 按权重随机选取一个条目。 */
    public static Entry weightedPick(List<Entry> pool) {
        int total = 0;
        for (Entry e : pool) {
            total += Math.max(1, e.weight());
        }
        int roll = RNG.nextInt(total);
        for (Entry e : pool) {
            roll -= Math.max(1, e.weight());
            if (roll < 0) {
                return e;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /**
     * 随机附魔书生成。必须使用发放对象的注册表（玩家/世界的活动注册表）——
     * 附魔是数据包注册表，静态默认表（VanillaRegistries）的 Holder 无法被网络编码，
     * 会导致 Failed to encode packet / 连接丢失。
     * advanced=true 时直接给该附魔的最高等级。
     */
    public static ItemStack randomBook(HolderLookup.Provider registries, int maxLevelCap, boolean advanced) {
        String id = ENCHANT_POOL[RNG.nextInt(ENCHANT_POOL.length)];
        var registry = registries.lookupOrThrow(Registries.ENCHANTMENT);
        var key = net.minecraft.resources.ResourceKey.create(
            Registries.ENCHANTMENT, Identifier.withDefaultNamespace(id));
        Optional<Holder.Reference<Enchantment>> holder = registry.get(key);
        if (holder.isEmpty()) {
            return new ItemStack(Items.PAPER);
        }
        int max = holder.get().value().getMaxLevel();
        int level = advanced ? max
            : SINGLE_LEVEL.contains(id) ? 1 : 1 + RNG.nextInt(Math.min(max, Math.max(1, maxLevelCap)));
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(holder.get(), level);
        book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return book;
    }
}
