package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import net.minecraft.network.chat.Component

/**
 * 客戶端可直接呈現的一項自動操作顯示資料。
 *
 * @property controlId 控制項識別碼。
 * @property label 本地化項目名稱。
 * @property description 本地化說明；沒有說明時為 null。
 * @property displayOrder 顯示排序值。
 */
data class ResolvedAutomaticControlDisplay(
    val controlId: String,
    val label: Component,
    val description: Component?,
    val displayOrder: Int,
)
