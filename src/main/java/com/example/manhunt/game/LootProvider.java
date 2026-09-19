package com.example.manhunt.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.manhunt.GameConfig;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 检查点物资表。稀有度随检查点序号递增。
 */
public final class LootProvider {
    private LootProvider() {}

    private static final RandomSource RNG = RandomSource.create();

    /** 常用正面附魔，用于随机附魔书。 */
    private static final String[] ENCHANT_POOL = {
        "sharpness", "smite", "efficiency", "unbreaking", "protection", "power", "punch",
        "feather_falling", "looting", "fortune", "silk_touch", "mending", "depth_strider",
        "soul_speed", "swift_sneak", "quick_charge", "impaling", "loyalty", "thorns",
        "respiration", "aqua_affinity", "frost_walker", "projectile_protection", "fire_protection",
        "infinity", "flame", "knockback", "fire_aspect", "luck_of_the_sea", "lure", "sweeping_edge"
    };
    /** 最高只有 1 级的附魔。 */
    private static final List<String> SINGLE_LEVEL = List.of("silk_touch", "mending", "infinity", "aqua_affinity");

    public static void giveCheckpointLoot(ServerPlayer runner, int checkpointNumber) {
        switch (checkpointNumber) {
            case 1 -> {
                // 基础生存物资
                rollLoot(runner, 3,
                    new ItemStack(Items.COOKED_BEEF, 8),
                    new ItemStack(Items.BREAD, 8),
                    new ItemStack(Items.IRON_INGOT, 5),
                    new ItemStack(Items.COAL, 16),
                    new ItemStack(Items.ENDER_PEARL, 1),
                    new ItemStack(Items.IRON_PICKAXE, 1),
                    new ItemStack(Items.TORCH, 32),
                    new ItemStack(Items.OAK_PLANKS, 32));
            }
            case 2 -> {
                // 进阶装备
                rollLoot(runner, 3,
                    new ItemStack(Items.DIAMOND, 3),
                    new ItemStack(Items.GOLDEN_APPLE, 2),
                    potion(Potions.STRONG_STRENGTH),
                    new ItemStack(Items.BOW, 1),
                    new ItemStack(Items.ARROW, 32),
                    new ItemStack(Items.GOLD_INGOT, 8),
                    new ItemStack(Items.IRON_CHESTPLATE, 1),
                    new ItemStack(Items.EXPERIENCE_BOTTLE, 12));
            }
            case 3 -> {
                // 附魔与稀有物资
                giveEnchantedBooks(runner, GameConfig.LOOT_ROLLS_PER_CHECKPOINT);
                rollLoot(runner, 2,
                    new ItemStack(Items.DIAMOND, 5),
                    new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1),
                    new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                    new ItemStack(Items.ARROW, 64),
                    new ItemStack(Items.IRON_BLOCK, 8),
                    new ItemStack(Items.OBSIDIAN, 8));
            }
            case 4 -> {
                // 固定：鞘翅 + 末影之眼（激活要塞传送门），外加随机补给
                give(runner, new ItemStack(Items.ELYTRA, 1));
                give(runner, new ItemStack(Items.ENDER_EYE, 12));
                rollLoot(runner, 2,
                    new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1),
                    new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                    new ItemStack(Items.DIAMOND, 5),
                    new ItemStack(Items.GOLDEN_APPLE, 3),
                    new ItemStack(Items.ARROW, 64));
            }
            default -> {
            }
        }
    }

    private static void rollLoot(ServerPlayer runner, int rolls, ItemStack... pool) {
        List<ItemStack> entries = new ArrayList<>(List.of(pool));
        for (int i = 0; i < rolls && !entries.isEmpty(); i++) {
            ItemStack picked = entries.remove(RNG.nextInt(entries.size()));
            give(runner, picked);
        }
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) {
            p.drop(stack, false);
        }
    }

    private static ItemStack potion(Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return stack;
    }

    /** 随机附魔书（I~III 级，受附魔自身上限约束）。 */
    public static void giveEnchantedBooks(ServerPlayer runner, int count) {
        var registry = runner.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (int i = 0; i < count; i++) {
            String id = ENCHANT_POOL[RNG.nextInt(ENCHANT_POOL.length)];
            Optional<Holder.Reference<Enchantment>> holder =
                registry.get(Identifier.withDefaultNamespace(id));
            if (holder.isEmpty()) {
                continue;
            }
            int max = holder.get().value().getMaxLevel();
            int level = SINGLE_LEVEL.contains(id) ? 1 : Math.min(max, 1 + RNG.nextInt(3));
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            mutable.set(holder.get(), level);
            book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
            give(runner, book);
        }
    }
}
