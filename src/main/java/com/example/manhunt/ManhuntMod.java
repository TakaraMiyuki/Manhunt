package com.example.manhunt;

import com.example.manhunt.game.CheckpointManager;
import com.example.manhunt.game.DeathHandler;
import com.example.manhunt.game.ManhuntGame;
import com.example.manhunt.command.ManhuntCommand;
import com.example.manhunt.item.CompassManager;
import com.example.manhunt.item.ManhuntItems;
import com.example.manhunt.loot.PendingRewardManager;
import com.example.manhunt.net.ManhuntPayloads;
import com.example.manhunt.recipe.EnchantBookRecipe;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.entity.XpOrbTargetingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(ManhuntMod.MODID)
public final class ManhuntMod {
    public static final String MODID = "manhunt";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 合成栏附魔配方序列化器。 */
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    static {
        RECIPE_SERIALIZERS.register("enchant_from_book", () -> EnchantBookRecipe.SERIALIZER);
    }

    public ManhuntMod(IEventBus modEventBus) {
        ManhuntItems.ITEMS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        modEventBus.addListener(ManhuntPayloads::onRegister);

        NeoForge.EVENT_BUS.addListener(ManhuntCommand::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(ManhuntGame::onServerTick);
        NeoForge.EVENT_BUS.addListener(ManhuntMod::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ManhuntMod::onServerStopping);
        NeoForge.EVENT_BUS.addListener(DeathHandler::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(DeathHandler::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(DeathHandler::onDamagePost);
        NeoForge.EVENT_BUS.addListener(DeathHandler::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(DeathHandler::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(DeathHandler::onExperienceDrop);
        NeoForge.EVENT_BUS.addListener(DeathHandler::onPickupXp);
        NeoForge.EVENT_BUS.addListener(DeathHandler::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(DeathHandler::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(DeathHandler::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(CompassManager::onRightClick);
        NeoForge.EVENT_BUS.addListener(ManhuntMod::onLoggedOut);
        // 经验球不再跟随参与者（配合 PickupXp 取消，防止经验球环绕无法吸收）
        NeoForge.EVENT_BUS.addListener(ManhuntMod::onXpOrbTargeting);
        // 技能栏：左键切换（打方块/打实体双侧取消并切换，挥空由客户端发包）
        NeoForge.EVENT_BUS.addListener(com.example.manhunt.cards.SkillSlotManager::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(com.example.manhunt.cards.SkillSlotManager::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(com.example.manhunt.cards.SkillSlotManager::onLeftClickEmpty);
    }

    private static void onXpOrbTargeting(XpOrbTargetingEvent event) {
        if (ManhuntGame.isRunning() && event.getFollowingPlayer() != null
                && ManhuntGame.isParticipant(event.getFollowingPlayer().getUUID())) {
            event.setFollowingPlayer(null);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ManhuntGame.onServerStarted(event.getServer());
        LOGGER.info("[Manhunt] 状态已加载，当前阶段: {}", ManhuntGame.phase());
        if ("1".equals(System.getenv("MANHUNT_SMOKETEST"))) {
            runSmokeTest(event.getServer());
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        ManhuntGame.onServerStopping(event.getServer());
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        var uuid = event.getEntity().getUUID();
        MileageManager_onLoggedOut(uuid);
        DeathHandler.onLoggedOut(uuid);
        com.example.manhunt.cards.SkillSlotManager.onLoggedOut(uuid);
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            PendingRewardManager.autoClaimExisting(player);
        }
    }

    private static void MileageManager_onLoggedOut(java.util.UUID id) {
        com.example.manhunt.game.MileageManager.onLoggedOut(id);
    }

    /**
     * 无玩家冒烟测试（环境变量 MANHUNT_SMOKETEST=1 时启用）：
     * 验证检查点生成、奖池抽取、整包网络编码、技能卡桥接、附魔配方与档位计算。
     */
    private static void runSmokeTest(net.minecraft.server.MinecraftServer server) {
        try {
            CheckpointManager.generateFirst(server);
            var cp = ManhuntGame.currentCheckpoint();
            LOGGER.info("[Manhunt][冒烟测试] 1 号检查点: {}",
                cp == null ? "未生成" : cp.getX() + ", " + cp.getY() + ", " + cp.getZ());

            // 奖池：每个档位抽多件验证无异常，并对整包做真实网络编码验证
            var registryAccess = server.overworld().registryAccess();
            var rng = net.minecraft.util.RandomSource.create();
            for (int tier = 0; tier <= com.example.manhunt.GameConfig.MILEAGE_TIERS.length; tier++) {
                var pool = com.example.manhunt.loot.RewardPools.pool(tier);
                var items = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
                for (int i = 0; i < 20 && !pool.isEmpty(); i++) {
                    items.add(com.example.manhunt.loot.RewardPools.weightedPick(pool).roll(registryAccess));
                }
                encodeVerify(items, registryAccess);
                LOGGER.info("[Manhunt][冒烟测试] 奖池档位 {} 共 {} 种条目，抽样+编码通过", tier + 1, pool.size());
            }
            // 随机附魔书（重点验证：数据包注册表 Holder 的网络编码）
            var book = com.example.manhunt.loot.RewardPools.randomBook(registryAccess, 3, false);
            var advBook = com.example.manhunt.loot.RewardPools.randomBook(registryAccess, 255, true);
            encodeVerify(java.util.List.of(book, advBook), registryAccess);
            LOGGER.info("[Manhunt][冒烟测试] 随机附魔书 {} / 高级附魔书 {} 编码通过",
                book.getItem(), advBook.getItem());

            // 合成栏附魔配方：附魔书 + 剑 → 附魔剑
            var sword = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
            var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1,
                java.util.List.of(sword, book));
            var recipe = EnchantBookRecipe.INSTANCE;
            boolean matched = recipe.matches(input, server.overworld());
            var result = recipe.assemble(input);
            LOGGER.info("[Manhunt][冒烟测试] 合成栏附魔: matches={} 结果={}", matched,
                matched ? result.getItem() + " 附魔数=" + result.getEnchantments().keySet().size() : "无");
            if (!matched || result.getEnchantments().keySet().size() != 1) {
                throw new IllegalStateException("合成栏附魔配方验证失败");
            }

            // 技能卡桥接
            LOGGER.info("[Manhunt][冒烟测试] 技能卡模组可用: {}",
                com.example.manhunt.cards.SkillCardsBridge.available());
            if (com.example.manhunt.cards.SkillCardsBridge.available()) {
                int common = 0, rare = 0, rainbow = 0, black = 0;
                for (int i = 0; i < 200; i++) {
                    var draw = com.example.manhunt.cards.SkillCardsBridge.drawRandom(rng);
                    if (draw == null) {
                        continue;
                    }
                    var grade = com.example.manhunt.cards.SkillCardsBridge.gradeOf(draw.stack());
                    switch (grade.name()) {
                        case "COMMON" -> common++;
                        case "RARE" -> rare++;
                        case "RAINBOW" -> rainbow++;
                        case "BLACK" -> black++;
                        default -> {
                        }
                    }
                }
                LOGGER.info("[Manhunt][冒烟测试] 200 次抽卡分布: 普通={} 稀有={} 彩卡={} 黑卡={}",
                    common, rare, rainbow, black);
            }

            // 档位
            com.example.manhunt.game.TierSystem.recalculate(server);
            LOGGER.info("[Manhunt][冒烟测试] 当前档位: {}",
                com.example.manhunt.game.TierSystem.displayTier());
        } catch (Exception e) {
            LOGGER.error("[Manhunt][冒烟测试] 失败", e);
        }
    }

    /** 用服务端活动注册表把物品整包走一遍 S2C 编码路径——编码失败即冒烟测试失败。 */
    private static void encodeVerify(java.util.List<net.minecraft.world.item.ItemStack> items,
                                     net.minecraft.core.RegistryAccess registryAccess) throws Exception {
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), registryAccess);
        var payload = new com.example.manhunt.net.LootRollPayload(
            com.example.manhunt.net.LootRollPayload.TYPE_SUPER, items, 0,
            com.example.manhunt.net.LootRollPayload.MODE_NEW);
        com.example.manhunt.net.LootRollPayload.STREAM_CODEC.encode(buf, payload);
        if (buf.readableBytes() <= 0) {
            throw new IllegalStateException("payload 编码后为空");
        }
    }
}
