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
 * 滚轮选择、中键逐个领取、右键领取选中项并退出（丢弃未选中物品）。
 * 触发下一次抽奖时若有未领取奖励，视为放弃（直接丢弃，不发放）。
 */
public final class PendingRewardManager {
    private PendingRewardManager() {}

    private record Pending(int type, List<ItemStack> remaining) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** 超级抽奖进行中积攒的普通资源抽奖（仅保存最近一次，新的覆盖旧的）。 */
    private static final Map<UUID, Pending> QUEUED = new HashMap<>();

    /** 开启一轮新的待领取（若上一轮未领取完则视为放弃）。 */
    public static void start(ServerPlayer player, int type, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        // 超级抽奖优先级最高：进行中的超级抽奖不会被普通资源抽奖顶掉，普通抽奖改为积攒
        Pending existing = PENDING.get(player.getUUID());
        if (existing != null && existing.type() == LootRollPayload.TYPE_SUPER
                && type == LootRollPayload.TYPE_RESOURCE) {
            QUEUED.put(player.getUUID(), new Pending(type, new ArrayList<>(items)));
            player.sendSystemMessage(Component.literal(
                    "§7[猎人游戏] 超级抽奖进行中，本次资源抽奖已保存。"), true);
            return;
        }
        discardExisting(player);
        PENDING.put(player.getUUID(), new Pending(type, new ArrayList<>(items)));
        send(player, new LootRollPayload(type, items, accentOf(type), LootRollPayload.MODE_NEW));
    }

    /** 领取全部标记物品并关闭待领取，未标记物品丢弃（右键提交）。 */
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
        // 超级抽奖结束：开启积攒的普通资源抽奖
        Pending queued = QUEUED.remove(player.getUUID());
        if (queued != null) {
            PENDING.put(player.getUUID(), queued);
            send(player, new LootRollPayload(queued.type(), queued.remaining(), accentOf(queued.type()),
                LootRollPayload.MODE_NEW));
            player.sendSystemMessage(Component.literal(
                    "§7[猎人游戏] 积攒的资源抽奖已开启。"), true);
        }
    }

    /** 新一轮抽奖前：上一轮未领取的奖励视为放弃（不发放）。 */
    public static void discardExisting(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending != null) {
            player.sendSystemMessage(Component.literal("§7[猎人游戏] 上一轮抽奖的未领取奖励已放弃。"), true);
        }
    }

    /** 掉线补偿：未领取奖励（含积攒的抽奖）直接入包（掉线非玩家主动选择）。 */
    public static void depositExisting(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending != null) {
            for (ItemStack stack : pending.remaining()) {
                give(player, stack);
            }
        }
        Pending queued = QUEUED.remove(player.getUUID());
        if (queued != null) {
            for (ItemStack stack : queued.remaining()) {
                give(player, stack);
            }
        }
    }

    /** 逃生者淘汰：奖励作废（不入包）。 */
    public static void discard(UUID id) {
        PENDING.remove(id);
        QUEUED.remove(id);
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
        for (Map.Entry<UUID, Pending> e : QUEUED.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                for (ItemStack stack : e.getValue().remaining()) {
                    give(p, stack);
                }
            }
        }
        PENDING.clear();
        QUEUED.clear();
    }

    public static boolean hasPending(UUID id) {
        return PENDING.containsKey(id);
    }

    private static void sendRefresh(ServerPlayer player, Pending pending) {
        // mode=REFRESH：客户端在领取模式下原位刷新剩余物品；空列表则关闭领取界面
        send(player, new LootRollPayload(pending.type(), pending.remaining(), accentOf(pending.type()),
            LootRollPayload.MODE_REFRESH));
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
