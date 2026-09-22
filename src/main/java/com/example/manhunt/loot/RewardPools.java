package com.example.manhunt.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
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
 * 抽奖奖池：五档按里程解锁，档位越高物资越优质。
 * 每档混入一定比例的非战斗实用物品，保持抽奖的不确定性。
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
        ItemStack roll(net.minecraft.core.HolderLookup.Provider registries);
    }

    /** 固定物品 + 随机数量区间。 */
    public record Simple(Item item, int min, int max) implements Entry {
        @Override
        public ItemStack roll(net.minecraft.core.HolderLookup.Provider registries) {
            return new ItemStack(item, min + RNG.nextInt(max - min + 1));
        }
    }

    /** 随机附魔书（等级受附魔上限约束）。 */
    public record EnchantedBook(int maxLevelCap) implements Entry {
        @Override
        public ItemStack roll(net.minecraft.core.HolderLookup.Provider registries) {
            return randomBook(registries, maxLevelCap);
        }
    }

    /** 固定药水。 */
    public record PotionItem(Holder<Potion> potion) implements Entry {
        @Override
        public ItemStack roll(net.minecraft.core.HolderLookup.Provider registries) {
            ItemStack stack = new ItemStack(Items.POTION);
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
            return stack;
        }
    }

    // ==================== 五档奖池 ====================

    private static final List<Entry> TIER_1 = List.of(
        // 低级生存物资（<500 里程）
        new Simple(Items.OAK_LOG, 4, 8), new Simple(Items.OAK_PLANKS, 8, 16),
        new Simple(Items.COBBLESTONE, 8, 16), new Simple(Items.COAL, 4, 8),
        new Simple(Items.IRON_NUGGET, 4, 8), new Simple(Items.FLINT, 2, 4),
        new Simple(Items.LEATHER, 2, 4), new Simple(Items.LEATHER_HELMET, 1, 1),
        new Simple(Items.LEATHER_CHESTPLATE, 1, 1), new Simple(Items.LEATHER_LEGGINGS, 1, 1),
        new Simple(Items.LEATHER_BOOTS, 1, 1), new Simple(Items.COOKIE, 4, 8),
        new Simple(Items.CARROT, 4, 8), new Simple(Items.POTATO, 4, 8),
        new Simple(Items.WOODEN_PICKAXE, 1, 1), new Simple(Items.STONE_SWORD, 1, 1),
        new Simple(Items.STONE_SHOVEL, 1, 1), new Simple(Items.TORCH, 8, 16),
        // 实用杂物
        new Simple(Items.SUGAR_CANE, 2, 4), new Simple(Items.EGG, 2, 6),
        new Simple(Items.BONE, 2, 4), new Simple(Items.STRING, 2, 4),
        new Simple(Items.SPIDER_EYE, 1, 3), new Simple(Items.DYE.red(), 2, 4),
        new Simple(Items.DYE.yellow(), 2, 4), new Simple(Items.DYE.blue(), 2, 4),
        new Simple(Items.SNOWBALL, 8, 16), new Simple(Items.ROTTEN_FLESH, 2, 6));

    private static final List<Entry> TIER_2 = List.of(
        // 中级物资（<1000 里程）
        new Simple(Items.IRON_INGOT, 2, 5), new Simple(Items.GOLD_INGOT, 2, 4),
        new Simple(Items.COPPER_INGOT, 4, 8), new Simple(Items.BREAD, 3, 6),
        new Simple(Items.BOW, 1, 1), new Simple(Items.ARROW, 8, 16),
        new Simple(Items.IRON_PICKAXE, 1, 1), new Simple(Items.IRON_SWORD, 1, 1),
        new Simple(Items.GOLDEN_SWORD, 1, 1), new Simple(Items.GOLDEN_PICKAXE, 1, 1),
        new Simple(Items.GOLDEN_AXE, 1, 1), new Simple(Items.SHIELD, 1, 1),
        new Simple(Items.BUCKET, 1, 1), new Simple(Items.SHEARS, 1, 1),
        new Simple(Items.COMPASS, 1, 1), new Simple(Items.CLOCK, 1, 1),
        new Simple(Items.COOKED_COD, 3, 6),
        // 实用杂物
        new Simple(Items.REDSTONE, 2, 6), new Simple(Items.LAPIS_LAZULI, 2, 6),
        new Simple(Items.LADDER, 4, 8), new Simple(Items.OAK_BOAT, 1, 1),
        new Simple(Items.CAMPFIRE, 1, 1), new Simple(Items.WRITABLE_BOOK, 1, 1),
        new Simple(Items.BELL, 1, 1));

    private static final List<Entry> TIER_3 = List.of(
        // 高级物资（<2000 里程）
        new Simple(Items.DIAMOND, 1, 3), new Simple(Items.EMERALD, 2, 5),
        new Simple(Items.COOKED_BEEF, 4, 8), new Simple(Items.COOKED_PORKCHOP, 4, 8),
        new Simple(Items.IRON_CHESTPLATE, 1, 1), new Simple(Items.IRON_LEGGINGS, 1, 1),
        new Simple(Items.IRON_HELMET, 1, 1), new Simple(Items.IRON_BOOTS, 1, 1),
        new Simple(Items.IRON_AXE, 1, 1), new Simple(Items.IRON_SHOVEL, 1, 1),
        new Simple(Items.ENDER_PEARL, 1, 2), new Simple(Items.EXPERIENCE_BOTTLE, 4, 8),
        new Simple(Items.SPYGLASS, 1, 1), new Simple(Items.IRON_BLOCK, 1, 2),
        new Simple(Items.REDSTONE_BLOCK, 1, 2),
        new EnchantedBook(3),
        // 实用杂物
        new Simple(Items.ANVIL, 1, 1), new Simple(Items.GOLDEN_CARROT, 2, 4),
        new Simple(Items.SADDLE, 1, 1), new Simple(Items.PRISMARINE_SHARD, 2, 6),
        new Simple(Items.OBSIDIAN, 2, 6), new Simple(Items.NAME_TAG, 1, 1));

    private static final List<Entry> TIER_4 = List.of(
        // 优质物资（<3000 里程）
        new Simple(Items.DIAMOND, 2, 4), new Simple(Items.GOLDEN_APPLE, 1, 2),
        new Simple(Items.DIAMOND_SWORD, 1, 1), new Simple(Items.DIAMOND_PICKAXE, 1, 1),
        new Simple(Items.DIAMOND_CHESTPLATE, 1, 1), new Simple(Items.DIAMOND_LEGGINGS, 1, 1),
        new Simple(Items.DIAMOND_HELMET, 1, 1), new Simple(Items.DIAMOND_BOOTS, 1, 1),
        new Simple(Items.CROSSBOW, 1, 1), new Simple(Items.GOLD_BLOCK, 1, 2),
        new Simple(Items.EMERALD_BLOCK, 1, 2), new Simple(Items.EXPERIENCE_BOTTLE, 8, 16),
        new PotionItem(Potions.STRONG_STRENGTH), new PotionItem(Potions.LONG_REGENERATION),
        new EnchantedBook(5),
        // 实用杂物
        new Simple(Items.NAME_TAG, 1, 1), new Simple(Items.SADDLE, 1, 1),
        new Simple(Items.PRISMARINE_SHARD, 2, 6), new Simple(Items.NAUTILUS_SHELL, 1, 2),
        new Simple(Items.ECHO_SHARD, 1, 2), new Simple(Items.ARMADILLO_SCUTE, 1, 2));

    private static final List<Entry> TIER_5 = List.of(
        // 顶级物资（3000+ 里程）
        new Simple(Items.ENCHANTED_GOLDEN_APPLE, 1, 1), new Simple(Items.TOTEM_OF_UNDYING, 1, 1),
        new Simple(Items.NETHERITE_INGOT, 1, 2), new Simple(Items.NETHERITE_SCRAP, 1, 2),
        new Simple(Items.DIAMOND_BLOCK, 1, 1), new Simple(Items.GOLDEN_CARROT, 4, 8),
        new Simple(Items.PHANTOM_MEMBRANE, 2, 4), new Simple(Items.ENDER_EYE, 1, 3),
        new Simple(Items.END_CRYSTAL, 1, 1), new Simple(Items.SHULKER_SHELL, 1, 2),
        new PotionItem(Potions.STRONG_STRENGTH), new PotionItem(Potions.STRONG_REGENERATION),
        new PotionItem(Potions.LONG_SLOW_FALLING), new EnchantedBook(5), new EnchantedBook(5),
        new Simple(Items.TOTEM_OF_UNDYING, 1, 1),
        // 实用杂物
        new Simple(Items.SADDLE, 1, 1), new Simple(Items.NAME_TAG, 1, 1),
        new Simple(Items.PRISMARINE_CRYSTALS, 2, 6), new Simple(Items.NETHER_STAR, 1, 1));

    private static final List<Entry> ALL = new ArrayList<>();

    static {
        ALL.addAll(TIER_1);
        ALL.addAll(TIER_2);
        ALL.addAll(TIER_3);
        ALL.addAll(TIER_4);
        ALL.addAll(TIER_5);
    }

    /** 按里程取奖池档位（0~4）。 */
    public static int tierOf(long mileage) {
        if (mileage < 500) {
            return 0;
        }
        if (mileage < 1000) {
            return 1;
        }
        if (mileage < 2000) {
            return 2;
        }
        if (mileage < 3000) {
            return 3;
        }
        return 4;
    }

    public static List<Entry> pool(int tier) {
        return switch (Math.max(0, Math.min(4, tier))) {
            case 0 -> TIER_1;
            case 1 -> TIER_2;
            case 2 -> TIER_3;
            case 3 -> TIER_4;
            default -> TIER_5;
        };
    }

    /** 全档位合并池（超级抽奖用，不限里程）。 */
    public static List<Entry> allPools() {
        return ALL;
    }

    /**
     * 随机附魔书生成。必须使用发放对象的注册表（玩家/世界的活动注册表）——
     * 附魔是数据包注册表，静态默认表（VanillaRegistries）的 Holder 无法被网络编码，
     * 会导致 Failed to encode packet / 连接丢失。
     */
    public static ItemStack randomBook(net.minecraft.core.HolderLookup.Provider registries, int maxLevelCap) {
        String id = ENCHANT_POOL[RNG.nextInt(ENCHANT_POOL.length)];
        var registry = registries.lookupOrThrow(Registries.ENCHANTMENT);
        var key = net.minecraft.resources.ResourceKey.create(
            Registries.ENCHANTMENT, Identifier.withDefaultNamespace(id));
        Optional<Holder.Reference<Enchantment>> holder = registry.get(key);
        if (holder.isEmpty()) {
            return new ItemStack(Items.PAPER);
        }
        int max = Math.min(holder.get().value().getMaxLevel(), Math.max(1, maxLevelCap));
        int level = SINGLE_LEVEL.contains(id) ? 1 : 1 + RNG.nextInt(max);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(holder.get(), level);
        book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return book;
    }
}
