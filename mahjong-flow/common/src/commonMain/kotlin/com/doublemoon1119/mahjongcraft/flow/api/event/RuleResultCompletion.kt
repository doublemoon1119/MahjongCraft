package com.doublemoon1119.mahjongcraft.flow.api.event

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 規則結果附帶的公開結算摘要；沒有摘要與沒有受益者是不同情況。
 *
 * @property outcomeId 結算結果的完整 namespaced ID。
 * @property kind 結算分類。
 * @property beneficiaryPlayerIds 摘要記錄的受益者，不與詳情成立者合併。
 */
class RuleResultCompletion private constructor(
    val outcomeId: String,
    val kind: RoundCompletionKind,
    val beneficiaryPlayerIds: Set<Uuid>,
) {
    init {
        NamespacedId.requireValid(outcomeId) { "Rule result outcome ID must be namespaced: $outcomeId" }
    }

    /** 事件系統內部建立摘要的入口。 */
    internal companion object {
        /**
         * 建立摘要並複製受益者集合。
         *
         * @param outcomeId 結算結果 ID。
         * @param kind 結算分類。
         * @param beneficiaryPlayerIds 摘要中的受益者。
         * @return 唯讀摘要。
         */
        @JvmSynthetic
        fun create(
            outcomeId: String,
            kind: RoundCompletionKind,
            beneficiaryPlayerIds: Set<Uuid>,
        ): RuleResultCompletion = RuleResultCompletion(
            outcomeId = outcomeId,
            kind = kind,
            beneficiaryPlayerIds = ReadOnlySetSnapshot(beneficiaryPlayerIds),
        )
    }
}
