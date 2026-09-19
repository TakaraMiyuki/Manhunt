package com.example.manhunt.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * 指向任意坐标的罗盘。继承原版 CompassItem 以复用客户端"磁石罗盘"渲染
 * （物品模型 property target=lodestone 读取 LODESTONE_TRACKER 组件），
 * 但覆写 inventoryTick 阻止原版在目标处无真实磁石时清空指向。
 */
public class TrackingCompassItem extends CompassItem {
    public TrackingCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        // 不调用 super：保留指向，即使目标位置没有真实磁石方块
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // 不允许对磁石右键重新绑定
        return InteractionResult.PASS;
    }
}
