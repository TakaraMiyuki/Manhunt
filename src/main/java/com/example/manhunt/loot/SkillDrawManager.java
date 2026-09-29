package com.example.manhunt.loot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.manhunt.cards.SkillCardsBridge;
import com.example.manhunt.cards.SkillSlotManager;
import com.example.manhunt.net.LootRollPayload;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 技能三选一（赏金模式，总里程池跨档触发）：
 * 每名在线存活逃生者抽 3 张互不相同且未拥有的卡，客户端选择后确认领取（只领一张，其余放弃）。
 * 新一轮到来覆盖旧轮；离线玩家跳过不补偿。
 */
public final class SkillDrawManager {
    private SkillDrawManager() {}

    private static final RandomSource RNG = RandomSource.create();
    /** 待选择：uuid → 3 张候选。 */
    private static final Map<UUID, List<SkillCardsBridge.CardDraw>> PENDING = new HashMap<>();

    /** 全体在线存活逃生者各触发一次三选一。 */
    public static void offerAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (com.example.manhunt.game.TeamUtil.isRunner(p)
                    && !com.example.manhunt.game.ManhuntGame.isEliminated(p.getUUID())) {
                offer(p);
            }
        }
    }

    public static void offer(ServerPlayer player) {
        if (!SkillCardsBridge.available()) {
            return;
        }
        List<SkillCardsBridge.CardDraw> choices = new ArrayList<>();
        var exclude = new java.util.HashSet<>(SkillSlotManager.ownedIds(player));
        for (int i = 0; i < 3; i++) {
            SkillCardsBridge.CardDraw draw = SkillCardsBridge.drawRandom(RNG, exclude);
            if (draw == null) {
                break; // 已集齐全部卡牌
            }
            exclude.add(itemIdOf(draw));
            choices.add(draw);
        }
        if (choices.isEmpty()) {
            return;
        }
        if (PENDING.put(player.getUUID(), choices) != null) {
            player.sendSystemMessage(Component.literal("§7[赏金猎人] 上一轮技能三选一已放弃。"), true);
        }
        List<net.minecraft.world.item.ItemStack> stacks = new ArrayList<>();
        for (SkillCardsBridge.CardDraw draw : choices) {
            stacks.add(draw.stack());
        }
        PacketDistributor.sendToPlayer(player, new LootRollPayload(
            LootRollPayload.TYPE_SKILL, stacks, com.example.manhunt.GameConfig.SKILL_CHOICE_ACCENT,
            LootRollPayload.MODE_NEW, "§d技能抽奖 §7· §f三选一"));
    }

    /** 客户端确认（C2S）：领取下标对应的卡。 */
    public static void pick(ServerPlayer player, int index) {
        List<SkillCardsBridge.CardDraw> choices = PENDING.remove(player.getUUID());
        if (choices == null || index < 0 || index >= choices.size()) {
            return;
        }
        SkillCardsBridge.CardDraw chosen = choices.get(index);
        SkillSlotManager.giveDrawnCard(player, chosen);
        player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.6F, 1.4F);
    }

    /** 客户端动画结束即进入选择阶段的判定数据。 */
    public static boolean hasPending(UUID id) {
        return PENDING.containsKey(id);
    }

    private static String itemIdOf(SkillCardsBridge.CardDraw draw) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(draw.stack().getItem()).toString();
    }

    public static void reset() {
        PENDING.clear();
    }
}
