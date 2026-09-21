package com.example.manhunt.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册。S2C 动画包的 handler 在客户端类（ManhuntClient）中注册。
 */
public final class ManhuntPayloads {
    private ManhuntPayloads() {}

    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // 仅注册编解码；客户端 handler 由 RegisterClientPayloadHandlersEvent 接管
        registrar.playToClient(LootRollPayload.TYPE, LootRollPayload.STREAM_CODEC);
    }
}
