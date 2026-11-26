package com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 建立範例的方塊實體型別。
 *
 * <p>原版建構器要求一份存檔格式轉換用的資料型別；範例的方塊實體沒有這份資料，因此傳 {@code null}。
 * Forge 把這個參數標成不可為 null，Kotlin 端會因此警告，所以由 Java 建立。建立方塊實體的函式型別原版不公開，由本體的
 * access transformer 開放。
 */
public final class ExampleBlockEntityTypes {
    private ExampleBlockEntityTypes() {
    }

    /**
     * 建立方塊實體型別。
     *
     * @param factory 建立方塊實體的函式
     * @param blocks 可以帶有這個方塊實體的方塊
     * @param <T> 方塊實體型別
     * @return 尚未登記的方塊實體型別
     */
    public static <T extends BlockEntity> BlockEntityType<T> create(
        BlockEntityType.BlockEntitySupplier<? extends T> factory,
        Block... blocks
    ) {
        return BlockEntityType.Builder.<T>of(factory, blocks).build(null);
    }
}
