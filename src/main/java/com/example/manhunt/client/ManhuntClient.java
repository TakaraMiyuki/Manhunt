package com.example.manhunt.client;

import com.example.manhunt.ManhuntMod;
import com.example.manhunt.net.LootRollPayload;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * 客户端事件订阅：HUD 抽奖动画层 + S2C 动画包 handler + 动画 tick。
 * （NeoForge 按 IModBusEvent 自动路由 mod 总线 / 游戏总线）
 */
@EventBusSubscriber(modid = ManhuntMod.MODID, value = Dist.CLIENT)
public final class ManhuntClient {
    private ManhuntClient() {}

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
            Identifier.fromNamespaceAndPath(ManhuntMod.MODID, "loot_roll"),
            ClientRollManager::render);
    }

    @SubscribeEvent
    public static void onRegisterClientPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(LootRollPayload.TYPE, (payload, ctx) -> ClientRollManager.start(payload));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientRollManager.tick();
    }
}
