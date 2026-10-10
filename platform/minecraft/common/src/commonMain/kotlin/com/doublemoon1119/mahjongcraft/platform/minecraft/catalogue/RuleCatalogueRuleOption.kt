package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

/**
 * 規則選擇的文字資料；缺少名稱時保留 ID，不排除沒有目錄的已知規則。
 *
 * @property ruleModuleId 穩定規則 ID。
 * @property displayName 當前語言的名稱，或缺少翻譯時的規則 ID。
 */
data class RuleCatalogueRuleOption(
    val ruleModuleId: String,
    val displayName: String,
)
