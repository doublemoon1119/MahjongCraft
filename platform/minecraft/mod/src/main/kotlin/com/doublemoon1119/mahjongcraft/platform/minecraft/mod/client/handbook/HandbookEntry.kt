package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.handbook

import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.item.ItemStack

/**
 * 麻將手冊快捷畫面上的一個入口。
 *
 * @property icon 入口左側繪製的物品。
 * @property titleKey 入口名稱的翻譯鍵。
 * @property descriptionKey 入口說明的翻譯鍵。
 * @property open 以快捷畫面為返回目標開啟功能頁。
 */
internal data class HandbookEntry(
    val icon: ItemStack,
    val titleKey: String,
    val descriptionKey: String,
    val open: (returnScreen: Screen) -> Unit,
)
