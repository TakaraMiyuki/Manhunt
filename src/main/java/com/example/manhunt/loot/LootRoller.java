package com.example.manhunt.loot;

import java.util.ArrayList;
import java.util.List;

import com.example.manhunt.GameConfig;
import com.example.manhunt.net.LootRollPayload;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * 抽奖执行：服务端结算并进入待领取仓库，客户端播放"老虎机"动画后由玩家领取。
 */
public final class LootRoller {
    private LootRoller() {}

    private static final RandomSource RNG = RandomSource.create();

    /** 资源抽奖：按玩家当前里程档位抽 {@code GameConfig.ROLL_ITEMS} 种。 */
    public static void resourceRoll(ServerPlayer player, long mileage) {
        List<RewardPools.Entry> pool = RewardPools.pool(RewardPools.tierOf(mileage));
        List<ItemStack> items = doRoll(pool, GameConfig.ROLL_ITEMS, player.registryAccess());
        PendingRewardManager.start(player, LootRollPayload.TYPE_RESOURCE, items);
    }

    /** 超级抽奖：从当前里程档及以上的池抽 {@code GameConfig.SUPER_ROLL_ITEMS} 种（不出现更差的物资）。 */
    public static void superRoll(ServerPlayer player) {
        int tier = RewardPools.tierOf(com.example.manhunt.game.MileageManager.mileage(player.getUUID()));
        List<ItemStack> items = doRoll(RewardPools.poolsFrom(tier), GameConfig.SUPER_ROLL_ITEMS, player.registryAccess());
        PendingRewardManager.start(player, LootRollPayload.TYPE_SUPER, items);
    }

    private static List<ItemStack> doRoll(List<RewardPools.Entry> pool, int count,
                                          net.minecraft.core.HolderLookup.Provider registries) {
        if (pool.isEmpty()) {
            return List.of();
        }
        List<ItemStack> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(RewardPools.weightedPick(pool).roll(registries));
        }
        return items;
    }
}
