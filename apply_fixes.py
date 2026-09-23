import re

BASE = "src/main/java/com/example/manhunt/"
SC = "F:/ZcodeBuild/SkillCards/src/main/java/com/example/skillcards/"

def patch(path, pairs, base=BASE):
    p = base + path
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

# ============ GameConfig: 概率 + 摔落保护 ============
patch("GameConfig.java", [
    ("public static final int CARD_WEIGHT_COMMON = 68;", "public static final int CARD_WEIGHT_COMMON = 54;"),
    ("public static final int CARD_WEIGHT_RARE = 20;", "public static final int CARD_WEIGHT_RARE = 30;"),
    ("public static final int CARD_WEIGHT_BLACK = 10;", "public static final int CARD_WEIGHT_BLACK = 12;"),
    ("public static final int CARD_WEIGHT_RAINBOW = 2;", "public static final int CARD_WEIGHT_RAINBOW = 4;"),
    ("""    /** 士气增长后，士气量表接管猎人经验条的持续时间（刻）。 */
    public static final int MORALE_METER_SHOW_TICKS = 100;""",
     """    /** 士气增长后，士气量表接管猎人经验条的持续时间（刻）。 */
    public static final int MORALE_METER_SHOW_TICKS = 100;
    /** 摔落伤害的保底生命值（保留最后 2 点 = 1 颗心，不会摔死）。 */
    public static final float FALL_MIN_HEALTH = 2.0F;"""),
])

# ============ DeathHandler: 摔落保底 ============
patch("game/DeathHandler.java", [
    ("""    /** 逃跑倒计时期间猎人无法伤害逃生者；末影龙与末影人始终无法伤害猎人。 */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }""",
     """    /** 逃跑倒计时期间猎人无法伤害逃生者；末影龙与末影人始终无法伤害猎人；
     *  参与者不会摔死——摔落伤害最多扣到保留 1 颗心。 */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (ManhuntGame.isRunning() && ManhuntGame.isParticipant(victim.getUUID())
                && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
            float keep = com.example.manhunt.GameConfig.FALL_MIN_HEALTH;
            float health = victim.getHealth();
            if (event.getAmount() >= health - keep) {
                event.setAmount(Math.max(0.0F, health - keep));
            }
        }"""),
])

# ============ ManhuntGame: 龙重生 + 要塞眼睛 ============
patch("game/ManhuntGame.java", [
    ("""        ManhuntStateIO.save(server);
        return null;
    }

    public static void stop(MinecraftServer server) {""",
     """        // 每局开始时确保末地有龙：上一局被击杀后自动重生（未击杀过则为无操作）
        ServerLevel end = server.getLevel(Level.END);
        if (end != null && end.getDragonFight() != null) {
            end.getDragonFight().tryRespawn();
        }
        ManhuntStateIO.save(server);
        return null;
    }

    public static void stop(MinecraftServer server) {"""),
    ("""        // 超级抽奖：全体存活逃生者
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                com.example.manhunt.loot.LootRoller.superRoll(p);
            }
        }""",
     """        // 超级抽奖：全体存活逃生者
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                com.example.manhunt.loot.LootRoller.superRoll(p);
            }
        }
        // 要塞检查点：保障开门物资（鞘翅 + 足量末影之眼）
        if (isStronghold) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                    com.example.manhunt.util.InvUtil.safeAdd(p,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ELYTRA));
                    com.example.manhunt.util.InvUtil.safeAdd(p,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_EYE, 12));
                }
            }
            broadcast(server, "§6[猎人游戏] §d全队已获得鞘翅 ×1 + 末影之眼 ×12，开启传送门！");
        }"""),
])

# ============ PendingRewardManager: 积攒单槽 ============
patch("loot/PendingRewardManager.java", [
    ("""    /** 超级抽奖进行中积攒的普通资源抽奖（超级结束后依次开启，上限 6 次）。 */
    private static final Map<UUID, List<Pending>> QUEUED = new HashMap<>();
    private static final int MAX_QUEUED = 6;""",
     """    /** 超级抽奖进行中积攒的普通资源抽奖（仅保存最近一次，新的覆盖旧的）。 */
    private static final Map<UUID, Pending> QUEUED = new HashMap<>();"""),
    ("""            List<Pending> queue = QUEUED.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
            if (queue.size() < MAX_QUEUED) {
                queue.add(new Pending(type, new ArrayList<>(items)));
                player.sendSystemMessage(Component.literal(
                        "§7[猎人游戏] 超级抽奖进行中，本次资源抽奖已积攒（"
                            + queue.size() + "/" + MAX_QUEUED + "）。"), true);
            } else {
                player.sendSystemMessage(Component.literal(
                        "§7[猎人游戏] 积攒已满，本次资源抽奖已放弃。"), true);
            }
            return;""",
     """            QUEUED.put(player.getUUID(), new Pending(type, new ArrayList<>(items)));
            player.sendSystemMessage(Component.literal(
                    "§7[猎人游戏] 超级抽奖进行中，本次资源抽奖已保存。"), true);
            return;"""),
    ("""        // 超级抽奖结束：依次开启积攒的普通资源抽奖
        List<Pending> queue = QUEUED.remove(player.getUUID());
        if (queue != null && !queue.isEmpty()) {
            Pending next = queue.get(0);
            if (queue.size() > 1) {
                QUEUED.put(player.getUUID(), new ArrayList<>(queue.subList(1, queue.size())));
            }
            PENDING.put(player.getUUID(), next);
            send(player, new LootRollPayload(next.type(), next.remaining(), accentOf(next.type()),
                LootRollPayload.MODE_NEW));
            player.sendSystemMessage(Component.literal(
                    "§7[猎人游戏] 积攒的资源抽奖已开启（剩余 " + Math.max(0, queue.size() - 1) + " 次）。"), true);
        }
    }""",
     """        // 超级抽奖结束：开启积攒的普通资源抽奖
        Pending queued = QUEUED.remove(player.getUUID());
        if (queued != null) {
            PENDING.put(player.getUUID(), queued);
            send(player, new LootRollPayload(queued.type(), queued.remaining(), accentOf(queued.type()),
                LootRollPayload.MODE_NEW));
            player.sendSystemMessage(Component.literal(
                    "§7[猎人游戏] 积攒的资源抽奖已开启。"), true);
        }
    }"""),
    ("""    /** 逃生者淘汰：奖励作废（不入包）。 */
    public static void discard(UUID id) {
        PENDING.remove(id);
        QUEUED.remove(id);
    }""",
     """    /** 逃生者淘汰：奖励作废（不入包）。 */
    public static void discard(UUID id) {
        PENDING.remove(id);
        QUEUED.remove(id);
    }"""),
    ("""        List<Pending> queue = QUEUED.remove(player.getUUID());
        if (queue != null) {
            for (Pending p : queue) {
                for (ItemStack stack : p.remaining()) {
                    give(player, stack);
                }
            }
        }
    }""",
     """        Pending queued = QUEUED.remove(player.getUUID());
        if (queued != null) {
            for (ItemStack stack : queued.remaining()) {
                give(player, stack);
            }
        }
    }"""),
    ("""        for (Map.Entry<UUID, List<Pending>> e : QUEUED.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                for (Pending pending : e.getValue()) {
                    for (ItemStack stack : pending.remaining()) {
                        give(p, stack);
                    }
                }
            }
        }
        PENDING.clear();
        QUEUED.clear();""",
     """        for (Map.Entry<UUID, Pending> e : QUEUED.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                for (ItemStack stack : e.getValue().remaining()) {
                    give(p, stack);
                }
            }
        }
        PENDING.clear();
        QUEUED.clear();"""),
])

# ============ ClientRollManager: 空标记右键直接退出 + 绿色选中框 ============
patch("client/ClientRollManager.java", [
    ("""        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            // 右键：领取全部标记物品并关闭界面（未标记则领取当前选中项）
            List<Integer> claim = current.marked.isEmpty()
                ? List.of(current.selected)
                : new ArrayList<>(current.marked);
            sendClaim(claim);
            current = null;
            return true;
        }""",
     """        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            // 右键：领取全部标记物品并关闭界面；未标记任何物品则直接退出（全部丢弃）
            sendClaim(new ArrayList<>(current.marked));
            current = null;
            return true;
        }"""),
    ("""                if (i == roll.selected) {
                    int glow = withAlpha(mixAlpha(0xFFFFFF00, pulse), alpha);
                    g.fill(sx - 1, barY - 1, sx + icon + 1, barY, glow);
                    g.fill(sx - 1, barY + icon, sx + icon + 1, barY + icon + 1, glow);
                    g.fill(sx - 1, barY, sx, barY + icon, glow);
                    g.fill(sx + icon, barY, sx + icon + 1, barY + icon, glow);
                }""",
     """                if (i == roll.selected) {
                    // 选中框：资源抽奖为绿色（区别于超级抽奖的金色），2px 全框
                    int sel = withAlpha(roll.type == LootRollPayload.TYPE_SUPER
                        ? mixAlpha(0xFFFFFF00, pulse) : 0xFF3CE13C, alpha);
                    g.fill(sx - 2, barY - 2, sx + icon + 2, barY, sel);
                    g.fill(sx - 2, barY + icon, sx + icon + 2, barY + icon + 2, sel);
                    g.fill(sx - 2, barY, sx, barY + icon, sel);
                    g.fill(sx + icon, barY, sx + icon + 2, barY + icon, sel);
                }"""),
])

# ============ SkillCards: 亚丝缇 → 稀有卡 ============
patch("registry/Card.java", [
    ('YASITI("yasiti", Grade.RAINBOW, 300, YaSiTiCard::activate),',
     'YASITI("yasiti", Grade.RARE, 300, YaSiTiCard::activate),'),
], base=SC)
PYEOF
