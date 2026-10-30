package com.doublemoon1119.mahjongcraft.flow.api.event

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.jvm.JvmSynthetic

/**
 * 一項已成立的規則結果，保留詳情與摘要各自的成立者。
 *
 * @property reasonId 規則結果原因的完整 namespaced ID。
 * @property winDetails 規則提供的公開和牌詳情；不包含隱藏牌或實體牌張識別碼。
 * @property completion 同次提交提供的結算摘要；沒有提供時為 null。
 */
class RuleResultDetail private constructor(
    val reasonId: String,
    val winDetails: List<RoundWinDetail>,
    val completion: RuleResultCompletion?,
) {
    init {
        NamespacedId.requireValid(reasonId) { "Rule result reason ID must be namespaced: $reasonId" }
    }

    /** 事件系統內部建立資料的入口。 */
    internal companion object {
        /**
         * 建立結果並複製詳情清單。
         *
         * @param reasonId 規則結果原因 ID。
         * @param winDetails 公開詳情。
         * @param completion 可選結算摘要。
         * @return 唯讀規則結果。
         */
        @JvmSynthetic
        fun create(
            reasonId: String,
            winDetails: List<RoundWinDetail>,
            completion: RuleResultCompletion?,
        ): RuleResultDetail = RuleResultDetail(
            reasonId = reasonId,
            winDetails = ReadOnlyListSnapshot(winDetails),
            completion = completion,
        )
    }
}
