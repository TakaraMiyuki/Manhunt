package com.example.manhunt.game;

import java.util.UUID;

import com.example.manhunt.GameConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 检查点链：数量不限、逐个解锁（间隔 400~600 格）。
 * 任一逃生者里程达到阈值后，下一个检查点固定指向末地要塞（要塞正上方地表）。
 */
public final class CheckpointManager {
    private CheckpointManager() {}

    private static final RandomSource RNG = RandomSource.create();
    /** 用于 locate 的结构 tag：数据文件 data/manhunt/tags/worldgen/structure/stronghold_finder.json。 */
    private static final TagKey<Structure> STRONGHOLD_TAG =
        TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("manhunt", "stronghold_finder"));

    // ==================== 生成 ====================

    /** 开局生成 1 号检查点（距世界出生点 400~600 格）。 */
    public static void generateFirst(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getLevelData().getRespawnData().pos();
        ManhuntGame.setCurrentCheckpoint(pickSurfacePoint(overworld, spawn,
            GameConfig.CP_MIN_DIST, GameConfig.CP_MAX_DIST), false);
    }

    /** 激活当前检查点后的链推进：发放奖励并生成下一个。 */
    public static void onActivated(MinecraftServer server, ServerPlayer activator) {
        boolean isStronghold = ManhuntGame.isCurrentStronghold();
        ManhuntGame.onCheckpointActivated(server, activator, isStronghold);
        if (isStronghold) {
            // 要塞检查点为最后一个：目标转为击杀末影龙
            ManhuntGame.setCurrentCheckpoint(null, false);
            return;
        }
        ServerLevel overworld = server.overworld();
        BlockPos prev = ManhuntGame.lastCheckpoint();
        if (ManhuntGame.nextStrongholdForced() || MileageManager.anyRunnerReachedStronghold(server)) {
            ManhuntGame.resetStrongholdForce();
            BlockPos stronghold = findStrongholdSurface(overworld, prev);
            if (stronghold == null) {
                stronghold = findStrongholdSurface(overworld, overworld.getLevelData().getRespawnData().pos());
            }
            if (stronghold != null) {
                ManhuntGame.setCurrentCheckpoint(stronghold, true);
                ManhuntGame.sendToRunners(server, "§6[猎人游戏] §d里程达标！下一个检查点：末地要塞（坐标 "
                    + stronghold.getX() + ", " + stronghold.getY() + ", " + stronghold.getZ()
                    + "，要塞入口在其地下）");
                placeMarker(overworld, stronghold);
                return;
            }
            ManhuntGame.broadcast(server, "§c[猎人游戏] 未能定位末地要塞，可让管理员用 /locate structure minecraft:stronghold 确认。");
        }
        BlockPos next = pickSurfacePoint(overworld, prev, GameConfig.CP_MIN_DIST, GameConfig.CP_MAX_DIST);
        ManhuntGame.setCurrentCheckpoint(next, false);
        placeMarker(overworld, next);
        ManhuntGame.sendToRunners(server, "§6[猎人游戏] §f下一个检查点坐标: §e"
            + next.getX() + ", " + next.getY() + ", " + next.getZ());
    }

    // ==================== 选址 ====================

    /**
     * 取 (x, z) 地表高度。注意：区块未加载时 Level.getHeight 会返回世界最低 Y，
     * 必须先同步加载区块。
     */
    public static int surfaceY(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** 随机方向选点，避开水面与海洋/河流群系。 */
    private static BlockPos pickSurfacePoint(ServerLevel level, BlockPos from, int minDist, int maxDist) {
        BlockPos fallback = null;
        for (int attempt = 0; attempt < 80; attempt++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double dist = minDist + RNG.nextDouble() * (maxDist - minDist);
            int x = Mth.floor(from.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(from.getZ() + Math.sin(angle) * dist);
            BlockPos pos = new BlockPos(x, surfaceY(level, x, z), z);
            if (fallback == null) {
                fallback = pos;
            }
            if (isGoodSurface(level, pos)) {
                return pos;
            }
        }
        // 多次重试失败则放宽要求取最后一个候选
        return fallback != null ? fallback
            : new BlockPos(from.getX(), surfaceY(level, from.getX(), from.getZ()), from.getZ());
    }

    private static boolean isGoodSurface(ServerLevel level, BlockPos pos) {
        if (pos.getY() < GameConfig.MIN_SURFACE_Y) {
            return false; // 海平面以下 = 水面/湖面
        }
        BlockPos ground = pos.below();
        if (!level.getFluidState(ground).isEmpty()) {
            return false;
        }
        Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(ground);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
            return false;
        }
        return true;
    }

    /** 定位最近的末地要塞，返回其正上方地表位置。 */
    private static BlockPos findStrongholdSurface(ServerLevel level, BlockPos from) {
        BlockPos found = level.findNearestMapStructure(
            STRONGHOLD_TAG, from, GameConfig.STRONGHOLD_SEARCH_RADIUS_CHUNKS, false);
        if (found == null) {
            return null;
        }
        return new BlockPos(found.getX(), surfaceY(level, found.getX(), found.getZ()), found.getZ());
    }

    /** 检查点视觉标记：3x3 金块平台 + 3 格高荧石柱。 */
    private static void placeMarker(ServerLevel level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(pos.offset(dx, -1, dz), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            }
        }
        level.setBlock(pos, Blocks.GLOWSTONE.defaultBlockState(), 3);
        level.setBlock(pos.above(), Blocks.GLOWSTONE.defaultBlockState(), 3);
        level.setBlock(pos.above(2), Blocks.GLOWSTONE.defaultBlockState(), 3);
    }

    // ==================== 激活判定 ====================

    /** 低频检查在线逃生者是否触及当前检查点。 */
    public static void checkActivations(MinecraftServer server) {
        BlockPos cp = ManhuntGame.currentCheckpoint();
        if (cp == null) {
            return;
        }
        double radiusSq = GameConfig.CHECKPOINT_ACTIVATE_RADIUS * GameConfig.CHECKPOINT_ACTIVATE_RADIUS;
        Vec3 center = Vec3.atCenterOf(cp);
        for (ServerPlayer p : ManhuntGame.onlineAliveRunners(server)) {
            if (p.distanceToSqr(center) <= radiusSq) {
                activate(server, p);
                return; // 一次 tick 只激活一个
            }
        }
    }

    /** 调试指令 / 靠近触发统一入口。 */
    public static void activate(MinecraftServer server, ServerPlayer activator) {
        onActivated(server, activator);
    }

    /** 供指令展示：最近激活检查点。 */
    public static String describeCurrent(UUID viewer) {
        BlockPos cp = ManhuntGame.currentCheckpoint();
        if (cp == null) {
            return "§d全部检查点已激活，前往末地击杀末影龙！";
        }
        return (ManhuntGame.isCurrentStronghold() ? "§d末地要塞" : "§e第 " + (ManhuntGame.activatedCount() + 1) + " 个检查点")
            + " §7@ §f" + cp.getX() + ", " + cp.getY() + ", " + cp.getZ()
            + " §7(激活数 " + ManhuntGame.activatedCount() + ")";
    }
}
