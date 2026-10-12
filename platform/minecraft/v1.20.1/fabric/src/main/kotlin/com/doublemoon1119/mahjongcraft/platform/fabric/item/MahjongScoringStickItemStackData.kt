package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDenomination
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickItemData
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement

/** 點棒 ItemStack 的面額資料讀寫邊界。 */
object MahjongScoringStickItemStackData {
    /** 物品面額欄位名稱。 */
    const val NBT_KEY_DENOMINATION: String = MahjongScoringStickItemData.DENOMINATION_KEY

    /**
     * 缺少面額、無效名稱或錯誤型別時使用百分棒。
     *
     * @param stack 要讀取的點棒物品堆疊。
     * @return 堆疊保存的有效面額；缺失或無效資料時為百分棒。
     */
    fun read(stack: ItemStack): MahjongScoringStickDenomination = readNbt(stack.nbt)

    /**
     * 從物品的 NBT 欄位讀取面額。
     *
     * @param nbt 物品 NBT；沒有資料時傳入 `null`。
     * @return 欄位中的有效面額；缺失、錯誤型別或未知名稱時為百分棒。
     */
    internal fun readNbt(nbt: NbtCompound?): MahjongScoringStickDenomination {
        val storedName = nbt?.takeIf { it.contains(NBT_KEY_DENOMINATION, NbtElement.STRING_TYPE.toInt()) }?.getString(NBT_KEY_DENOMINATION)
        return MahjongScoringStickItemData.read(storedName)
    }

    /**
     * 以面額名稱寫入物品資料。
     *
     * @param stack 要更新的點棒物品堆疊。
     * @param denomination 要保存的點棒面額。
     */
    fun write(stack: ItemStack, denomination: MahjongScoringStickDenomination) {
        writeNbt(stack.orCreateNbt, denomination)
    }

    /**
     * 將面額寫入物品的 NBT 欄位。
     *
     * @param nbt 目的物品 NBT。
     * @param denomination 要保存的點棒面額。
     */
    internal fun writeNbt(nbt: NbtCompound, denomination: MahjongScoringStickDenomination) {
        nbt.putString(NBT_KEY_DENOMINATION, MahjongScoringStickItemData.write(denomination))
    }
}
