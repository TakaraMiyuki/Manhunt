package com.example.manhunt.game;

import java.util.List;

import com.example.manhunt.GameConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * 猎人复活位置选址（在传送时刻调用，保证末地覆盖与最新位置生效）：
 * <ol>
 *   <li>有存活逃生者进入末地 → 全体猎人重生至末地传送门门口（要塞传送门房间）；</li>
 *   <li>距最近逃生者 &gt;{@code HUNTER_RESPAWN_MIN_DIST} 格的存活猎人中取<b>最近</b>者，
 *       在其附近约 20 格复活；</li>
 *   <li>无合格猎人 → 距最近逃生者约 {@code HUNTER_RESPAWN_FALLBACK_DIST} 格的随机方向地表；</li>
 *   <li>兜底：世界出生点。</li>
 * </ol>
 */
public final class RespawnSelector {
    private RespawnSelector() {}

    private static final RandomSource RNG = RandomSource.create();

    public static GlobalPos selectHunterRespawn(MinecraftServer server, ServerPlayer respawning) {
        ServerLevel overworld = server.overworld();
        List<ServerPlayer> runners = ManhuntGame.onlineAliveRunners(server);

        // ① 末地覆盖：有逃生者进入末地后，猎人重生点变更为末地传送门门口
        if (!runners.isEmpty() && anyRunnerInEnd(runners)) {
            GlobalPos portal = ManhuntGame.strongholdPortalPos();
            if (portal != null) {
                return portal;
            }
        }

        // ② 距逃生者 >300 格的存活猎人中取最近者，附近约 20 格复活
        ServerPlayer anchor = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!TeamUtil.isHunter(p) || p.getUUID().equals(respawning.getUUID()) || p.isDeadOrDying()) {
                continue;
            }
            double nearest = nearestRunnerDistSq(runners, p);
            if (nearest > GameConfig.HUNTER_RESPAWN_MIN_DIST * GameConfig.HUNTER_RESPAWN_MIN_DIST
                    && nearest < best) {
                best = nearest;
                anchor = p;
            }
        }
        if (anchor != null) {
            return GlobalPos.of(Level.OVERWORLD, randomNear(overworld, anchor.blockPosition(),
                GameConfig.RESPAWN_OFFSET_MIN, GameConfig.RESPAWN_OFFSET_MAX));
        }

        // ③ 距最近逃生者约 500 格的随机地表
        if (!runners.isEmpty()) {
            BlockPos fallback = distantSurface(overworld, runners);
            if (fallback != null) {
                return GlobalPos.of(Level.OVERWORLD, fallback);
            }
        }

        // ④ 兜底：世界出生点
        return GlobalPos.of(Level.OVERWORLD, overworld.getLevelData().getRespawnData().pos());
    }

    private static boolean anyRunnerInEnd(List<ServerPlayer> runners) {
        for (ServerPlayer r : runners) {
            if (r.level().dimension() == Level.END) {
                return true;
            }
        }
        return false;
    }

    private static double nearestRunnerDistSq(List<ServerPlayer> runners, ServerPlayer hunter) {
        double min = Double.MAX_VALUE;
        for (ServerPlayer r : runners) {
            min = Math.min(min, hunter.distanceToSqr(r));
        }
        return min;
    }

    /** 以首个存活逃生者为参照，随机方向约 500 格的地表点；校验与所有逃生者的距离。 */
    private static BlockPos distantSurface(ServerLevel overworld, List<ServerPlayer> runners) {
        ServerPlayer ref = runners.get(0);
        double minDistSq = GameConfig.HUNTER_RESPAWN_FALLBACK_DIST * GameConfig.HUNTER_RESPAWN_FALLBACK_DIST;
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double dist = GameConfig.HUNTER_RESPAWN_FALLBACK_DIST + RNG.nextDouble() * 64;
            int x = Mth.floor(ref.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(ref.getZ() + Math.sin(angle) * dist);
            int y = CheckpointManager.surfaceY(overworld, x, z);
            if (y < GameConfig.MIN_SURFACE_Y) {
                continue; // 水面/未加载
            }
            BlockPos pos = new BlockPos(x, y, z);
            boolean ok = true;
            for (ServerPlayer r : runners) {
                if (r.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < minDistSq * 0.92) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return pos;
            }
        }
        return null;
    }

    private static BlockPos randomNear(ServerLevel overworld, BlockPos center, int min, int max) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double dist = min + RNG.nextDouble() * (max - min);
            int x = Mth.floor(center.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(center.getZ() + Math.sin(angle) * dist);
            int y = CheckpointManager.surfaceY(overworld, x, z);
            if (y >= GameConfig.MIN_SURFACE_Y) {
                return new BlockPos(x, y, z);
            }
        }
        return center;
    }
}
