package com.example.manhunt.loot;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
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
 * 食物与合成材料/消耗品占大头，装备为低权重稀有项；2000+ 附魔书权重提升，3000+ 高级（满级）附魔书权重提升。
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

    /** 固定药水。 */
    public record PotionItem(Holder<Potion> potion, int weight) implements Entry {
        public PotionItem(Holder<Potion> potion) {
            this(potion, 1);
        }

        @Override
        public ItemStack roll(HolderLookup.Provider registries) {
            ItemStack stack = new ItemStack(Items.POTION);
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
            return stack;
        }
    }

    // ==================== 四档奖池（食物/消耗品为主，装备低权重） ====================

    private static final List<Entry> TIER_1 = List.of(
        // 食物（主力）
        new Simple(Items.BREAD, 3, 6, 3), new Simple(Items.COOKIE, 4, 8, 3),
        new Simple(Items.CARROT, 3, 6, 3), new Simple(Items.POTATO, 3, 6, 3),
        new Simple(Items.BAKED_POTATO, 3, 6, 2), new Simple(Items.APPLE, 2, 4, 2),
        new Simple(Items.MELON_SLICE, 4, 8, 2), new Simple(Items.SWEET_BERRIES, 3, 6, 2),
        new Simple(Items.COOKED_COD, 2, 4, 2), new Simple(Items.PUMPKIN_PIE, 1, 2, 1),
        new Simple(Items.WHEAT_SEEDS, 2, 6, 2), new Simple(Items.BEEF, 2, 4, 2),
        new Simple(Items.PORKCHOP, 2, 4, 2), new Simple(Items.CHICKEN, 2, 4, 2),
        new Simple(Items.BROWN_MUSHROOM, 2, 4, 2), new Simple(Items.RED_MUSHROOM, 2, 4, 2),
        new Simple(Items.DRIED_KELP, 4, 8, 2), new Simple(Items.BEETROOT, 3, 6, 2),
        new Simple(Items.WHEAT, 2, 5, 2), new Simple(Items.PUMPKIN, 1, 2, 1),
        // 合成材料
        new Simple(Items.OAK_LOG, 4, 8, 3), new Simple(Items.SPRUCE_LOG, 4, 8, 2),
        new Simple(Items.BIRCH_LOG, 4, 8, 2), new Simple(Items.OAK_PLANKS, 8, 16, 2),
        new Simple(Items.COBBLESTONE, 8, 16, 3), new Simple(Items.COAL, 4, 8, 3),
        new Simple(Items.STICK, 8, 16, 2), new Simple(Items.IRON_NUGGET, 6, 12, 2),
        new Simple(Items.FLINT, 2, 4, 2), new Simple(Items.LEATHER, 1, 3, 2),
        new Simple(Items.TORCH, 8, 16, 2), new Simple(Items.SAND, 8, 16, 2),
        new Simple(Items.GRAVEL, 4, 8, 2), new Simple(Items.CLAY_BALL, 2, 4, 2),
        new Simple(Items.BAMBOO, 3, 6, 1), new Simple(Items.CACTUS, 2, 4, 1),
        new Simple(Items.FEATHER, 2, 4, 2), new Simple(Items.PAPER, 2, 4, 2),
        new Simple(Items.SUGAR, 2, 4, 1), new Simple(Items.COCOA_BEANS, 2, 4, 1),
        new Simple(Items.INK_SAC, 1, 3, 1),
        // 实用道具
        new Simple(Items.BOWL, 2, 4, 1), new Simple(Items.CHEST, 1, 1, 1),
        new Simple(Items.CRAFTING_TABLE, 1, 1, 1), new Simple(Items.FURNACE, 1, 1, 1),
        new Simple(Items.OAK_SAPLING, 1, 2, 1),
        // 杂物
        new Simple(Items.SUGAR_CANE, 2, 4, 2), new Simple(Items.EGG, 2, 6, 2),
        new Simple(Items.BONE, 2, 4, 2), new Simple(Items.STRING, 2, 4, 2),
        new Simple(Items.SPIDER_EYE, 1, 3, 1), new Simple(Items.DYE.red(), 2, 4, 1),
        new Simple(Items.DYE.yellow(), 2, 4, 1), new Simple(Items.DYE.blue(), 2, 4, 1),
        new Simple(Items.ROTTEN_FLESH, 2, 6, 1),
        // 装备（低权重）
        new Simple(Items.WOODEN_PICKAXE, 1, 1, 1), new Simple(Items.WOODEN_SWORD, 1, 1, 1),
        new Simple(Items.STONE_SWORD, 1, 1, 1), new Simple(Items.STONE_SHOVEL, 1, 1, 1),
        new Simple(Items.STONE_AXE, 1, 1, 1), new Simple(Items.LEATHER_HELMET, 1, 1, 1),
        new Simple(Items.LEATHER_CHESTPLATE, 1, 1, 1), new Simple(Items.LEATHER_LEGGINGS, 1, 1, 1),
        new Simple(Items.LEATHER_BOOTS, 1, 1, 1));

    private static final List<Entry> TIER_2 = List.of(
        // 食物（主力）
        new Simple(Items.BREAD, 4, 8, 3), new Simple(Items.COOKED_BEEF, 3, 6, 3),
        new Simple(Items.COOKED_PORKCHOP, 3, 6, 2), new Simple(Items.COOKED_CHICKEN, 3, 6, 2),
        new Simple(Items.COOKED_SALMON, 3, 6, 2), new Simple(Items.GOLDEN_CARROT, 1, 2, 2),
        new Simple(Items.BAKED_POTATO, 4, 8, 2), new Simple(Items.COOKED_MUTTON, 3, 6, 2),
        new Simple(Items.RABBIT, 2, 4, 1), new Simple(Items.TROPICAL_FISH, 1, 2, 1),
        new Simple(Items.SUSPICIOUS_STEW, 1, 1, 1), new Simple(Items.DRIED_KELP_BLOCK, 1, 2, 1),
        new Simple(Items.PUMPKIN_PIE, 1, 2, 2),
        // 合成材料/消耗品
        new Simple(Items.IRON_INGOT, 2, 5, 3), new Simple(Items.COPPER_INGOT, 4, 8, 3),
        new Simple(Items.GOLD_INGOT, 1, 4, 2), new Simple(Items.COAL, 6, 12, 2),
        new Simple(Items.REDSTONE, 2, 6, 2), new Simple(Items.LAPIS_LAZULI, 2, 6, 2),
        new Simple(Items.GUNPOWDER, 1, 4, 2), new Simple(Items.GLASS, 4, 8, 2),
        new Simple(Items.LADDER, 4, 8, 2), new Simple(Items.ARROW, 8, 16, 2),
        new Simple(Items.HONEY_BOTTLE, 1, 2, 1), new Simple(Items.STRING, 2, 4, 2),
        new Simple(Items.FEATHER, 2, 4, 2), new Simple(Items.SLIME_BALL, 1, 3, 2),
        new Simple(Items.PAPER, 3, 6, 2), new Simple(Items.SUGAR, 2, 4, 2),
        new Simple(Items.HONEYCOMB, 1, 3, 2), new Simple(Items.QUARTZ, 2, 5, 2),
        new Simple(Items.MAGMA_CREAM, 1, 2, 1), new Simple(Items.BLAZE_POWDER, 1, 2, 1),
        new Simple(Items.BRICK, 2, 6, 2), new Simple(Items.SANDSTONE, 4, 8, 1),
        new Simple(Items.NETHER_WART, 2, 4, 1), new Simple(Items.GLOWSTONE_DUST, 2, 4, 2),
        new Simple(Items.GLASS_BOTTLE, 2, 4, 2),
        // 实用道具
        new Simple(Items.BUCKET, 1, 1, 2), new Simple(Items.SHEARS, 1, 1, 2),
        new Simple(Items.COMPASS, 1, 1, 1), new Simple(Items.CLOCK, 1, 1, 1),
        new Simple(Items.OAK_BOAT, 1, 1, 1), new Simple(Items.CAMPFIRE, 1, 1, 1),
        new Simple(Items.WRITABLE_BOOK, 1, 1, 1), new Simple(Items.FISHING_ROD, 1, 1, 1),
        new Simple(Items.LEAD, 1, 1, 2), new Simple(Items.PAINTING, 1, 1, 1),
        new Simple(Items.ITEM_FRAME, 1, 1, 1),
        // 装备（低权重）
        new Simple(Items.BOW, 1, 1, 1), new Simple(Items.IRON_PICKAXE, 1, 1, 1),
        new Simple(Items.IRON_SWORD, 1, 1, 1), new Simple(Items.IRON_AXE, 1, 1, 1),
        new Simple(Items.SHIELD, 1, 1, 1));

    private static final List<Entry> TIER_3 = List.of(
        // 附魔书（权重提升：合计 8）
        new EnchantedBook(3, 4), new EnchantedBook(3, 4),
        // 食物（主力）
        new Simple(Items.COOKED_BEEF, 4, 8, 3), new Simple(Items.COOKED_PORKCHOP, 4, 8, 2),
        new Simple(Items.BREAD, 6, 12, 2), new Simple(Items.GOLDEN_CARROT, 2, 4, 2),
        new Simple(Items.COOKED_MUTTON, 4, 8, 2), new Simple(Items.COOKED_RABBIT, 3, 6, 2),
        new Simple(Items.CAKE, 1, 1, 1),
        // 合成材料/消耗品
        new Simple(Items.DIAMOND, 1, 2, 2), new Simple(Items.EMERALD, 2, 5, 2),
        new Simple(Items.IRON_BLOCK, 1, 2, 2), new Simple(Items.OBSIDIAN, 2, 6, 2),
        new Simple(Items.EXPERIENCE_BOTTLE, 4, 8, 3), new Simple(Items.AMETHYST_SHARD, 1, 3, 2),
        new Simple(Items.ENDER_PEARL, 1, 2, 2), new Simple(Items.ARROW, 16, 32, 2),
        new Simple(Items.GOLDEN_APPLE, 1, 1, 1), new Simple(Items.RAW_IRON, 2, 4, 2),
        new Simple(Items.RAW_GOLD, 1, 3, 2), new Simple(Items.RAW_COPPER, 3, 6, 2),
        new Simple(Items.CHARCOAL, 4, 8, 2), new Simple(Items.IRON_NUGGET, 6, 12, 2),
        // 实用道具
        new Simple(Items.ANVIL, 1, 1, 1), new Simple(Items.SADDLE, 1, 1, 1),
        new Simple(Items.NAME_TAG, 1, 1, 1), new Simple(Items.PRISMARINE_SHARD, 2, 6, 2),
        new Simple(Items.BOOKSHELF, 1, 2, 1), new Simple(Items.LODESTONE, 1, 1, 1),
        new Simple(Items.FIRE_CHARGE, 1, 2, 1),
        // 装备（低权重）
        new Simple(Items.IRON_CHESTPLATE, 1, 1, 1), new Simple(Items.IRON_LEGGINGS, 1, 1, 1),
        new Simple(Items.IRON_HELMET, 1, 1, 1), new Simple(Items.IRON_BOOTS, 1, 1, 1),
        new Simple(Items.IRON_SWORD, 1, 1, 1), new Simple(Items.BOW, 1, 1, 1),
        new Simple(Items.SHIELD, 1, 1, 1));

    private static final List<Entry> TIER_4 = List.of(
        // 高级（满级）附魔书（权重提升：合计 6）
        new AdvancedBook(3), new AdvancedBook(3),
        // 末地攻略物资
        new Simple(Items.ENDER_EYE, 3, 6, 3), new Simple(Items.ENDER_PEARL, 2, 4, 2),
        new Simple(Items.ELYTRA, 1, 1, 1), new Simple(Items.ARROW, 32, 64, 2),
        new PotionItem(Potions.STRONG_STRENGTH, 2), new PotionItem(Potions.LONG_FIRE_RESISTANCE, 2),
        new PotionItem(Potions.LONG_SWIFTNESS, 2),
        // 食物
        new Simple(Items.GOLDEN_APPLE, 1, 2, 2), new Simple(Items.COOKED_BEEF, 6, 12, 2),
        new Simple(Items.ENCHANTED_GOLDEN_APPLE, 1, 1, 1),
        new Simple(Items.GLOW_BERRIES, 4, 8, 2), new Simple(Items.CHORUS_FRUIT, 2, 4, 2),
        // 合成材料/消耗品
        new Simple(Items.DIAMOND, 2, 4, 2), new Simple(Items.EXPERIENCE_BOTTLE, 8, 16, 2),
        new Simple(Items.PHANTOM_MEMBRANE, 2, 4, 2), new Simple(Items.NETHERITE_SCRAP, 1, 1, 1),
        new Simple(Items.ECHO_SHARD, 1, 2, 1), new Simple(Items.EMERALD_BLOCK, 1, 1, 1),
        // 实用道具
        new Simple(Items.SADDLE, 1, 1, 1), new Simple(Items.NAME_TAG, 1, 1, 1),
        new Simple(Items.TOTEM_OF_UNDYING, 1, 1, 1), new Simple(Items.ENDER_CHEST, 1, 1, 1),
        new Simple(Items.SHULKER_BOX, 1, 1, 1), new Simple(Items.LODESTONE, 1, 1, 1),
        new Simple(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 1, 1),
        new Simple(Items.DIAMOND_HORSE_ARMOR, 1, 1, 1),
        // 装备（低权重）
        new Simple(Items.DIAMOND_SWORD, 1, 1, 1), new Simple(Items.DIAMOND_PICKAXE, 1, 1, 1),
        new Simple(Items.DIAMOND_CHESTPLATE, 1, 1, 1), new Simple(Items.CROSSBOW, 1, 1, 1));

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

    /** 全档位合并池（超级抽奖用，不限里程）。 */
    public static List<Entry> allPools() {
        return ALL;
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
