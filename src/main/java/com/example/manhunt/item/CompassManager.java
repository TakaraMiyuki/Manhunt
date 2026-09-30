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
    /** 赏金模式：检查点罗盘手动选择的检查点（"x,y,z"）。 */
    private static final String CP_CHOICE_TAG = "manhunt_cp_choice";

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

    /** 罗盘不可丢弃（取消抛掷，防止掉落被他人拾取）。 */
    public static void onItemToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        if (!ManhuntGame.isRunning()) {
            return;
        }
        var player = event.getPlayer();
        if (!ManhuntGame.isParticipant(player.getUUID())) {
            return;
        }
        ItemStack tossed = event.getEntity().getItem();
        if (tossed.is(ManhuntItems.TRACKING_COMPASS.get()) || tossed.is(ManhuntItems.CHECKPOINT_COMPASS.get())) {
            event.setCanceled(true);
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§7[猎人游戏] 罗盘无法丢弃。"), true);
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
        // 赏金模式：赦免中的逃生者不可被罗盘追踪
        if (ManhuntGame.isBounty()) {
            runners.removeIf(r -> ManhuntGame.isCompassImmune(r.getUUID(), server));
        }

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

        // 逃生者罗盘：经典 → 当前目标检查点；赏金 → 手动选择的检查点（默认最近）
        if (ManhuntGame.isBounty()) {
            for (ServerPlayer runner : runners) {
                BlockPos manual = readCpChoice(runner, ManhuntItems.CHECKPOINT_COMPASS.get());
                if (manual != null && !ManhuntGame.bountyCheckpoints().contains(manual)) {
                    clearCpChoice(runner, ManhuntItems.CHECKPOINT_COMPASS.get());
                    manual = null;
                }
                BlockPos nearest = ManhuntGame.currentCheckpoint() != null
                    ? ManhuntGame.currentCheckpoint()
                    : com.example.manhunt.game.CheckpointManager.nearestBountyCheckpoint(
                        server.overworld(), runner);
                BlockPos target = manual != null ? manual : nearest;
                if (target == null) {
                    continue;
                }
                GlobalPos pos = GlobalPos.of(Level.OVERWORLD, target);
                forEachStack(runner, ManhuntItems.CHECKPOINT_COMPASS.get(), stack -> setTracker(stack, pos));
            }
            return;
        }
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

    /** 赏金模式：逃生者右键检查点罗盘 → 循环切换追踪的检查点，并显示实际距离。 */
    private static void onCheckpointCompassRightClick(PlayerInteractEvent.RightClickItem event, ItemStack stack) {
        if (!(event.getEntity() instanceof ServerPlayer runner)) {
            return;
        }
        if (!ManhuntGame.isBounty() || !ManhuntGame.isRunning()) {
            return;
        }
        java.util.List<BlockPos> cps = ManhuntGame.bountyCheckpoints();
        if (ManhuntGame.currentCheckpoint() != null) {
            // 要塞阶段：只有一个目标，刷新指向并显示距离
            BlockPos stronghold = ManhuntGame.currentCheckpoint();
            setTracker(stack, GlobalPos.of(Level.OVERWORLD, stronghold));
            runner.sendSystemMessage(Component.literal("§d检查点 → 末地要塞 §7(距离 §f"
                + horizDist(runner, stronghold) + "m§7)"), true);
            event.setCanceled(true);
            return;
        }
        if (cps.isEmpty()) {
            runner.sendSystemMessage(Component.literal("§7当前没有可追踪的检查点。"), true);
            event.setCanceled(true);
            return;
        }
        BlockPos manual = readCpChoice(runner, ManhuntItems.CHECKPOINT_COMPASS.get());
        int idx = 0;
        double bestSq = Double.MAX_VALUE;
        for (int i = 0; i < cps.size(); i++) {
            BlockPos cp = cps.get(i);
            if (cp.equals(manual)) {
                idx = i;
                break;
            }
            double dx = runner.getX() - (cp.getX() + 0.5);
            double dz = runner.getZ() - (cp.getZ() + 0.5);
            double d2 = dx * dx + dz * dz;
            if (d2 < bestSq) {
                bestSq = d2;
                idx = i; // 无手动选择时从最近的下一个开始
            }
        }
        int next = (idx + 1) % cps.size();
        BlockPos target = cps.get(next);
        writeCpChoice(stack, target);
        setTracker(stack, GlobalPos.of(Level.OVERWORLD, target));
        runner.sendSystemMessage(Component.literal(
            "§e检查点 → §f" + target.getX() + ", " + target.getY() + ", " + target.getZ()
                + " §7(距离 §f" + horizDist(runner, target) + "m§7"
                + " §7| 第 " + (next + 1) + "/" + cps.size() + " 个)"), true);
        event.setCanceled(true);
    }

    private static int horizDist(ServerPlayer p, BlockPos pos) {
        double dx = p.getX() - (pos.getX() + 0.5);
        double dz = p.getZ() - (pos.getZ() + 0.5);
        return (int) Math.sqrt(dx * dx + dz * dz);
    }

    private static BlockPos readCpChoice(ServerPlayer p, net.minecraft.world.item.Item item) {
        BlockPos[] out = {null};
        forEachStack(p, item, stack -> {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) {
                return;
            }
            String value = data.copyTag().getStringOr(CP_CHOICE_TAG, "");
            if (value.isEmpty()) {
                return;
            }
            String[] parts = value.split(",");
            if (parts.length == 3) {
                try {
                    out[0] = new BlockPos(Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        return out[0];
    }

    private static void writeCpChoice(ItemStack stack, BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString(CP_CHOICE_TAG, pos.getX() + "," + pos.getY() + "," + pos.getZ());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void clearCpChoice(ServerPlayer p, net.minecraft.world.item.Item item) {
        forEachStack(p, item, stack -> {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            CompoundTag tag = data != null ? data.copyTag() : new CompoundTag();
            if (tag.contains(CP_CHOICE_TAG)) {
                tag.remove(CP_CHOICE_TAG);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
        });
    }

    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer hunter)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.is(ManhuntItems.CHECKPOINT_COMPASS.get())) {
            onCheckpointCompassRightClick(event, stack);
            return;
        }
        if (!stack.is(ManhuntItems.TRACKING_COMPASS.get())) {
            return;
        }
        if (!ManhuntGame.isRunning()) {
            return;
        }
        List<ServerPlayer> runners = ManhuntGame.onlineAliveRunners(hunter.level().getServer());
        if (ManhuntGame.isBounty()) {
            runners.removeIf(r -> ManhuntGame.isCompassImmune(r.getUUID(), hunter.level().getServer()));
        }
        if (runners.isEmpty()) {
            hunter.sendSystemMessage(Component.literal(ManhuntGame.isBounty()
                ? "§7当前没有可追踪的逃生者（可能处于赦免期）。" : "§7当前没有可追踪的逃生者。"), true);
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
        if (ManhuntGame.isBounty()) {
            long base = com.example.manhunt.game.MileageManager.mileage(target.getUUID())
                / GameConfig.BOUNTY_KILL_DIVISOR;
            int rank = com.example.manhunt.game.BountyManager.markRank(target.getUUID());
            boolean marked = com.example.manhunt.game.BountyManager.isMarked(target.getUUID());
            double mult = com.example.manhunt.game.BountyManager.killMultiplier(
                hunter.level().getServer(), target.getUUID());
            String info = marked
                ? "§6第 " + rank + " 名 §7| §6赏金 §f" + base + " §7(×" + (mult == Math.floor(mult)
                    ? String.valueOf((long) mult) : String.valueOf(mult)) + ")"
                : "§7赏金 §f" + base;
            hunter.sendSystemMessage(Component.literal(
                "§c追踪目标 → " + (marked ? "§6" : "§f") + target.getName().getString() + " §7· " + info), true);
        } else {
            hunter.sendSystemMessage(Component.literal(
                "§c追踪目标 → §f" + target.getName().getString()), true);
        }
        event.setCanceled(true);
    }
}
