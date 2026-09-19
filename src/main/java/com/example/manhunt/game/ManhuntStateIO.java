package com.example.manhunt.game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.example.manhunt.GameConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 游戏状态持久化：world/manhunt_state.json（服务器重启后恢复进度）。
 */
public final class ManhuntStateIO {
    private ManhuntStateIO() {}

    private static final String FILE_NAME = "manhunt_state.json";

    public static synchronized void save(MinecraftServer server) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("phase", ManhuntGame.rawPhase().name());
            root.addProperty("unlocked", ManhuntGame.unlockedCount());
            root.add("hunters", uuidList(ManhuntGame.hunters()));
            root.add("runners", uuidList(ManhuntGame.runners()));
            Set<UUID> eliminated = new HashSet<>();
            for (UUID id : ManhuntGame.runners()) {
                if (ManhuntGame.isEliminated(id)) {
                    eliminated.add(id);
                }
            }
            root.add("eliminated", uuidList(eliminated));
            JsonObject cps = new JsonObject();
            for (int i = 1; i <= GameConfig.CHECKPOINT_COUNT; i++) {
                BlockPos pos = ManhuntGame.checkpoint(i);
                if (pos != null) {
                    cps.add(String.valueOf(i), encodePos(pos));
                }
            }
            root.add("checkpoints", cps);

            Path file = path(server);
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString());
        } catch (IOException e) {
            // 持久化失败不影响对局
        }
    }

    public static synchronized void load(MinecraftServer server) {
        Path file = path(server);
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            ManhuntGame.Phase phase = ManhuntGame.Phase.valueOf(root.get("phase").getAsString());
            if (phase == ManhuntGame.Phase.IDLE || phase == ManhuntGame.Phase.ENDED) {
                return;
            }
            // 倒计时无法跨重启恢复，统一按追逐阶段恢复
            ManhuntGame.Phase restored = phase == ManhuntGame.Phase.ESCAPE ? ManhuntGame.Phase.RUNNING : phase;
            int unlocked = root.get("unlocked").getAsInt();
            Set<UUID> hunters = readUuids(root.get("hunters"));
            Set<UUID> runners = readUuids(root.get("runners"));
            Set<UUID> eliminated = readUuids(root.get("eliminated"));
            BlockPos[] cps = new BlockPos[GameConfig.CHECKPOINT_COUNT];
            JsonObject cpsJson = root.getAsJsonObject("checkpoints");
            for (int i = 1; i <= GameConfig.CHECKPOINT_COUNT; i++) {
                if (cpsJson.has(String.valueOf(i))) {
                    cps[i - 1] = decodePos(cpsJson.get(String.valueOf(i)).getAsJsonObject());
                }
            }
            ManhuntGame.restoreState(restored, hunters, runners, eliminated, unlocked, cps);
            ManhuntGame.reattachBossbars(server);
            ManhuntGame.broadcast(server, "§6[猎人游戏] §7已从存档恢复对局（第 " + unlocked + " 个检查点已激活）。");
        } catch (Exception e) {
            // 状态损坏则忽略，视为无对局
        }
    }

    private static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    private static com.google.gson.JsonArray uuidList(Set<UUID> ids) {
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (UUID id : ids) {
            arr.add(id.toString());
        }
        return arr;
    }

    private static Set<UUID> readUuids(com.google.gson.JsonElement el) {
        Set<UUID> set = new HashSet<>();
        if (el != null && el.isJsonArray()) {
            for (var e : el.getAsJsonArray()) {
                try {
                    set.add(UUID.fromString(e.getAsString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return set;
    }

    private static JsonObject encodePos(BlockPos pos) {
        JsonObject o = new JsonObject();
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        return o;
    }

    private static BlockPos decodePos(JsonObject o) {
        return new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
    }
}
