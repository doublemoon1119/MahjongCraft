package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import kotlin.uuid.Uuid

/** MahjongCraft 內建特殊 round outcome 的完整識別碼。 */
object BuiltInRoundOutcomeIds {
    /** 一般自摸。 */
    val TSUMO: String = MahjongCraftMetadata.id("tsumo")

    /** 一般榮和（含搶槓）。 */
    val RON: String = MahjongCraftMetadata.id("ron")

    /** 日麻流局滿貫。 */
    val NAGASHI_MANGAN: String = MahjongCraftMetadata.id("nagashi_mangan")
}

/** 第三方 outcome 可攜帶、並由 extension DTO registry 負責序列化的強型別詳細資料。 */
interface ExtensionRoundOutcomeDetail

/** 特殊 round outcome 在後續呈現流程中的規則中立分類。 */
enum class RoundOutcomePresentationClassification {
    /** 外觀與胡牌結算等價，但不代表核心需要理解該規則的計分細節。 */
    WIN_EQUIVALENT,

    /** 外觀與流局結算等價。 */
    EXHAUSTIVE_DRAW_EQUIVALENT,
}

/** 本局結算後應採用的明確莊家推進決策。 */
enum class RoundTransitionDirective {
    /** 莊家連莊。 */
    REPEAT_DEALER,

    /** 莊家過莊。 */
    ADVANCE_DEALER,
}

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
        require(ID_PATTERN.matches(id)) { "Round outcome id must be a full namespaced id: $id" }
        val playerIds = settledTableState.players.mapTo(mutableSetOf()) { it.id }
        require(beneficiaryPlayerIds.all { it in playerIds }) { "Outcome beneficiaries must belong to the table" }
        require(responsiblePlayerIds.all { it in playerIds }) { "Outcome responsible players must belong to the table" }
        require(stickPotCollectorPlayerIds.all { it in playerIds }) { "Stick pot collectors must belong to the table" }
        require(scoreDeltas.keys == playerIds) { "Outcome score deltas must contain exactly the table players" }
    }

    private companion object {
        /** 接受 Minecraft 慣用 namespace 與 path 字元的完整識別碼格式。 */
        val ID_PATTERN = Regex("[a-z0-9_.-]+:[a-z0-9/._-]+")
    }
}
