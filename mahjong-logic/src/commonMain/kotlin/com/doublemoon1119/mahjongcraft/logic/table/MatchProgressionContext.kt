package com.doublemoon1119.mahjongcraft.logic.table

import kotlin.uuid.Uuid

/** Match progression policy 的純邏輯輸入。 */
data class MatchProgressionContext(
    /** 本局完成分數結算後、尚未開下一局的權威桌況。 */
    val tableState: TableState,
    /** 本局權威結果摘要。 */
    val completion: RoundCompletionSummary,
    /** 由規則的 Match comparator 產生、由第一名到末位的權威玩家順序。 */
    val rankedPlayerIds: List<Uuid>,
) {
    init {
        val playerIds = tableState.players.mapTo(mutableSetOf()) { it.id }
        require(rankedPlayerIds.size == playerIds.size && rankedPlayerIds.toSet() == playerIds) {
            "Match ranking must contain every table player exactly once"
        }
        require(completion.settledScoresByPlayerId.keys == playerIds) {
            "Round completion scores must contain exactly the table players"
        }
    }
}
