package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDenomination
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement

/**
 * 點棒面額的持久化讀寫。
 *
 * 一律以 enum 名稱保存：名稱不受 enum 宣告順序影響，在中間插入新面額時既有存檔仍然指向同一種點棒。
 */
internal object MahjongScoringStickDenominationNbt {
    /** 以名稱寫入面額。 */
    fun write(nbt: NbtCompound, key: String, denomination: MahjongScoringStickDenomination) {
        nbt.putString(key, denomination.name)
    }

    /** 讀取面額；缺少名稱或名稱無效時使用百分棒。 */
    fun read(nbt: NbtCompound, key: String): MahjongScoringStickDenomination = when {
        nbt.contains(key, NbtElement.STRING_TYPE.toInt()) ->
            MahjongScoringStickDenomination.fromNameOrDefault(nbt.getString(key))
        else -> MahjongScoringStickDenomination.P100
    }
}
