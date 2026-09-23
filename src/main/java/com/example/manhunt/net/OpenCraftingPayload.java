package com.example.manhunt.net;

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
