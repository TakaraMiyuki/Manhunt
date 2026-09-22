package com.example.manhunt.loot;

import java.util.ArrayList;
import java.util.List;

import com.example.manhunt.GameConfig;
import com.example.manhunt.net.LootRollPayload;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * 抽奖执行：服务端先结算并入包，再向客户端发送展示动画数据。
 */
public final class LootRoller {
    private LootRoller() {}

    private static final RandomSource RNG = RandomSource.create();

    /** 资源抽奖：按玩家当前里程档位抽 {@code GameConfig.ROLL_ITEMS} 种。 */
    public static void resourceRoll(ServerPlayer player, long mileage) {
        List<RewardPools.Entry> pool = RewardPools.pool(RewardPools.tierOf(mileage));
        List<ItemStack> items = doRoll(pool, GameConfig.ROLL_ITEMS, player.registryAccess());
        deliver(player, LootRollPayload.TYPE_RESOURCE, items, 0xFF8B8B8B);
    }

    /** 超级抽奖：不限档位全池抽 {@code GameConfig.SUPER_ROLL_ITEMS} 种。 */
    public static void superRoll(ServerPlayer player) {
        List<ItemStack> items = doRoll(RewardPools.allPools(), GameConfig.SUPER_ROLL_ITEMS, player.registryAccess());
        deliver(player, LootRollPayload.TYPE_SUPER, items, 0xFFFFD700);
    }

    private static List<ItemStack> doRoll(List<RewardPools.Entry> pool, int count,
                                          net.minecraft.core.HolderLookup.Provider registries) {
        if (pool.isEmpty()) {
            return List.of();
        }
        List<ItemStack> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(pool.get(RNG.nextInt(pool.size())).roll(registries));
        }
        return items;
    }

    private static void deliver(ServerPlayer player, int type, List<ItemStack> items, int accent) {
        if (items.isEmpty()) {
            return;
        }
        // 展示用副本：入包合并可能改变原 stack 的数量
        List<ItemStack> display = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            display.add(stack.copy());
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        CustomPacketPayload payload = new LootRollPayload(type, display, accent);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
    }
}
