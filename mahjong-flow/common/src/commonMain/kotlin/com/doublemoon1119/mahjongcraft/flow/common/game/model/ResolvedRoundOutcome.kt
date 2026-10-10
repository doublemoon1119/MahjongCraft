package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 規則 resolver 產生的強型別特殊本局結果。
 *
 * [settledTableState] 是 resolver 以純函式算出的完整權威結果；Flow 只負責原子寫回，不會再次計分。
 * [scoreDeltas] 則保留可供呈現與診斷使用的明確差額，並在建立時驗證與桌況前後一致。
 */
data class ResolvedRoundOutcome(
    val id: String,
    val settledTableState: TableState,
    val beneficiaryPlayerIds: Set<Uuid>,
    val responsiblePlayerIds: Set<Uuid> = emptySet(),
    val scoreDeltas: Map<Uuid, Int>,
    val stickPotCollectorPlayerIds: Set<Uuid> = emptySet(),
    val transitionDirective: RoundTransitionDirective,
    val presentationClassification: RoundOutcomePresentationClassification,
    val extensionDetail: ExtensionRoundOutcomeDetail? = null,
) {
    init {
        NamespacedId.requireValid(id) { "Round outcome id must be a full namespaced id: $id" }
        val playerIds = settledTableState.players.mapTo(mutableSetOf()) { it.id }
        require(beneficiaryPlayerIds.all { it in playerIds }) { "Outcome beneficiaries must belong to the table" }
        require(responsiblePlayerIds.all { it in playerIds }) { "Outcome responsible players must belong to the table" }
        require(stickPotCollectorPlayerIds.all { it in playerIds }) { "Stick pot collectors must belong to the table" }
        require(scoreDeltas.keys == playerIds) { "Outcome score deltas must contain exactly the table players" }
    }
}
