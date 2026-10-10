package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/**
 * 第三方可調整的受控終局面板模板。
 *
 * @property key 完整 namespaced template key。
 * @property titleTranslationKey 面板標題翻譯 key。
 * @property backgroundArgb 背景 ARGB。
 * @property titleRgb 標題 RGB。
 * @property rankRgb 一般名次 RGB。
 * @property championRgb 第一名 RGB。
 * @property scoreRgb 最終點數 RGB。
 * @property revealOrder 逐名揭曉順序。
 * @property rowRevealIntervalTicks 兩列揭曉起點的間隔。
 * @property readingTicks 全部揭曉後的閱讀時間。
 * @property rowSoundId 一般名次揭曉聲音 resource ID。
 * @property championSoundId 第一名揭曉聲音 resource ID。
 */
data class MatchSettlementPresentationTemplate(
    val key: String,
    val titleTranslationKey: String,
    val backgroundArgb: Int = 0xB8000000.toInt(),
    val titleRgb: Int = 0xFFD37A,
    val rankRgb: Int = 0xFFD86A,
    val championRgb: Int = 0xFFD65A,
    val scoreRgb: Int = 0xFFF3C4,
    val revealOrder: MatchSettlementRevealOrder = MatchSettlementRevealOrder.LAST_TO_FIRST,
    val rowRevealIntervalTicks: Int = 12,
    val readingTicks: Int = 100,
    val rowSoundId: String = "minecraft:entity.experience_orb.pickup",
    val championSoundId: String = "minecraft:ui.toast.challenge_complete",
) {
    init {
        NamespacedId.requireValid(key) { "Match settlement template key must be namespaced: $key" }
        require(titleTranslationKey.isNotBlank())
        require(rowRevealIntervalTicks in 1..100 && readingTicks in 20..1200)
        MinecraftResourceIds.requireValid(rowSoundId) { "Invalid match settlement row sound ID: $rowSoundId" }
        MinecraftResourceIds.requireValid(championSoundId) { "Invalid match settlement champion sound ID: $championSoundId" }
    }
}
