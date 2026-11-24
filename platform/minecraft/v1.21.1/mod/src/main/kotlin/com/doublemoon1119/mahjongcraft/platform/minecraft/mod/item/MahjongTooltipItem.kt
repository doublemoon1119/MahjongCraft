package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item

import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

/**
 * 在物品提示框加入說明文字的物品：子類別在 [addTooltipLines] 加入文字，各版本的原版提示框方法都交給它。
 *
 * @param properties 原版物品設定。
 */
abstract class MahjongTooltipItem(properties: Properties) : Item(properties) {
    /**
     * 在提示框的物品名稱之後加入說明文字。
     *
     * @param stack 顯示提示框的物品。
     * @param lines 提示框的文字，依顯示順序。
     * @param flag 是否顯示進階資訊。
     */
    protected abstract fun addTooltipLines(stack: ItemStack, lines: MutableList<Component>, flag: TooltipFlag)

    final override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        addTooltipLines(stack, tooltip, flag)
    }
}
