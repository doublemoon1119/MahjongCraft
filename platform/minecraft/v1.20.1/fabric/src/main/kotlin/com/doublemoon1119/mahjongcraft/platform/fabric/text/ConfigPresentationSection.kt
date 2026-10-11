package com.doublemoon1119.mahjongcraft.platform.fabric.text

import net.minecraft.text.Text

/**
 * 對應 TOML 區塊的設定分類。
 *
 * @property name 分類的本地化名稱。
 * @property entries 此分類的設定名稱與有效值。
 */
data class ConfigPresentationSection(
    val name: Text,
    val entries: List<ConfigPresentationEntry>,
)
