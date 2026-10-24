package com.doublemoon1119.mahjongcraft.flow.api.event

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 同一個和牌結果中的一組贏家與責任玩家。
 *
 * @property outcomeId 和牌結果的完整 namespaced ID。
 * @property winners 依規則提供順序排列的贏家詳情。
 * @property responsiblePlayerIds 規則認定負責此結果的玩家。
 * @property discarderPlayerIds 規則明確認定為放銃者的玩家；無法判定時為空集合。
 */
class RoundWinGroup private constructor(
    val outcomeId: String,
    val winners: List<RoundWinDetail>,
    val responsiblePlayerIds: Set<Uuid>,
    val discarderPlayerIds: Set<Uuid>,
) {
    init {
        NamespacedId.requireValid(outcomeId) { "Round win outcome ID must be namespaced: $outcomeId" }
    }

    /** 事件系統內部建立資料的入口。 */
    internal companion object {
        /**
         * 建立和牌結果群組，並複製所有集合。
         *
         * @param outcomeId 和牌結果的完整 namespaced ID。
         * @param winners 贏家詳情。
         * @param responsiblePlayerIds 負責此結果的玩家。
         * @param discarderPlayerIds 規則明確認定的放銃者。
         * @return 建立的和牌結果群組。
         */
        @JvmSynthetic
        fun create(
            outcomeId: String,
            winners: List<RoundWinDetail>,
            responsiblePlayerIds: Set<Uuid>,
            discarderPlayerIds: Set<Uuid>,
        ): RoundWinGroup = RoundWinGroup(
            outcomeId = outcomeId,
            winners = ReadOnlyListSnapshot(winners),
            responsiblePlayerIds = ReadOnlySetSnapshot(responsiblePlayerIds),
            discarderPlayerIds = ReadOnlySetSnapshot(discarderPlayerIds),
        )
    }
}
