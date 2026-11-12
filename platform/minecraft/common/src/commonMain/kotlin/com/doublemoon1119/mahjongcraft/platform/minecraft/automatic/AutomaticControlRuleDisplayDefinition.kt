package com.doublemoon1119.mahjongcraft.platform.minecraft.automatic

/**
 * 規則為自動操作登記自己的名稱與說明，覆寫該規則對局中的預設顯示。
 *
 * @property ruleModuleId 套用這份顯示的規則模組 ID。
 * @property controlId 自動操作控制項的穩定識別碼。
 * @property display 這個規則使用的呈現描述。
 */
data class AutomaticControlRuleDisplayDefinition(
    val ruleModuleId: String,
    val controlId: String,
    val display: AutomaticControlDisplay,
)
