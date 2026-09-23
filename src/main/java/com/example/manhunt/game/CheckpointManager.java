package com.example.manhunt.game;

import java.util.UUID;

import com.example.manhunt.GameConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
                // 定位末地传送门房间：检查点刷新在其正上方地表，猎人罗盘也指向此处
                BlockPos portalRoom = findPortalRoom(overworld, stronghold);
                if (portalRoom != null) {
                    BlockPos above = new BlockPos(portalRoom.getX(),
                        surfaceY(overworld, portalRoom.getX(), portalRoom.getZ()), portalRoom.getZ());
                    ManhuntGame.setCurrentCheckpoint(above, true);
                    ManhuntGame.setStrongholdPortalPos(
                        net.minecraft.core.GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD, portalRoom));
                    ManhuntGame.sendToRunners(server, "§6[猎人游戏] §d要塞传送门房间已定位：检查点在其正上方，"
                        + "坐标 " + above.getX() + ", " + above.getY() + ", " + above.getZ() + "（向下挖掘进入）");
                    return;
                }
                ManhuntGame.setStrongholdPortalPos(null);
                ManhuntGame.setCurrentCheckpoint(stronghold, true);
                ManhuntGame.sendToRunners(server, "§6[猎人游戏] §d里程达标！下一个检查点：末地要塞（坐标 "
                    + stronghold.getX() + ", " + stronghold.getY() + ", " + stronghold.getZ()
                    + "，要塞入口在其地下）");
                return;
            }
            ManhuntGame.broadcast(server, "§c[猎人游戏] 未能定位末地要塞，可让管理员用 /locate structure minecraft:stronghold 确认。");
        }
        BlockPos next = pickSurfacePoint(overworld, prev, GameConfig.CP_MIN_DIST, GameConfig.CP_MAX_DIST);
        ManhuntGame.setCurrentCheckpoint(next, false);
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
        BlockPos found = findStronghold(level, from);
        if (found == null) {
            return null;
        }
        return new BlockPos(found.getX(), surfaceY(level, found.getX(), found.getZ()), found.getZ());
    }

    /** 定位最近的末地要塞（结构原点，未取地表）。 */
    private static BlockPos findStronghold(ServerLevel level, BlockPos from) {
        return level.findNearestMapStructure(
            STRONGHOLD_TAG, from, GameConfig.STRONGHOLD_SEARCH_RADIUS_CHUNKS, false);
    }

    /** 在要塞结构 piece 中查找末地传送门房间（PortalRoom）的中心位置；找不到返回 null。 */
    private static BlockPos findPortalRoom(ServerLevel level, BlockPos strongholdPos) {
        try {
            var holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .get(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace("stronghold")));
            if (holder.isEmpty()) {
                return null;
            }
            net.minecraft.world.level.levelgen.structure.Structure target = holder.get().value();
            var starts = level.structureManager().startsForStructure(
                net.minecraft.world.level.ChunkPos.containing(strongholdPos), s -> s == target);
            for (var start : starts) {
                for (var piece : start.getPieces()) {
                    if (piece instanceof net.minecraft.world.level.levelgen.structure.structures
                            .StrongholdPieces.PortalRoom room) {
                        return room.getBoundingBox().getCenter();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }


    // ==================== 激活判定 ====================

    /** 低频检查在线逃生者是否触及当前检查点。 */
    public static void checkActivations(MinecraftServer server) {
        BlockPos cp = ManhuntGame.currentCheckpoint();
        if (cp == null) {
            return;
        }
        // 水平距离判定（忽略 Y 差）：检查点可能落在树上/悬崖边，玩家在树冠下也应激活
        double radiusSq = GameConfig.CHECKPOINT_ACTIVATE_RADIUS * GameConfig.CHECKPOINT_ACTIVATE_RADIUS;
        for (ServerPlayer p : ManhuntGame.onlineAliveRunners(server)) {
            double dx = p.getX() - (cp.getX() + 0.5);
            double dz = p.getZ() - (cp.getZ() + 0.5);
            if (dx * dx + dz * dz <= radiusSq) {
                activate(server, p);
                return; // 一次 tick 只激活一个
            }
        }
    }

    /** 调试指令 / 靠近触发统一入口。 */
    public static void activate(MinecraftServer server, ServerPlayer activator) {
        onActivated(server, activator);
    }

    // ==================== 粒子圈 ====================

    /** 激活后变绿的检查点：位置 → 到期时刻。 */
    private static final java.util.Map<BlockPos, Long> GREEN_RINGS = new java.util.HashMap<>();
    /** 当前检查点粒子圈（黄色）。 */
    private static final net.minecraft.core.particles.DustParticleOptions YELLOW_RING =
        new net.minecraft.core.particles.DustParticleOptions(0xFFD800, 2.4F);
    /** 激活后的绿色粒子圈。 */
    private static final net.minecraft.core.particles.DustParticleOptions GREEN_RING =
        new net.minecraft.core.particles.DustParticleOptions(0x4CFF4C, 2.4F);

    private static long gameTime(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** 登记一个刚激活检查点的绿色圈。 */
    public static void markActivated(BlockPos pos, MinecraftServer server) {
        GREEN_RINGS.put(pos.immutable(), gameTime(server) + GameConfig.CHECKPOINT_GREEN_SECONDS * 20L);
    }

    /** 周期绘制检查点粒子圈（当前=黄色，激活过=绿色，10 秒后移除）。 */
    public static void tickEffects(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        BlockPos current = ManhuntGame.currentCheckpoint();
        if (current != null) {
            drawRing(overworld, current, YELLOW_RING);
        }
        var it = GREEN_RINGS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (gameTime(server) >= e.getValue()) {
                it.remove();
                continue;
            }
            drawRing(overworld, e.getKey(), GREEN_RING);
        }
    }

    private static void drawRing(ServerLevel level, BlockPos pos, net.minecraft.core.particles.DustParticleOptions dust) {
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.8, cz = pos.getZ() + 0.5;
        double radius = GameConfig.CHECKPOINT_ACTIVATE_RADIUS; // 与实际触发范围一致
        int points = 20;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            for (int h = 0; h < 2; h++) {
                double py = cy + h * 1.0;
                for (ServerPlayer p : level.players()) {
                    level.sendParticles(p, dust, true, false,
                        cx + Math.cos(angle) * radius, py, cz + Math.sin(angle) * radius,
                        1, 0.0, 0.02, 0.0, 0.0);
                }
            }
        }
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
