import re

BASE = "src/main/java/com/example/manhunt/"

def patch(path, pairs):
    p = BASE + path
    s = open(p, encoding="utf-8").read()
    changed = False
    for old, new in pairs:
        if new in s and old not in s:
            continue  # already applied
        assert old in s, f"{path}: anchor not found: {old[:70]!r}"
        s = s.replace(old, new, 1)
        changed = True
    if changed:
        open(p, "w", encoding="utf-8", newline="\n").write(s)
        print(path, "ok")
    else:
        print(path, "skip (already applied)")

# ============ 3×3 便携合成：Payload ============
open(BASE + "net/OpenCraftingPayload.java", "w", encoding="utf-8", newline="\n").write('''package com.example.manhunt.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S：请求打开 3×3 便携合成（按键触发，服务端打开原版工作台菜单）。
 */
public record OpenCraftingPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenCraftingPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("manhunt", "open_crafting"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCraftingPayload> STREAM_CODEC =
        StreamCodec.unit(new OpenCraftingPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
''')
print("payload ok")

# ============ ManhuntPayloads: 注册 handler ============
patch("net/ManhuntPayloads.java", [
    ("""import com.example.manhunt.cards.SkillSlotManager;
import com.example.manhunt.loot.PendingRewardManager;""",
     """import com.example.manhunt.cards.SkillSlotManager;
import com.example.manhunt.loot.PendingRewardManager;

import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.CraftingMenu;"""),
    ("""        // 切换技能
        registrar.playToServer(SkillSwitchPayload.TYPE, SkillSwitchPayload.STREAM_CODEC,
            (payload, ctx) -> {
                if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                    SkillSlotManager.switchSkill(player);
                }
            });""",
     """        // 切换技能
        registrar.playToServer(SkillSwitchPayload.TYPE, SkillSwitchPayload.STREAM_CODEC,
            (payload, ctx) -> {
                if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                    SkillSlotManager.switchSkill(player);
                }
            });
        // 3×3 便携合成（覆盖 stillValid 使菜单不因离开方块而关闭）
        registrar.playToServer(OpenCraftingPayload.TYPE, OpenCraftingPayload.STREAM_CODEC,
            (payload, ctx) -> {
                if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                    player.openMenu(new SimpleMenuProvider(
                        id -> new CraftingMenu(id, player.getInventory()) {
                            @Override
                            public boolean stillValid(net.minecraft.world.entity.player.Player p) {
                                return true;
                            }
                        },
                        Component.literal("3×3 合成")));
                }
            });"""),
])

# ============ ManhuntClient: 键位 + 发包 ============
patch("client/ManhuntClient.java", [
    ("""    /** 领取标记物品并退出选择阶段（默认鼠标右键，可改键）。 */
    public static final KeyMapping LOOT_CLAIM = new KeyMapping(
        "key.manhunt.loot_claim", KeyConflictContext.IN_GAME,
        InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, LOOT_CATEGORY);""",
     """    /** 领取标记物品并退出选择阶段（默认鼠标右键，可改键）。 */
    public static final KeyMapping LOOT_CLAIM = new KeyMapping(
        "key.manhunt.loot_claim", KeyConflictContext.IN_GAME,
        InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, LOOT_CATEGORY);
    /** 打开 3×3 便携合成（默认 G 键，可改键）。 */
    public static final KeyMapping PORTABLE_CRAFTING = new KeyMapping(
        "key.manhunt.crafting", KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, LOOT_CATEGORY);"""),
    ("""        event.registerCategory(LOOT_CATEGORY);
        event.register(LOOT_MARK);
        event.register(LOOT_CLAIM);
    }""",
     """        event.registerCategory(LOOT_CATEGORY);
        event.register(LOOT_MARK);
        event.register(LOOT_CLAIM);
        event.register(PORTABLE_CRAFTING);
    }"""),
    ("""    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientRollManager.tick();
    }""",
     """    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientRollManager.tick();
        while (PORTABLE_CRAFTING.consumeClick()) {
            net.neoforged.neoforge.network.ClientPacketDistributor.sendToServer(
                new com.example.manhunt.net.OpenCraftingPayload());
        }
    }"""),
])

# ============ lang ============
patch("resources/assets/manhunt/lang/zh_cn.json", [
    ('"key.manhunt.loot_claim": "领取标记的奖励"',
     '"key.manhunt.loot_claim": "领取标记的奖励",\n  "key.manhunt.crafting": "打开 3×3 合成"'),
], base="")
patch("resources/assets/manhunt/lang/en_us.json", [
    ('"key.manhunt.loot_claim": "Claim Marked Rewards"',
     '"key.manhunt.loot_claim": "Claim Marked Rewards",\n  "key.manhunt.crafting": "Open 3×3 Crafting"'),
], base="")
