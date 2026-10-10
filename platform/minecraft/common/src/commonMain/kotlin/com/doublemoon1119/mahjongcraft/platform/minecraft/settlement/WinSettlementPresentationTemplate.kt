package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 第三方可完整替換的胡牌結算面板模板。
 *
 * @property key 模板的唯一 namespaced 識別碼。
 * @property root 模板的宣告式布局樹。
 * @property reveal 模板使用的揭示動畫與音效設定。
 * @property detailFieldLabelKeys 規則詳情欄位 ID 對應的本地化標題 key；未列出的欄位不預設標題。
 */
data class WinSettlementPresentationTemplate(

    val key: String,
    val root: PresentationLayout,
    val reveal: WinSettlementRevealSequence = WinSettlementRevealSequence(),
    val detailFieldLabelKeys: Map<PresentationFieldId, String> = emptyMap(),
) {
    init {
        NamespacedId.requireValid(key) {
            "Win settlement template key must be namespaced: $key"
        }
    }
}
