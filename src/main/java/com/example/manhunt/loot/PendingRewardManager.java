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
 * 积攒规则：
 * <ul>
 *   <li>里程值资源抽奖（{@code fromMileage=true}）之间**相互覆盖**——当前轮未领取时又攒满一轮
 *       里程抽奖，则放弃当前轮，新轮立即开启；</li>
 *   <li>其余来源（超级抽奖、补给光柱等）与当前轮/队列之间一律**顺延**，非批量到达时顺延槽位
 *       最多一个（新的顶替旧的）；</li>
 *   <li>同一刻批量到达（如赏金量表一次连升数档触发的多次超级抽奖）**全部保留**、依次开启。</li>
 * </ul>
 */
public final class PendingRewardManager {
    private PendingRewardManager() {}

    private record Pending(int type, List<ItemStack> remaining, int accentColor, String title,
                           boolean fromMileage) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** 等待队列：当前轮领取后按序自动开启（批量到达可多条，非批量最多顺延一个）。 */
    private static final Map<UUID, List<Pending>> QUEUED = new HashMap<>();
    /** 批量到达判定：同刻的上一次 start 时刻。 */
    private static final Map<UUID, Long> LAST_START_TICK = new HashMap<>();

    /** 里程值资源抽奖（来源=里程累计）。 */
    public static void startMileageRoll(ServerPlayer player, int type, List<ItemStack> items) {
        start(player, type, items, accentOf(type), null, true);
    }

    /** 其他来源的抽奖（超级抽奖、补给光柱等）。 */
    public static void start(ServerPlayer player, int type, List<ItemStack> items) {
        start(player, type, items, accentOf(type), null, false);
    }

    /** 其他来源的抽奖：带强调色版本。 */
    public static void start(ServerPlayer player, int type, List<ItemStack> items, int accentColor) {
        start(player, type, items, accentColor, null, false);
    }

    /**
     * 带强调色与自定义标题的版本：抽奖 UI 边框/品级色使用调用方指定的 ARGB 颜色，
     * 标题使用指定翻译键（两者在待领取全程保持，排队重开亦然）。
     *
     * @param fromMileage 是否为里程值资源抽奖（里程轮之间相互覆盖，其余来源顺延）
     */
    public static void start(ServerPlayer player, int type, List<ItemStack> items, int accentColor, String title,
                             boolean fromMileage) {
        if (items.isEmpty()) {
            return;
        }
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        boolean batch = LAST_START_TICK.getOrDefault(id, -1L) == now; // 同刻多次到达 = 批量
        LAST_START_TICK.put(id, now);
        Pending existing = PENDING.get(id);
        if (existing != null && fromMileage && existing.fromMileage()) {
            // 里程抽奖相互覆盖：放弃当前轮（含队列中遗留的里程轮），新轮立即开启
            PENDING.remove(id);
            QUEUED.getOrDefault(id, new ArrayList<>()).removeIf(Pending::fromMileage);
        } else if (existing != null) {
            // 顺延：新轮排到当前轮后面
            List<Pending> queue = QUEUED.computeIfAbsent(id, k -> new ArrayList<>());
            Pending queued = new Pending(type, new ArrayList<>(items), accentColor, title, fromMileage);
            if (batch || queue.isEmpty()) {
                queue.add(queued); // 批量到达全部保留
            } else if (queue.size() == 1) {
                queue.set(0, queued); // 非批量：顺延槽位最多一个，新的顶替旧的
            } else {
                queue.set(queue.size() - 1, queued);
            }
            player.sendSystemMessage(Component.literal(
                "§7[猎人游戏] 新一轮抽奖已顺延在当前轮之后（待领取 " + (queue.size() + 1) + " 轮）。"), true);
            return; // 当前轮保持不动
        }
        PENDING.put(id, new Pending(type, new ArrayList<>(items), accentColor, title, fromMileage));
        send(player, new LootRollPayload(type, items, accentColor, LootRollPayload.MODE_NEW, title));
    }

    /** 领取全部标记物品并关闭当前轮，未标记物品丢弃（右键/↓ 提交）；队列自动开启下一轮。 */
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
        openNextQueued(player);
    }

    /** 队列非空则自动开启下一轮（保留其强调色与标题）。 */
    private static void openNextQueued(ServerPlayer player) {
        List<Pending> queue = QUEUED.get(player.getUUID());
        if (queue == null || queue.isEmpty()) {
            return;
        }
        Pending next = queue.remove(0);
        PENDING.put(player.getUUID(), next);
        send(player, new LootRollPayload(next.type(), next.remaining(), next.accentColor(),
            LootRollPayload.MODE_NEW, next.title()));
        player.sendSystemMessage(Component.literal("§7[猎人游戏] 下一轮抽奖已开启。"), true);
    }

    /** 新一轮抽奖前：上一轮未领取的奖励视为放弃（不发放）。 */
    public static void discardExisting(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending != null) {
            player.sendSystemMessage(Component.literal("§7[猎人游戏] 上一轮抽奖的未领取奖励已放弃。"), true);
        }
    }

    /** 掉线补偿：当前轮与全部排队轮次直接入包（掉线非玩家主动选择）。 */
    public static void depositExisting(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending != null) {
            for (ItemStack stack : pending.remaining()) {
                give(player, stack);
            }
        }
        List<Pending> queue = QUEUED.remove(player.getUUID());
        if (queue != null) {
            for (Pending p : queue) {
                for (ItemStack stack : p.remaining()) {
                    give(player, stack);
                }
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
        for (Map.Entry<UUID, List<Pending>> e : QUEUED.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                for (Pending pending : e.getValue()) {
                    for (ItemStack stack : pending.remaining()) {
                        give(p, stack);
                    }
                }
            }
        }
        PENDING.clear();
        QUEUED.clear();
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
