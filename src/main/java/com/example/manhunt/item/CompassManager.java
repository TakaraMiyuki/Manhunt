package com.example.manhunt.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

import com.example.manhunt.GameConfig;
import com.example.manhunt.game.ManhuntGame;
import com.example.manhunt.game.TeamUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 两类罗盘的发放、指向刷新与目标切换。
 * 指向通过每秒写入 LODESTONE_TRACKER 组件实现（客户端按磁石罗盘渲染）。
 */
public final class CompassManager {
    private CompassManager() {}

    private static final String TARGET_TAG = "manhunt_target";

    /**
     * 罗盘指向装饰器（供其他模组扩展，如技能卡"盲点"的干扰效果）：
     * 输入罗盘持有者 UUID 与原目标，返回替换目标；返回 null 表示不干预。
     */
    public static BiFunction<UUID, GlobalPos, GlobalPos> targetDecorator = null;

    // ==================== 发放 ====================

    public static void giveTrackingCompass(ServerPlayer hunter) {
        removeOld(hunter, ManhuntItems.TRACKING_COMPASS.get());
        ItemStack compass = new ItemStack(ManhuntItems.TRACKING_COMPASS.get());
        compass.set(DataComponents.CUSTOM_NAME, Component.literal("§c追踪罗盘 §7(右键切换目标)"));
        if (!com.example.manhunt.util.InvUtil.safeAdd(hunter, compass)) {
            hunter.drop(compass, false);
        }
    }

    public static void giveCheckpointCompass(ServerPlayer runner) {
        removeOld(runner, ManhuntItems.CHECKPOINT_COMPASS.get());
        ItemStack compass = new ItemStack(ManhuntItems.CHECKPOINT_COMPASS.get());
        compass.set(DataComponents.CUSTOM_NAME, Component.literal("§e检查点罗盘"));
        if (!com.example.manhunt.util.InvUtil.safeAdd(runner, compass)) {
            runner.drop(compass, false);
        }
    }

    private static void removeOld(ServerPlayer p, net.minecraft.world.item.Item item) {
        var inventory = p.getInventory();
        var items = inventory.getNonEquipmentItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).is(item)) {
                items.set(i, ItemStack.EMPTY);
            }
        }
    }

    /** 按阵营补发对应罗盘（登录/复活时调用）。 */
    public static void ensureCompasses(ServerPlayer p) {
        if (TeamUtil.isHunter(p)) {
            boolean has = false;
            var items = p.getInventory().getNonEquipmentItems();
            for (ItemStack stack : items) {
                if (stack.is(ManhuntItems.TRACKING_COMPASS.get())) {
                    has = true;
                    break;
                }
            }
            if (!has) {
                giveTrackingCompass(p);
            }
        } else if (TeamUtil.isRunner(p) && !ManhuntGame.isEliminated(p.getUUID())) {
            boolean has = false;
            var items = p.getInventory().getNonEquipmentItems();
            for (ItemStack stack : items) {
                if (stack.is(ManhuntItems.CHECKPOINT_COMPASS.get())) {
                    has = true;
                    break;
                }
            }
            if (!has) {
                giveCheckpointCompass(p);
            }
        }
    }

    // ==================== 每秒指向刷新 ====================

    public static void updateAll(MinecraftServer server) {
        List<ServerPlayer> runners = ManhuntGame.onlineAliveRunners(server);

        // 猎人罗盘 → 所选逃生者
        for (ServerPlayer hunter : server.getPlayerList().getPlayers()) {
            if (!TeamUtil.isHunter(hunter)) {
                continue;
            }
            forEachStack(hunter, ManhuntItems.TRACKING_COMPASS.get(), stack -> {
                ServerPlayer target = resolveTarget(runners, readTarget(stack));
                if (target == null) {
                    return;
                }
                GlobalPos pos;
                if (target.level().dimension() == Level.END) {
                    // 目标进入末地：罗盘指向末地传送门（猎人在主世界时指向要塞传送门房间，
                    // 猎人也已进入末地时指向末地中央出口传送门）
                    if (hunter.level().dimension() == Level.END) {
                        pos = GlobalPos.of(Level.END, BlockPos.ZERO);
                    } else {
                        GlobalPos portal = ManhuntGame.strongholdPortalPos();
                        pos = portal != null ? portal
                            : GlobalPos.of(Level.OVERWORLD, ManhuntGame.lastCheckpoint() != null
                                ? ManhuntGame.lastCheckpoint() : target.blockPosition());
                    }
                } else {
                    pos = decorate(hunter.getUUID(),
                        GlobalPos.of(target.level().dimension(), target.blockPosition()));
                }
                setTracker(stack, pos);
            });
        }

        // 逃生者罗盘 → 当前目标检查点（主世界）；进入末地阶段则指向最近激活检查点
        BlockPos objective = ManhuntGame.currentCheckpoint();
        if (objective == null) {
            objective = ManhuntGame.lastCheckpoint();
        }
        if (objective != null) {
            GlobalPos target = GlobalPos.of(Level.OVERWORLD, objective);
            for (ServerPlayer runner : runners) {
                forEachStack(runner, ManhuntItems.CHECKPOINT_COMPASS.get(), stack -> setTracker(stack, target));
            }
        }
    }

    private static GlobalPos decorate(UUID holder, GlobalPos original) {
        if (targetDecorator == null) {
            return original;
        }
        GlobalPos replaced = targetDecorator.apply(holder, original);
        return replaced == null ? original : replaced;
    }

    private static ServerPlayer resolveTarget(List<ServerPlayer> runners, Optional<UUID> preferred) {
        if (preferred.isPresent()) {
            for (ServerPlayer r : runners) {
                if (r.getUUID().equals(preferred.get())) {
                    return r;
                }
            }
        }
        return runners.isEmpty() ? null : runners.get(0);
    }

    private static Optional<UUID> readTarget(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }
        CompoundTag tag = data.copyTag();
        String value = tag.getStringOr(TARGET_TAG, "");
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static void writeTarget(ItemStack stack, UUID target) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TARGET_TAG, target.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void setTracker(ItemStack stack, GlobalPos target) {
        LodestoneTracker current = stack.get(DataComponents.LODESTONE_TRACKER);
        if (current != null && current.target().isPresent() && current.target().get().equals(target)) {
            return; // 值未变化时不重写，避免每秒同步物品数据
        }
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(target), false));
    }

    private interface StackConsumer {
        void accept(ItemStack stack);
    }

    /** 遍历背包主物品栏 + 副手。 */
    private static void forEachStack(ServerPlayer p, net.minecraft.world.item.Item item, StackConsumer consumer) {
        var inventory = p.getInventory();
        var items = inventory.getNonEquipmentItems();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.is(item)) {
                consumer.accept(stack);
            }
        }
        ItemStack offhand = p.getItemInHand(InteractionHand.OFF_HAND);
        if (offhand.is(item)) {
            consumer.accept(offhand);
        }
    }

    // ==================== 右键切换追踪目标 ====================

    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer hunter)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!stack.is(ManhuntItems.TRACKING_COMPASS.get())) {
            return;
        }
        if (!ManhuntGame.isRunning()) {
            return;
        }
        List<ServerPlayer> runners = ManhuntGame.onlineAliveRunners(hunter.level().getServer());
        if (runners.isEmpty()) {
            hunter.sendSystemMessage(Component.literal("§7当前没有可追踪的逃生者。"), true);
            event.setCanceled(true);
            return;
        }
        Optional<UUID> current = readTarget(stack);
        int next = 0;
        for (int i = 0; i < runners.size(); i++) {
            if (runners.get(i).getUUID().equals(current.orElse(null))) {
                next = (i + 1) % runners.size();
                break;
            }
        }
        ServerPlayer target = runners.get(next);
        writeTarget(stack, target.getUUID());
        setTracker(stack, GlobalPos.of(target.level().dimension(), target.blockPosition()));
        hunter.sendSystemMessage(Component.literal(
            "§c追踪目标 → §f" + target.getName().getString()), true);
        event.setCanceled(true);
    }
}
