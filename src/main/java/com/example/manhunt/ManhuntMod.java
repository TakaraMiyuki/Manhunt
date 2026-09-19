package com.example.manhunt;

import com.example.manhunt.game.CheckpointManager;
import com.example.manhunt.game.DeathHandler;
import com.example.manhunt.game.ManhuntGame;
import com.example.manhunt.command.ManhuntCommand;
import com.example.manhunt.item.CompassManager;
import com.example.manhunt.item.ManhuntItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(ManhuntMod.MODID)
public final class ManhuntMod {
    public static final String MODID = "manhunt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ManhuntMod(IEventBus modEventBus) {
        ManhuntItems.ITEMS.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(ManhuntCommand::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(ManhuntGame::onServerTick);
        NeoForge.EVENT_BUS.addListener(ManhuntMod::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ManhuntMod::onServerStopping);
        NeoForge.EVENT_BUS.addListener(DeathHandler::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(DeathHandler::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(DeathHandler::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(DeathHandler::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(DeathHandler::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(CompassManager::onRightClick);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ManhuntGame.onServerStarted(event.getServer());
        LOGGER.info("[Manhunt] 状态已加载，当前阶段: {}", ManhuntGame.phase());
        if ("1".equals(System.getenv("MANHUNT_SMOKETEST"))) {
            runSmokeTest(event.getServer());
        }
    }

    /**
     * 无玩家冒烟测试（环境变量 MANHUNT_SMOKETEST=1 时启用）：
     * 验证检查点生成算法与末地要塞定位，日志输出坐标与间距。
     */
    private static void runSmokeTest(net.minecraft.server.MinecraftServer server) {
        try {
            String warning = CheckpointManager.generateAll(server);
            net.minecraft.core.BlockPos prev = null;
            for (int i = 1; i <= 4; i++) {
                var pos = ManhuntGame.checkpoint(i);
                if (pos == null) {
                    LOGGER.error("[Manhunt][冒烟测试] {} 号检查点未生成", i);
                    continue;
                }
                String dist = prev == null ? "-" : String.format("%.0f",
                    Math.sqrt(prev.distSqr(pos)));
                LOGGER.info("[Manhunt][冒烟测试] {} 号检查点: {} {}（与上一检查点间距 {} 格）",
                    i, pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), "", dist);
                prev = pos;
            }
            LOGGER.info("[Manhunt][冒烟测试] 生成警告: {}", warning == null ? "无" : warning);
        } catch (Exception e) {
            LOGGER.error("[Manhunt][冒烟测试] 检查点生成失败", e);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        ManhuntGame.onServerStopping(event.getServer());
    }
}
