package com.example.manhunt.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * C2S：调试/未开局状态下直接设置技能栏槽位（放入或取出）。
 */
public record SkillBarEditPayload(int slot, ItemStack stack) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SkillBarEditPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("manhunt", "skill_bar_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillBarEditPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(buf, payload.slot());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.stack());
        },
        buf -> new SkillBarEditPayload(
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT.decode(buf),
            ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
