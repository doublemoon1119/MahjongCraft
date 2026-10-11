package com.doublemoon1119.mahjongcraft.platform.fabric.text

import net.minecraft.text.Text

/**
 * 設定指令 hover 使用的一個本地化欄位與格式化值。
 *
 * @property name 欄位名稱。
 * @property displayedValue 欄位目前值。
 */
data class ConfigPresentationEntry(
    val name: Text,
    val displayedValue: Text,
)
