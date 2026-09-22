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

    /** 开启一轮新的待领取（若上一轮未领取完则视为放弃）。 */
    public static void start(ServerPlayer player, int type, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        discardExisting(player);
        PENDING.put(player.getUUID(), new Pending(type, new ArrayList<>(items)));
        send(player, new LootRollPayload(type, items, accentOf(type), LootRollPayload.MODE_NEW));
    }

    /** 领取选中的一项，其余保留（中键/回车）。 */
    public static void claimOne(ServerPlayer player, int index) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || index < 0 || index >= pending.remaining().size()) {
            return;
        }
        give(player, pending.remaining().remove(index));
        player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 1.3F);
        if (pending.remaining().isEmpty()) {
            PENDING.remove(player.getUUID());
        }
        sendRefresh(player, pending);
    }

    /** 领取选中的一项并退出选择阶段，丢弃未选中的物品（右键）。 */
    public static void claimAndExit(ServerPlayer player, int index) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null || index < 0 || index >= pending.remaining().size()) {
            return;
        }
        give(player, pending.remaining().get(index));
        player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 1.0F);
    }

    /** 新一轮抽奖前：上一轮未领取的奖励视为放弃（不发放）。 */
    public static void discardExisting(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending != null) {
            player.sendSystemMessage(Component.literal("§7[猎人游戏] 上一轮抽奖的未领取奖励已放弃。"), true);
        }
    }

    /** 掉线补偿：未领取奖励直接入包（掉线非玩家主动选择）。 */
    public static void depositExisting(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            return;
        }
        for (ItemStack stack : pending.remaining()) {
            give(player, stack);
        }
    }

    /** 逃生者淘汰：奖励作废（不入包）。 */
    public static void discard(UUID id) {
        PENDING.remove(id);
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
        PENDING.clear();
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
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static int accentOf(int type) {
        return type == LootRollPayload.TYPE_SUPER ? 0xFFFFD700 : 0xFF8B8B8B;
    }
}
