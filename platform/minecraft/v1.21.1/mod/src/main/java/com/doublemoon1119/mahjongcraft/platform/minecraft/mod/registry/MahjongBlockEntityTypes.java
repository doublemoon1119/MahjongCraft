package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 建立 MahjongCraft 的方塊實體型別。
 *
 * <p>原版建構器要求一份存檔格式轉換用的資料型別；模組的方塊實體沒有這份資料，因此傳 {@code null}。
 * 這個參數在 Kotlin 端被視為不可為 null，所以由 Java 建立。
 */
public final class MahjongBlockEntityTypes {
    private MahjongBlockEntityTypes() {
    }

    /**
     * 建立方塊實體型別。
     *
     * @param factory 建立方塊實體的函式
     * @param blocks 可以帶有這個方塊實體的方塊
     * @param <T> 方塊實體型別
     * @return 新的方塊實體型別
     */
    public static <T extends BlockEntity> BlockEntityType<T> create(
        BlockEntityType.BlockEntitySupplier<? extends T> factory,
        Block... blocks
    ) {
        return BlockEntityType.Builder.<T>of(factory, blocks).build(null);
    }
}
