package com.example.manhunt.net;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * S2C：抽奖展示数据。
 *
 * @param rollType    0=资源抽奖(5格) 1=超级抽奖(7格) 2=技能卡(单卡翻转, items[0] 为卡)
 * @param items       展示物品（资源/超级抽奖为待领取奖励）
 * @param accentColor 强调色（ARGB）：边框/品级色
 * @param mode        0=新抽奖（重置领取会话） 1=领取刷新（原位更新剩余物品，空列表=关闭）
 * @param title       自定义标题翻译键（null=按类型显示默认标题"资源抽奖/超级抽奖"）
 */
public record LootRollPayload(int rollType, List<ItemStack> items, int accentColor, int mode,
                              @Nullable String title)
    implements CustomPacketPayload {
    public static final int TYPE_RESOURCE = 0;
    public static final int TYPE_SUPER = 1;
    public static final int TYPE_CARD = 2;
    public static final int MODE_NEW = 0;
    public static final int MODE_REFRESH = 1;

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
            ByteBufCodecs.VAR_INT.encode(buf, payload.mode);
            buf.writeBoolean(payload.title != null);
            if (payload.title != null) {
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.title);
            }
        },
        buf -> new LootRollPayload(
            ByteBufCodecs.VAR_INT.decode(buf),
            ITEM_LIST_CODEC.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            buf.readBoolean() ? ByteBufCodecs.STRING_UTF8.decode(buf) : null));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
