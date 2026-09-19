package com.example.manhunt.item;

import com.example.manhunt.ManhuntMod;

import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品注册。
 */
public final class ManhuntItems {
    private ManhuntItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ManhuntMod.MODID);

    /** 猎人的追踪罗盘：指向所选逃生者，右键切换目标。 */
    public static final DeferredItem<TrackingCompassItem> TRACKING_COMPASS =
        ITEMS.registerItem("tracking_compass",
            properties -> new TrackingCompassItem(properties.stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** 逃生者的检查点罗盘：指向当前目标检查点。 */
    public static final DeferredItem<TrackingCompassItem> CHECKPOINT_COMPASS =
        ITEMS.registerItem("checkpoint_compass",
            properties -> new TrackingCompassItem(properties.stacksTo(1).rarity(Rarity.UNCOMMON)));
}
