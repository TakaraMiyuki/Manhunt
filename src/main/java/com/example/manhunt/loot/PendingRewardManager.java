package com.example.manhunt.loot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.manhunt.net.LootRollPayload;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 抽奖奖励待领取仓库：抽奖结果停留在客户端抽奖 UI 上，
 * 滚轮选择、方向键/中键标记、右键/↓ 领取标记物品并退出（丢弃未选中物品）。
 *
 * 优先级规则：超级抽奖优先级最高——
 * <ul>
 *   <li>超级进行中触发的普通抽奖 → 积攒（仅保留最近一次，新的覆盖旧的），超级结束后自动开启；</li>
 *   <li>普通领取中被超级顶替的普通抽奖 → 保留，超级结束后自动重新开启；</li>
 *   <li>同类型新一轮 → 旧一轮未领取奖励视为放弃。</li>
 * </ul>
 */
public final class PendingRewardManager {
    private PendingRewardManager() {}

    private record Pending(int type, List<ItemStack> remaining) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** 被顶替/积攒而延迟开启的普通资源抽奖（仅一个槽位）。 */
    private static final Map<UUID, Pending> DEFERRED = new HashMap<>();

    /** 开启一轮新的待领取，并按优先级处理被顶替的轮次。 */
    public static void start(ServerPlayer player, int type, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        Pending existing = PENDING.get(player.getUUID());
        if (existing != null && existing.type() != type) {
            // 异类型相遇：超级立即开启，被顶替的普通抽奖保留，超级结束后自动重启
            DEFERRED.put(player.getUUID(), existing);
            PENDING.remove(player.getUUID());
        } else if (existing != null) {
            player.sendSystemMessage(Component.literal(
                    "§7[猎人游戏] 上一轮抽奖的未领取奖励已放弃。"), true);
        }
        PENDING.put(player.getUUID(), new Pending(type, new ArrayList<>(items)));
        send(player, new LootRollPayload(type, items, accentOf(type), LootRollPayload.MODE_NEW));
    }

    /** 领取全部标记物品并关闭待领取，未标记物品丢弃（右键/↓ 提交）。 */
    public static void claimMarked(ServerPlayer player, List<Integer> indices) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            return;
        }
        int claimed = 0;
        for (int index : indices) {
            if (index >= 0 && index < pending.remaining().size()) {
                give(player, pending.remaining().get(index));
                claimed++;
            }
        }
        if (claimed > 0) {
            player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 1.0F);
        }
        // 被顶替而保留的普通抽奖：超级结束后自动重启
        Pending deferred = DEFERRED.remove(player.getUUID());
        if (deferred != null) {
            PENDING.put(player.getUUID(), deferred);
            send(player, new LootRollPayload(deferred.type(), deferred.remaining(), accentOf(deferred.type()),
                LootRollPayload.MODE_NEW));
            player.sendSystemMessage(Component.literal(
                    "§7[猎人游戏] 被保留的资源抽奖已开启。"), true);
        }
    }

    /** 新一轮抽奖前：上一轮未领取的奖励视为放弃（不发放）。 */
    public static void discardExisting(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending != null) {
            player.sendSystemMessage(Component.literal("§7[猎人游戏] 上一轮抽奖的未领取奖励已放弃。"), true);
        }
    }

    /** 掉线补偿：未领取与被保留的奖励直接入包（掉线非玩家主动选择）。 */
    public static void depositExisting(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending != null) {
            for (ItemStack stack : pending.remaining()) {
                give(player, stack);
            }
        }
        Pending deferred = DEFERRED.remove(player.getUUID());
        if (deferred != null) {
            for (ItemStack stack : deferred.remaining()) {
                give(player, stack);
            }
        }
    }

    /** 逃生者淘汰：奖励作废（不入包）。 */
    public static void discard(UUID id) {
        PENDING.remove(id);
        DEFERRED.remove(id);
    }

    /** 游戏结束：在线参与者全部强制入包。 */
    public static void depositAll(MinecraftServer server) {
        for (Map.Entry<UUID, Pending> e : PENDING.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                for (ItemStack stack : e.getValue().remaining()) {
                    give(p, stack);
                }
            }
        }
        for (Map.Entry<UUID, Pending> e : DEFERRED.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                for (ItemStack stack : e.getValue().remaining()) {
                    give(p, stack);
                }
            }
        }
        PENDING.clear();
        DEFERRED.clear();
    }

    public static boolean hasPending(UUID id) {
        return PENDING.containsKey(id);
    }

    private static void send(ServerPlayer player, LootRollPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!com.example.manhunt.util.InvUtil.safeAdd(player, stack)) {
            player.drop(stack, false);
        }
    }

    private static int accentOf(int type) {
        return type == LootRollPayload.TYPE_SUPER ? 0xFFFFD700 : 0xFF8B8B8B;
    }
}
