package com.example.manhunt.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.example.manhunt.GameConfig;
import com.example.manhunt.cards.CardSlotManager;
import com.example.manhunt.cards.SkillCardsBridge;
import com.example.manhunt.game.CheckpointManager;
import com.example.manhunt.game.ManhuntGame;
import com.example.manhunt.game.MileageManager;
import com.example.manhunt.game.MoraleManager;
import com.example.manhunt.game.TeamUtil;
import com.example.manhunt.game.TierSystem;
import com.example.manhunt.loot.LootRoller;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /manhunt 指令。管理类子命令 OP 权限 2；cardchoose 供所有玩家点击聊天按钮使用。
 */
public final class ManhuntCommand {
    private ManhuntCommand() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> root =
            Commands.literal("manhunt");

        // ==================== 队伍（OP） ====================
        LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> admin =
            Commands.literal("admin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        admin.then(Commands.literal("team")
            .then(Commands.literal("add")
                .then(Commands.literal("hunter")
                    .then(Commands.argument("players", EntityArgument.players())
                        .executes(ctx -> addTeam(EntityArgument.getPlayers(ctx, "players"), true))))
                .then(Commands.literal("runner")
                    .then(Commands.argument("players", EntityArgument.players())
                        .executes(ctx -> addTeam(EntityArgument.getPlayers(ctx, "players"), false)))))
            .then(Commands.literal("clear").executes(ctx -> {
                clearTeams();
                ctx.getSource().sendSuccess(() -> Component.literal("§7[猎人游戏] 队伍已清空。"), true);
                return 1;
            })));

        admin.then(Commands.literal("start")
            .executes(ctx -> startAssigned(ctx.getSource()))
            .then(Commands.literal("random")
                .then(Commands.argument("hunters", IntegerArgumentType.integer(1))
                    .executes(ctx -> startRandom(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "hunters")))))
            .then(Commands.literal("solo").executes(ctx -> startSolo(ctx.getSource()))));

        admin.then(Commands.literal("stop").executes(ctx -> {
            ManhuntGame.stop(ctx.getSource().getServer());
            return 1;
        }));

        admin.then(Commands.literal("debug")
            .then(Commands.literal("status").executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.literal(statusText(ctx.getSource().getServer())), false);
                return 1;
            }))
            .then(Commands.literal("mileage")
                .then(Commands.literal("add")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> debugMileage(ctx, IntegerArgumentType.getInteger(ctx, "amount"), true))))
                .then(Commands.literal("set")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                        .executes(ctx -> debugMileage(ctx, IntegerArgumentType.getInteger(ctx, "amount"), false)))))
            .then(Commands.literal("morale")
                .then(Commands.literal("add")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> debugMorale(ctx, IntegerArgumentType.getInteger(ctx, "amount"), true))))
                .then(Commands.literal("set")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                        .executes(ctx -> debugMorale(ctx, IntegerArgumentType.getInteger(ctx, "amount"), false)))))
            .then(Commands.literal("draw").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                if (requireRunning(ctx.getSource())) {
                    LootRoller.resourceRoll(p, MileageManager.mileage(p.getUUID()));
                    return 1;
                }
                return 0;
            }))
            .then(Commands.literal("superdraw").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                if (requireRunning(ctx.getSource())) {
                    LootRoller.superRoll(p);
                    return 1;
                }
                return 0;
            }))
            .then(Commands.literal("card")
                .executes(ctx -> debugCard(ctx, null))
                .then(Commands.literal("common").executes(ctx -> debugCard(ctx, "common")))
                .then(Commands.literal("rare").executes(ctx -> debugCard(ctx, "rare")))
                .then(Commands.literal("rainbow").executes(ctx -> debugCard(ctx, "rainbow")))
                .then(Commands.literal("black").executes(ctx -> debugCard(ctx, "black"))))
            .then(Commands.literal("slot")
                .then(Commands.literal("set")
                    .then(Commands.argument("tier", IntegerArgumentType.integer(1, 4))
                        .executes(ctx -> {
                            TierSystem.setForced(IntegerArgumentType.getInteger(ctx, "tier"));
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§7档位已锁定为 " + TierSystem.displayTier()), true);
                            return 1;
                        })))
                .then(Commands.literal("auto").executes(ctx -> {
                    TierSystem.setForced(null);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7档位恢复自动。"), true);
                    return 1;
                })))
            .then(Commands.literal("nextcp")
                .then(Commands.literal("stronghold").executes(ctx -> {
                    if (!requireRunning(ctx.getSource())) {
                        return 0;
                    }
                    ManhuntGame.forceNextStronghold();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§7已强制：下一个刷新的检查点将是末地要塞。"), true);
                    return 1;
                })))
            .then(Commands.literal("unlock").executes(ctx -> {
                MinecraftServer server = ctx.getSource().getServer();
                if (!requireRunning(ctx.getSource())) {
                    return 0;
                }
                CheckpointManager.activate(server, ctx.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("tp")
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                    .executes(ctx -> debugTeleport(ctx, IntegerArgumentType.getInteger(ctx, "index")))))
            .then(Commands.literal("compass")
                .then(Commands.argument("players", EntityArgument.players())
                    .executes(ctx -> {
                        for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players")) {
                            com.example.manhunt.item.CompassManager.giveTrackingCompass(p);
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("§7罗盘已补发。"), true);
                        return 1;
                    }))));

        // ==================== 卡牌选择（所有玩家，聊天按钮） ====================
        root.then(Commands.literal("cardchoose")
            .then(Commands.literal("giveup").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                CardSlotManager.handleChoice(p, -1);
                return 1;
            }))
            .then(Commands.argument("slot", IntegerArgumentType.integer(1, 9)).executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                CardSlotManager.handleChoice(p, IntegerArgumentType.getInteger(ctx, "slot") - 1);
                return 1;
            })));

        // ==================== 状态（所有玩家可查） ====================
        root.then(Commands.literal("status").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal(statusText(ctx.getSource().getServer())), false);
            return 1;
        }));

        event.getDispatcher().register(root.then(admin));
    }

    private static boolean requireRunning(net.minecraft.commands.CommandSourceStack source) {
        if (!ManhuntGame.isRunning()) {
            source.sendFailure(Component.literal("§c当前没有进行中的对局。"));
            return false;
        }
        return true;
    }

    // ==================== 队伍与开局 ====================

    private static int addTeam(Collection<ServerPlayer> players, boolean hunter) {
        for (ServerPlayer p : players) {
            if (hunter) {
                ManhuntGame.hunters().add(p.getUUID());
                ManhuntGame.runners().remove(p.getUUID());
            } else {
                ManhuntGame.runners().add(p.getUUID());
                ManhuntGame.hunters().remove(p.getUUID());
            }
        }
        return players.size();
    }

    private static void clearTeams() {
        ManhuntGame.hunters().clear();
        ManhuntGame.runners().clear();
    }

    private static int startAssigned(net.minecraft.commands.CommandSourceStack source) {
        String error = ManhuntGame.start(source.getServer(),
            new HashSet<>(ManhuntGame.hunters()), new HashSet<>(ManhuntGame.runners()), false);
        return finishStart(source, error);
    }

    private static int startRandom(net.minecraft.commands.CommandSourceStack source, int hunterCount) {
        List<ServerPlayer> online = new ArrayList<>(source.getServer().getPlayerList().getPlayers());
        if (hunterCount >= online.size()) {
            source.sendFailure(Component.literal("§c猎人数必须少于在线玩家数（至少留 1 名逃生者）。"));
            return 0;
        }
        Collections.shuffle(online);
        Set<UUID> hunters = new HashSet<>();
        Set<UUID> runners = new HashSet<>();
        for (int i = 0; i < online.size(); i++) {
            if (i < hunterCount) {
                hunters.add(online.get(i).getUUID());
            } else {
                runners.add(online.get(i).getUUID());
            }
        }
        String error = ManhuntGame.start(source.getServer(), hunters, runners, false);
        return finishStart(source, error);
    }

    private static int startSolo(net.minecraft.commands.CommandSourceStack source) {
        Set<UUID> runners = new HashSet<>();
        for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
            runners.add(p.getUUID());
        }
        String error = ManhuntGame.start(source.getServer(), Set.of(), runners, true);
        return finishStart(source, error);
    }

    private static int finishStart(net.minecraft.commands.CommandSourceStack source, String error) {
        if (error != null) {
            source.sendFailure(Component.literal("§c" + error));
            return 0;
        }
        return 1;
    }

    // ==================== 调试 ====================

    private static int debugMileage(CommandContext<net.minecraft.commands.CommandSourceStack> ctx, int amount, boolean add)
            throws CommandSyntaxException {
        if (!requireRunning(ctx.getSource())) {
            return 0;
        }
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (add) {
            MileageManager.addMileage(p, amount);
        } else {
            MileageManager.setMileage(p, amount);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§7里程：" + MileageManager.mileage(p.getUUID()) + "（已抽奖 " + MileageManager.rollCount(p.getUUID()) + " 次）"), true);
        return 1;
    }

    private static int debugMorale(CommandContext<net.minecraft.commands.CommandSourceStack> ctx, int amount, boolean add) {
        if (!requireRunning(ctx.getSource())) {
            return 0;
        }
        if (add) {
            MoraleManager.addMorale(ctx.getSource().getServer(), amount);
        } else {
            MoraleManager.restore(Math.min(amount, GameConfig.MORALE_CAP), 0);
            // 按新值重算已触发档数
            int rewards = 0;
            while (rewards < GameConfig.MORALE_THRESHOLDS.length
                    && amount >= GameConfig.MORALE_THRESHOLDS[rewards]) {
                rewards++;
            }
            MoraleManager.restore(Math.min(amount, GameConfig.MORALE_CAP), rewards);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§7士气：" + MoraleManager.morale() + "（已触发 " + MoraleManager.rewards() + " 档）"), true);
        return 1;
    }

    private static int debugCard(CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                 String gradeId) throws CommandSyntaxException {
        if (!requireRunning(ctx.getSource())) {
            return 0;
        }
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!SkillCardsBridge.available()) {
            ctx.getSource().sendFailure(Component.literal("§c未安装技能卡模组（skillcards）。"));
            return 0;
        }
        var draw = SkillCardsBridge.drawByGradeId(gradeId, net.minecraft.util.RandomSource.create());
        if (draw == null) {
            ctx.getSource().sendFailure(Component.literal("§c该品级没有可用卡牌。"));
            return 0;
        }
        CardSlotManager.giveDrawnCard(p, draw);
        return 1;
    }

    private static int debugTeleport(CommandContext<net.minecraft.commands.CommandSourceStack> ctx, int ordinal)
            throws CommandSyntaxException {
        BlockPos pos = ManhuntGame.currentCheckpoint() != null
            ? ManhuntGame.currentCheckpoint()
            : ManhuntGame.lastCheckpoint();
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (pos == null) {
            ctx.getSource().sendFailure(Component.literal("§c当前没有已生成的检查点。"));
            return 0;
        }
        ServerLevel overworld = ctx.getSource().getServer().overworld();
        int y = CheckpointManager.surfaceY(overworld, pos.getX(), pos.getZ());
        player.teleportTo(overworld, pos.getX() + 0.5, y + 1, pos.getZ() + 0.5, java.util.Set.of(), 0.0F, 0.0F, false);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§7已传送到检查点（激活数 " + ManhuntGame.activatedCount() + "）。"), false);
        return 1;
    }

    // ==================== 状态 ====================

    private static String statusText(MinecraftServer server) {
        StringBuilder sb = new StringBuilder();
        sb.append("§6==== 猎人游戏状态 ====\n");
        sb.append("§7阶段: §f").append(ManhuntGame.phase())
            .append(ManhuntGame.soloMode() ? " §b(单人调试)" : "").append('\n');
        sb.append("§7档位: §f").append(TierSystem.displayTier())
            .append(TierSystem.forcedTier() != null ? " §c(锁定)" : " §7(自动)").append('\n');
        sb.append("§7士气: §f").append(MoraleManager.morale())
            .append("§7/触发 ").append(MoraleManager.rewards()).append(" 档\n");
        sb.append("§7检查点: §f已激活 ").append(ManhuntGame.activatedCount())
            .append(" 个 | ").append(CheckpointManager.describeCurrent(null)).append('\n');
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!ManhuntGame.isParticipant(p.getUUID())) {
                    continue;
                }
                String role = TeamUtil.isHunter(p) ? "§c猎人" : "§a逃生者";
                String extra = TeamUtil.isHunter(p) ? "" : " §7| 里程 §f" + MileageManager.mileage(p.getUUID());
                String pending = CardSlotManager.hasPending(p.getUUID()) ? " §e[待选卡]" : "";
                sb.append(role).append(" §f").append(p.getName().getString())
                    .append(p.isSpectator() ? " §7(旁观)" : "")
                    .append(extra).append(pending).append('\n');
            }
        }
        return sb.toString();
    }
}
