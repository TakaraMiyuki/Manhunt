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
 * 抽奖奖励待领取仓库：抽奖结果不再直接入包，而是停留在客户端抽奖 UI 上，
 * 玩家滚轮选择、中键逐个领取、右键一键收取全部。
 * 触发下一次抽奖时若有未领取奖励，自动全部入包（强制放弃选择流程）。
 */
public final class PendingRewardManager {
    private PendingRewardManager() {}

    private record Pending(int type, List<ItemStack> remaining) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    /** 开启一轮新的待领取（若上一轮未领完则自动入包）。 */
    public static void start(ServerPlayer player, int type, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        autoClaimExisting(player);
        PENDING.put(player.getUUID(), new Pending(type, new ArrayList<>(items)));
        send(player, new LootRollPayload(type, items, accentOf(type), LootRollPayload.MODE_NEW));
    }

    /** 领取选中的一项（C2S 请求）。 */
    public static void claimOne(ServerPlayer player, int index) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || index < 0 || index >= pending.remaining().size()) {
            return;
        }
        ItemStack stack = pending.remaining().remove(index);
        give(player, stack);
        player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 1.3F);
        if (pending.remaining().isEmpty()) {
            PENDING.remove(player.getUUID());
        }
        sendRefresh(player, pending);
    }

    /** 收取全部剩余（右键退出 / 主动请求）。 */
    public static void claimAll(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            return;
        }
        for (ItemStack stack : pending.remaining()) {
            give(player, stack);
        }
        player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 1.0F);
    }

    /** 新一轮抽奖前/掉线时：把未领取奖励直接入包。 */
    public static void autoClaimExisting(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            return;
        }
        for (ItemStack stack : pending.remaining()) {
            give(player, stack);
        }
        player.sendSystemMessage(Component.literal("§7[猎人游戏] 上一轮抽奖奖励已自动放入背包。"));
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
