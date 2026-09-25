import os

BASE = "src/main/java/com/example/manhunt/"
SC = "F:/ZcodeBuild/SkillCards/src/main/java/com/example/skillcards/"

def patch(path, pairs, base=BASE):
    p = base + path
    s = open(p, encoding="utf-8").read()
    changed = False
    for old, new in pairs:
        if new in s and old not in s:
            continue
        assert old in s, f"{path}: anchor not found: {old[:70]!r}"
        s = s.replace(old, new, 1)
        changed = True
    if changed:
        open(p, "w", encoding="utf-8", newline="\n").write(s)
        print(path, "ok")
    else:
        print(path, "skip")

# ============ P1: 要塞眼睛溢出 → 掉落+提醒 ============
patch("game/ManhuntGame.java", [
    ("""        if (isStronghold) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                    com.example.manhunt.util.InvUtil.safeAdd(p,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_EYE, 12));
                }
            }
            broadcast(server, "§6[猎人游戏] §d全队已获得末影之眼 ×12，开启传送门！");
        }""",
     """        if (isStronghold) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (TeamUtil.isRunner(p) && !isEliminated(p.getUUID())) {
                    var eyes = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_EYE, 12);
                    if (!com.example.manhunt.util.InvUtil.safeAdd(p, eyes) && !eyes.isEmpty()) {
                        p.drop(eyes, false); // 背包放不下：掉落在脚下并提醒
                        p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§c[猎人游戏] 背包已满，部分末影之眼未拾取，已掉落在脚下！"), false);
                    }
                }
            }
            broadcast(server, "§6[猎人游戏] §d全队已获得末影之眼 ×12，开启传送门！");
        }"""),
])

# ============ P3: 方向键完整交互 ============
patch("client/ClientRollManager.java", [
    ("""        // 固定备用键：←→ 选择
        if (key == GLFW.GLFW_KEY_LEFT) {
            current.selected = Math.floorMod(current.selected - 1, current.items.size());
            uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F, 0.15F);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            current.selected = Math.floorMod(current.selected + 1, current.items.size());
            uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F, 0.15F);
            return true;
        }
        return false;
    }""",
     """        // 固定备用键：←→ 选择，↑ 标记，↓ 领取标记并退出
        if (key == GLFW.GLFW_KEY_LEFT) {
            current.selected = Math.floorMod(current.selected - 1, current.items.size());
            uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F, 0.15F);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            current.selected = Math.floorMod(current.selected + 1, current.items.size());
            uiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F, 0.15F);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            toggleMark(current);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            claimMarkedAndClose();
            return true;
        }
        return false;
    }"""),
])

# ============ O5: 移除 CoAS+Curios 桥接 ============
bridge = BASE + "compat/CraftingOnAStickBridge.java"
if os.path.exists(bridge):
    os.remove(bridge)
    print("bridge removed")

patch("game/ManhuntGame.java", [
    ("""            SkillCardsBridge.resetPersistentBonuses(p);
            com.example.manhunt.compat.CraftingOnAStickBridge.giveStick(p);
            TeamUtil.applyBaseAttributes(p);""",
     """            SkillCardsBridge.resetPersistentBonuses(p);
            TeamUtil.applyBaseAttributes(p);"""),
])
patch("game/DeathHandler.java", [
    ("""        TeamUtil.refreshBuffs(player);
        MileageManager.syncMeter(player);
        com.example.manhunt.compat.CraftingOnAStickBridge.giveStick(player);
    }""",
     """        TeamUtil.refreshBuffs(player);
        MileageManager.syncMeter(player);
    }"""),
])

# ============ O4: 罗盘不可丢弃 + 周期补发 ============
patch("item/CompassManager.java", [
    ("""    /** 按阵营补发对应罗盘（登录/复活时调用）。 */
    public static void ensureCompasses(ServerPlayer p) {""",
     """    /** 罗盘不可丢弃（取消抛掷，防止掉落被他人拾取）。 */
    public static void onItemToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        if (!ManhuntGame.isRunning()) {
            return;
        }
        var player = event.getPlayer();
        if (!ManhuntGame.isParticipant(player.getUUID())) {
            return;
        }
        ItemStack tossed = event.getEntity().getItem();
        if (tossed.is(ManhuntItems.TRACKING_COMPASS.get()) || tossed.is(ManhuntItems.CHECKPOINT_COMPASS.get())) {
            event.setCanceled(true);
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§7[猎人游戏] 罗盘无法丢弃。"), true);
            }
        }
    }

    /** 按阵营补发对应罗盘（登录/复活时调用）。 */
    public static void ensureCompasses(ServerPlayer p) {"""),
])

# ManhuntGame: 每秒为参与者补发罗盘
patch("game/ManhuntGame.java", [
    ("""                if (participant) {
                    MileageManager.syncMeter(p);
                }""",
     """                if (participant) {
                    CompassManager.ensureCompasses(p);
                    MileageManager.syncMeter(p);
                }"""),
])

# ============ O1: 猎人罗盘 50 格红脉冲边框（渲染层） ============
patch("client/ManhuntClient.java", [
    ("""        if (!ManhuntClientState.isRunner()) {
            return; // 仅逃生者显示固定技能槽
        }""",
     """        if (!ManhuntClientState.isRunner()) {
            renderTrackingCompassBorder(g, mc);
            return; // 猎人显示追踪罗盘边框（贴近逃生者时红色脉冲）
        }"""),
    ("""    private static int withAlpha(int argb, float alpha) {""",
     """    /**
     * 猎人追踪罗盘边框：罗盘所在物品栏格子常显金框；
     * 当罗盘目标（逃生者）与猎人距离 < 50 格时变为红色快速脉冲。
     */
    private static void renderTrackingCompassBorder(GuiGraphicsExtractor g, Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) {
            return;
        }
        var inv = mc.player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack stack = inv.get(slot);
            if (!stack.is(com.example.manhunt.item.ManhuntItems.TRACKING_COMPASS.get())) {
                continue;
            }
            int x = g.guiWidth() / 2 - 90 + slot * 20 + 2;
            int y = g.guiHeight() - 19;
            long now = mc.level.getGameTime();
            float t = now + delta.getGameTimeDeltaPartialTick(false);
            boolean close = false;
            var tracker = stack.get(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER);
            if (tracker != null && tracker.target().isPresent()
                    && tracker.target().get().dimension() == mc.player.level().dimension()) {
                var tpos = tracker.target().get().pos();
                double d2 = mc.player.distanceToSqr(
                    tpos.getX() + 0.5, tpos.getY() + 0.5, tpos.getZ() + 0.5);
                close = d2 < 50.0 * 50.0;
            }
            int col;
            if (close) {
                col = withAlpha(0xFFE33B3B, 0.55F + 0.45F * (float) Math.abs(Math.sin(t * 0.5)));
            } else {
                col = withAlpha(0xFFFFC844, 0.75F);
            }
            g.fill(x - 3, y - 3, x + 19, y - 1, col);
            g.fill(x - 3, y + 17, x + 19, y + 19, col);
            g.fill(x - 3, y - 1, x - 1, y + 17, col);
            g.fill(x + 17, y - 1, x + 19, y + 17, col);
        }
    }

    private static int withAlpha(int argb, float alpha) {"""),
])
print("all base edits ok")
