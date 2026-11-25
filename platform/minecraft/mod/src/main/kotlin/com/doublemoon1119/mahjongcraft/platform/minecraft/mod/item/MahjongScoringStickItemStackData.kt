package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.storeScoringStickDenomination
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.storedScoringStickDenomination
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDenomination
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickItemData
import net.minecraft.world.item.ItemStack

/** 點棒 ItemStack 的面額資料讀寫邊界。 */
object MahjongScoringStickItemStackData {
    /**
     * 缺少面額、無效名稱或錯誤型別時使用百分棒。
     *
     * @param stack 要讀取的點棒物品堆疊。
     * @return 堆疊保存的有效面額。缺失或無效資料時為百分棒。
     */
    fun read(stack: ItemStack): MahjongScoringStickDenomination = MahjongScoringStickItemData.read(stack.storedScoringStickDenomination())

    /**
     * 以面額名稱寫入物品資料。
     *
     * @param stack 要更新的點棒物品堆疊。
     * @param denomination 要保存的點棒面額。
     */
    fun write(stack: ItemStack, denomination: MahjongScoringStickDenomination) {
        stack.storeScoringStickDenomination(MahjongScoringStickItemData.write(denomination))
    }
}
