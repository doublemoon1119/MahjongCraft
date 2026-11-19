package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.minecraft.handbook.MinecraftHandbookScreenKeys
import net.minecraft.client.item.TooltipContext
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.World

/**
 * 麻將手冊：拿在手上按右鍵開啟麻將手冊快捷畫面，不消耗物品。
 *
 * 開啟畫面是客戶端的行為，由客戶端的使用物品事件處理；伺服器不需要知道。
 *
 * @param settings 物品設定。
 */
class MahjongHandbookItem(settings: Settings) : Item(settings) {
    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        tooltip += Text.translatable(MinecraftHandbookScreenKeys.ITEM_TOOLTIP).formatted(Formatting.GRAY)
    }
}
