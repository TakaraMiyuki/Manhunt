package com.example.manhunt.game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
            root.addProperty("solo", ManhuntGame.rawSoloMode());
            root.addProperty("activated", ManhuntGame.activatedCount());
            root.add("hunters", uuidList(ManhuntGame.hunters()));
            root.add("runners", uuidList(ManhuntGame.runners()));
            Set<UUID> eliminated = new HashSet<>();
            for (UUID id : ManhuntGame.runners()) {
                if (ManhuntGame.isEliminated(id)) {
                    eliminated.add(id);
                }
            }
            root.add("eliminated", uuidList(eliminated));
            if (ManhuntGame.currentCheckpoint() != null) {
                root.add("currentCheckpoint", encodePos(ManhuntGame.currentCheckpoint()));
                root.addProperty("currentIsStronghold", ManhuntGame.isCurrentStronghold());
            }
            if (ManhuntGame.rawLastCheckpoint() != null) {
                root.add("lastCheckpoint", encodePos(ManhuntGame.rawLastCheckpoint()));
            }
            root.add("mileage", longMap(MileageManager.snapshotMileage()));
            root.add("rollCount", intMap(MileageManager.snapshotRolls()));
            root.addProperty("morale", MoraleManager.morale());
            root.addProperty("moraleRewards", MoraleManager.rewards());
            root.add("skills", stringMap(com.example.manhunt.cards.SkillSlotManager.snapshotIds()));

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
            Set<UUID> hunters = readUuids(root.get("hunters"));
            Set<UUID> runners = readUuids(root.get("runners"));
            Set<UUID> eliminated = readUuids(root.get("eliminated"));
            BlockPos current = root.has("currentCheckpoint")
                ? decodePos(root.getAsJsonObject("currentCheckpoint")) : null;
            boolean stronghold = root.has("currentIsStronghold") && root.get("currentIsStronghold").getAsBoolean();
            BlockPos last = root.has("lastCheckpoint")
                ? decodePos(root.getAsJsonObject("lastCheckpoint")) : null;
            int activated = root.get("activated").getAsInt();
            boolean solo = root.has("solo") && root.get("solo").getAsBoolean();

            ManhuntGame.restoreState(restored, hunters, runners, eliminated, activated,
                current, stronghold, last, solo);

            Map<UUID, Long> mileage = new HashMap<>();
            if (root.has("mileage")) {
                for (var e : root.getAsJsonObject("mileage").entrySet()) {
                    try {
                        mileage.put(UUID.fromString(e.getKey()), e.getValue().getAsLong());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            Map<UUID, Integer> rolls = new HashMap<>();
            if (root.has("rollCount")) {
                for (var e : root.getAsJsonObject("rollCount").entrySet()) {
                    try {
                        rolls.put(UUID.fromString(e.getKey()), e.getValue().getAsInt());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            MileageManager.restore(mileage, rolls);
            MoraleManager.restore(root.get("morale").getAsInt(), root.get("moraleRewards").getAsInt());
            if (root.has("skills")) {
                Map<UUID, java.util.List<String>> skills = new HashMap<>();
                for (var e : root.getAsJsonObject("skills").entrySet()) {
                    try {
                        java.util.List<String> ids = new java.util.ArrayList<>();
                        for (var el : e.getValue().getAsJsonArray()) {
                            ids.add(el.getAsString());
                        }
                        skills.put(UUID.fromString(e.getKey()), ids);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                com.example.manhunt.cards.SkillSlotManager.restore(skills);
            }

            ManhuntGame.reattachBossbars(server);
            ManhuntGame.broadcast(server, "§6[猎人游戏] §7已从存档恢复对局（已激活 " + activated + " 个检查点）。");
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

    private static com.google.gson.JsonObject stringMap(Map<UUID, java.util.List<String>> map) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        for (Map.Entry<UUID, java.util.List<String>> e : map.entrySet()) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (String id : e.getValue()) {
                arr.add(id);
            }
            o.add(e.getKey().toString(), arr);
        }
        return o;
    }

    private static com.google.gson.JsonObject longMap(Map<UUID, Long> map) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        for (Map.Entry<UUID, Long> e : map.entrySet()) {
            o.addProperty(e.getKey().toString(), e.getValue());
        }
        return o;
    }

    private static com.google.gson.JsonObject intMap(Map<UUID, Integer> map) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        for (Map.Entry<UUID, Integer> e : map.entrySet()) {
            o.addProperty(e.getKey().toString(), e.getValue());
        }
        return o;
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
