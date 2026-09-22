package com.example.manhunt.recipe;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

/**
 * 合成栏附魔：附魔书 + 可附魔装备 → 附魔后的装备（无需铁砧与等级）。
 * 同名附魔等级相同则 +1，不同取高者，均不超过该附魔的最高等级。
 */
public final class EnchantBookRecipe extends CustomRecipe {
    public static final EnchantBookRecipe INSTANCE = new EnchantBookRecipe();
    public static final MapCodec<EnchantBookRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantBookRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<EnchantBookRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private record Inputs(ItemStack target, ItemStack book) {}

    private static Inputs findInputs(CraftingInput input) {
        if (input.ingredientCount() != 2) {
            return null;
        }
        ItemStack target = null;
        ItemStack book = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(Items.ENCHANTED_BOOK)) {
                ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
                if (book != null || stored == null || stored.isEmpty()) {
                    return null;
                }
                book = stack;
            } else {
                if (target != null || stack.get(DataComponents.ENCHANTABLE) == null) {
                    return null;
                }
                target = stack;
            }
        }
        return target != null && book != null ? new Inputs(target, book) : null;
    }

    private EnchantBookRecipe() {
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findInputs(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Inputs inputs = findInputs(input);
        if (inputs == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = inputs.target().copy();
        ItemEnchantments bookEnchants = inputs.book().get(DataComponents.STORED_ENCHANTMENTS);
        if (bookEnchants == null) {
            return ItemStack.EMPTY;
        }
        EnchantmentHelper.updateEnchantments(result, mutable -> {
            for (Holder<Enchantment> holder : bookEnchants.keySet()) {
                int bookLevel = bookEnchants.getLevel(holder);
                int current = mutable.getLevel(holder);
                int merged = current == bookLevel ? current + 1 : Math.max(current, bookLevel);
                mutable.set(holder, Math.min(merged, holder.value().getMaxLevel()));
            }
        });
        return result;
    }

    @Override
    public RecipeSerializer<EnchantBookRecipe> getSerializer() {
        return SERIALIZER;
    }
}
