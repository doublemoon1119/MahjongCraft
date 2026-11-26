package com.doublemoon1119.mahjongcraft.platform.minecraft.automatic

/**
 * 將自動操作控制項 ID 綁定到名稱、說明及排列順序。
 *
 * @property controlId 自動操作控制項的穩定識別碼。
 * @property display 對應的呈現描述。
 */
data class AutomaticControlDisplayDefinition(
    val controlId: String,
    val display: AutomaticControlDisplay,
)
