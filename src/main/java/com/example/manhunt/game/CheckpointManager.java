package com.example.manhunt.game;

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
 * 检查点生成、标记与激活判定。
 */
public final class CheckpointManager {
    private CheckpointManager() {}

    private static final RandomSource RNG = RandomSource.create();
    /** 用于 locate 的结构 tag：数据文件 data/manhunt/tags/worldgen/structure/stronghold_finder.json。 */
    private static final TagKey<Structure> STRONGHOLD_TAG =
        TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("manhunt", "stronghold_finder"));

    /**
     * 生成全部 4 个检查点：
     * 1 号距世界出生点 1000~1500 格；2/3 号距上一检查点 500~800 格，随机方向，地表选址；
     * 4 号固定在最近的末地要塞正上方地表。
     *
     * @return 错误提示；null 表示全部成功
     */
    public static String generateAll(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getLevelData().getRespawnData().pos();

        BlockPos cp1 = pickSurfacePoint(overworld, spawn,
            GameConfig.CP1_MIN_DIST, GameConfig.CP1_MAX_DIST);
        ManhuntGame.setCheckpoint(0, cp1);

        BlockPos prev = cp1;
        for (int i = 1; i <= 2; i++) {
            prev = pickSurfacePoint(overworld, prev,
                GameConfig.CP_STEP_MIN_DIST, GameConfig.CP_STEP_MAX_DIST);
            ManhuntGame.setCheckpoint(i, prev);
        }

        String warning = null;
        BlockPos cp4 = findStrongholdSurface(overworld, prev);
        if (cp4 == null) {
            // 从世界出生点再找一次；仍找不到则退化为随机点
            cp4 = findStrongholdSurface(overworld, spawn);
            if (cp4 == null) {
                cp4 = pickSurfacePoint(overworld, prev,
                    GameConfig.CP_STEP_MIN_DIST, GameConfig.CP_STEP_MAX_DIST);
                warning = "未找到末地要塞，4 号检查点已退化为随机位置（找不到时可用 /locate structure minecraft:stronghold 手动确认）";
            }
        }
        ManhuntGame.setCheckpoint(3, cp4);

        for (int i = 1; i <= GameConfig.CHECKPOINT_COUNT; i++) {
            placeMarker(overworld, ManhuntGame.checkpoint(i));
        }
        return warning;
    }

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
        return fallback != null ? fallback : new BlockPos(from.getX(), surfaceY(level, from.getX(), from.getZ()), from.getZ());
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

    /** 每刻（低频）检查在线逃生者是否触及当前检查点。 */
    public static void checkActivations(MinecraftServer server) {
        BlockPos cp = ManhuntGame.currentCheckpoint();
        if (cp == null) {
            return;
        }
        double radiusSq = GameConfig.CHECKPOINT_ACTIVATE_RADIUS * GameConfig.CHECKPOINT_ACTIVATE_RADIUS;
        Vec3 center = Vec3.atCenterOf(cp);
        for (ServerPlayer p : ManhuntGame.onlineAliveRunners(server)) {
            if (p.distanceToSqr(center) <= radiusSq) {
                ManhuntGame.activateCheckpoint(server, p);
                return; // 一次 tick 只激活一个
            }
        }
    }
}
