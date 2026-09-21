package com.example.manhunt.net;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * S2C：抽奖展示动画数据。物品已在服务端入包，此包只驱动客户端"老虎机"动画。
 *
 * @param rollType    0=资源抽奖(5格) 1=超级抽奖(7格) 2=技能卡(单卡翻转, items[0] 为卡)
 * @param items       展示/实际发放的物品
 * @param accentColor 强调色（ARGB）：资源/超级抽奖用边框色，技能卡为品级色
 */
public record LootRollPayload(int rollType, List<ItemStack> items, int accentColor) implements CustomPacketPayload {
    public static final int TYPE_RESOURCE = 0;
    public static final int TYPE_SUPER = 1;
    public static final int TYPE_CARD = 2;

    public static final CustomPacketPayload.Type<LootRollPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("manhunt", "loot_roll"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> ITEM_LIST_CODEC =
        ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list());

    /** 手写编解码：VAR_INT 与物品列表的 B 类型不同，无法用 composite 合成。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, LootRollPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            ByteBufCodecs.VAR_INT.encode(buf, payload.rollType);
            ITEM_LIST_CODEC.encode(buf, payload.items);
            ByteBufCodecs.VAR_INT.encode(buf, payload.accentColor);
        },
        buf -> new LootRollPayload(
            ByteBufCodecs.VAR_INT.decode(buf),
            ITEM_LIST_CODEC.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
