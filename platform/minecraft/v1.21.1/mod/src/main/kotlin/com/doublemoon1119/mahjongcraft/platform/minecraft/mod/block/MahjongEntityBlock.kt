package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block

import com.mojang.serialization.MapCodec
import net.minecraft.world.level.block.BaseEntityBlock

/**
 * 本 mod 帶方塊實體的方塊的基底：右鍵互動覆寫原版的 `useWithoutItem`，原版只在主手右鍵時呼叫。各版本專屬的要求（例如方塊 codec）由
 * 這個類別處理。
 *
 * @param properties 原版方塊設定。
 */
abstract class MahjongEntityBlock(properties: Properties) : BaseEntityBlock(properties) {
    /** 這些方塊不從資料建立，codec 只回傳這個方塊本身。 */
    private val blockCodec: MapCodec<MahjongEntityBlock> = MapCodec.unit(this)

    final override fun codec(): MapCodec<out BaseEntityBlock> = blockCodec
}
