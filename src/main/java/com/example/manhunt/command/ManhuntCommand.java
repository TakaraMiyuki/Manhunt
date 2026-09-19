package com.example.manhunt.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.example.manhunt.GameConfig;
import com.example.manhunt.game.ManhuntGame;
import com.example.manhunt.game.TeamUtil;
import com.example.manhunt.item.CompassManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /manhunt 指令（OP 权限 2）。
 */
public final class ManhuntCommand {
    private ManhuntCommand() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> root =
            Commands.literal("manhunt").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        // ---- 队伍 ----
        root.then(Commands.literal("team")
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

        // ---- 开始 ----
        root.then(Commands.literal("start")
            .executes(ctx -> startAssigned(ctx.getSource()))
            .then(Commands.literal("random")
                .then(Commands.argument("hunters", IntegerArgumentType.integer(1))
                    .executes(ctx -> startRandom(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "hunters"))))));

        root.then(Commands.literal("stop").executes(ctx -> {
            ManhuntGame.stop(ctx.getSource().getServer());
            return 1;
        }));

        root.then(Commands.literal("status").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal(statusText()), false);
            return 1;
        }));

        // ---- 调试 ----
        root.then(Commands.literal("debug")
            .then(Commands.literal("unlock").executes(ctx -> {
                MinecraftServer server = ctx.getSource().getServer();
                if (!ManhuntGame.isRunning()) {
                    ctx.getSource().sendFailure(Component.literal("§c当前没有进行中的对局。"));
                    return 0;
                }
                ManhuntGame.activateCheckpoint(server, ctx.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("tp")
                .then(Commands.argument("index", IntegerArgumentType.integer(1, GameConfig.CHECKPOINT_COUNT))
                    .executes(ctx -> {
                        int index = IntegerArgumentType.getInteger(ctx, "index");
                        BlockPos pos = ManhuntGame.checkpoint(index);
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        if (pos == null) {
                            ctx.getSource().sendFailure(Component.literal("§c该检查点尚未生成。"));
                            return 0;
                        }
                        ServerLevel overworld = ctx.getSource().getServer().overworld();
                        int y = com.example.manhunt.game.CheckpointManager.surfaceY(overworld, pos.getX(), pos.getZ());
                        player.teleportTo(overworld, pos.getX() + 0.5, y + 1, pos.getZ() + 0.5, java.util.Set.of(), 0.0F, 0.0F, false);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§7已传送到 " + index + " 号检查点。"), false);
                        return 1;
                    })))
            .then(Commands.literal("compass")
                .then(Commands.argument("players", EntityArgument.players())
                    .executes(ctx -> {
                        for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players")) {
                            if (TeamUtil.isHunter(p)) {
                                CompassManager.giveTrackingCompass(p);
                            } else if (TeamUtil.isRunner(p)) {
                                CompassManager.giveCheckpointCompass(p);
                            }
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("§7罗盘已补发。"), true);
                        return 1;
                    }))));

        event.getDispatcher().register(root);
    }

    private static int addTeam(java.util.Collection<ServerPlayer> players, boolean hunter) {
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
        if (ManhuntGame.hunters().isEmpty() || ManhuntGame.runners().isEmpty()) {
            source.sendFailure(Component.literal(
                "§c请先用 /manhunt team add hunter|runner <玩家> 指定角色，或用 /manhunt start random <猎人数>。"));
            return 0;
        }
        String error = ManhuntGame.start(source.getServer(),
            new HashSet<>(ManhuntGame.hunters()), new HashSet<>(ManhuntGame.runners()));
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
        String error = ManhuntGame.start(source.getServer(), hunters, runners);
        return finishStart(source, error);
    }

    private static int finishStart(net.minecraft.commands.CommandSourceStack source, String error) {
        if (error != null) {
            source.sendFailure(Component.literal("§c" + error));
            return 0;
        }
        return 1;
    }

    private static String statusText() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6==== 猎人游戏状态 ====\n");
        sb.append("§7阶段: §f").append(ManhuntGame.phase()).append('\n');
        sb.append("§7检查点: §f").append(ManhuntGame.unlockedCount()).append("/4 已激活\n");
        if (ManhuntGame.isRunning()) {
            for (int i = 1; i <= GameConfig.CHECKPOINT_COUNT; i++) {
                BlockPos pos = ManhuntGame.checkpoint(i);
                if (pos != null) {
                    sb.append("§7  ").append(i).append(" 号: §f")
                        .append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ()).append('\n');
                }
            }
        }
        sb.append("§c猎人 §7(").append(ManhuntGame.hunters().size()).append("): §f")
            .append(ManhuntGame.hunters().size()).append(" 人\n");
        sb.append("§a逃生者 §7(").append(ManhuntGame.runners().size()).append("): §f")
            .append(ManhuntGame.runners().size() - eliminatedCount()).append(" 人存活\n");
        return sb.toString();
    }

    private static int eliminatedCount() {
        int n = 0;
        for (UUID id : ManhuntGame.runners()) {
            if (ManhuntGame.isEliminated(id)) {
                n++;
            }
        }
        return n;
    }
}
