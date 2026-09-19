package com.example.manhunt.game;

import java.util.UUID;

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
 * 猎人复活位置选址规则。
 */
public final class RespawnSelector {
    private RespawnSelector() {}

    private static final RandomSource RNG = RandomSource.create();

    /**
     * 规则：
     * <ul>
     *   <li>在末地被击杀 → 复活在 4 号检查点附近 {@code END_RESPAWN_RADIUS} 格（主世界）；</li>
     *   <li>其他情况 → 距最近逃生者 &gt;{@code HUNTER_RESPAWN_MIN_DIST} 格的存活猎人中，
     *       选距逃生者最远者，在其附近随机复活；</li>
     *   <li>无合格猎人 → 上一个已激活检查点；再退到世界出生点。</li>
     * </ul>
     */
    public static GlobalPos selectHunterRespawn(MinecraftServer server, ServerPlayer deadHunter) {
        ServerLevel overworld = server.overworld();
        BlockPos base = null;

        if (deadHunter.level().dimension() == Level.END) {
            BlockPos cp4 = ManhuntGame.checkpoint(4);
            if (cp4 != null) {
                base = randomNear(overworld, cp4, GameConfig.END_RESPAWN_RADIUS);
            }
        }

        if (base == null) {
            ServerPlayer anchor = findFarthestHunter(server, deadHunter.getUUID());
            if (anchor != null) {
                base = randomNear(overworld, anchor.blockPosition(),
                    GameConfig.RESPAWN_OFFSET_MIN, GameConfig.RESPAWN_OFFSET_MAX);
            }
        }

        if (base == null) {
            BlockPos last = ManhuntGame.unlockedCount() > 0
                ? ManhuntGame.checkpoint(ManhuntGame.unlockedCount())
                : null;
            if (last != null) {
                base = randomNear(overworld, last, 32);
            }
        }

        if (base == null) {
            base = surfacePos(overworld, overworld.getLevelData().getRespawnData().pos());
        }
        return GlobalPos.of(Level.OVERWORLD, base);
    }

    /** 在距最近逃生者 &gt;300 格的存活猎人中选最远者。 */
    private static ServerPlayer findFarthestHunter(MinecraftServer server, UUID deadId) {
        ServerPlayer best = null;
        double bestDist = -1;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!TeamUtil.isHunter(p) || p.getUUID().equals(deadId) || p.isDeadOrDying()) {
                continue;
            }
            double nearest = nearestRunnerDistSq(server, p);
            if (nearest > GameConfig.HUNTER_RESPAWN_MIN_DIST * GameConfig.HUNTER_RESPAWN_MIN_DIST
                    && nearest > bestDist) {
                bestDist = nearest;
                best = p;
            }
        }
        return best;
    }

    private static double nearestRunnerDistSq(MinecraftServer server, ServerPlayer hunter) {
        double min = Double.MAX_VALUE;
        for (ServerPlayer r : ManhuntGame.onlineAliveRunners(server)) {
            min = Math.min(min, hunter.distanceToSqr(r));
        }
        return min;
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
        return surfacePos(overworld, center);
    }

    private static BlockPos randomNear(ServerLevel overworld, BlockPos center, int radius) {
        return randomNear(overworld, center, radius / 2, radius);
    }

    /** 取参照点所在柱的地表位置（水面上则原地返回由调用方兜底）。 */
    private static BlockPos surfacePos(ServerLevel overworld, BlockPos ref) {
        int x = ref.getX(), z = ref.getZ();
        int y = CheckpointManager.surfaceY(overworld, x, z);
        return new BlockPos(x, Math.max(y, ref.getY()), z);
    }
}
