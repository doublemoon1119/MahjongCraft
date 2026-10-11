package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import net.minecraft.text.Text

/** Client 可直接呈現的一項自動操作顯示資料。 */
data class ResolvedAutomaticControlDisplay(
    val controlId: String,
    val label: Text,
    val description: Text?,
    val displayOrder: Int,
)
