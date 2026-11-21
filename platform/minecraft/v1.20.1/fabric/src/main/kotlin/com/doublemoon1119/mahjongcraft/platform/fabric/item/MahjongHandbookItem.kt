package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.minecraft.handbook.MinecraftHandbookScreenKeys
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

/**
 * 麻將手冊：拿在手上按右鍵開啟麻將手冊快捷畫面，不消耗物品。
 *
 * 開啟畫面是客戶端的行為，由客戶端的使用物品事件處理；伺服器不需要知道。
 *
 * @param settings 物品設定。
 */
class MahjongHandbookItem(settings: Properties) : Item(settings) {
    override fun appendHoverText(stack: ItemStack, world: Level?, tooltip: MutableList<Component>, context: TooltipFlag) {
        tooltip += Component.translatable(MinecraftHandbookScreenKeys.ITEM_TOOLTIP).withStyle(ChatFormatting.GRAY)
    }
}
