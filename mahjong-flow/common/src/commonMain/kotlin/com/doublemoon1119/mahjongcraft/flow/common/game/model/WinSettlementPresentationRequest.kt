package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.module.WinSettlementResult
import kotlin.uuid.Uuid

/**
 * 規則算出的一個胡牌詳情值；只描述結果本身，顯示文字與排版由平台依欄位與條目 ID 決定。
 */
sealed interface WinSettlementDetailValue {
    /**
     * 一組有單位的數值，例如日麻的「2 翻、30 符」或「兩倍役滿」。
     *
     * @property quantities 依規則順序排列的數值。
     */
    data class Quantities(val quantities: List<WinSettlementQuantity>) : WinSettlementDetailValue {
        init {
            require(quantities.isNotEmpty()) { "Win settlement quantities must not be empty" }
        }
    }

    /**
     * 依規則順序排列的條目，例如日麻的役種與各自的翻數。
     *
     * @property entries 條目清單。
     */
    data class Entries(val entries: List<WinSettlementDetailEntry>) : WinSettlementDetailValue

    /**
     * 局內牌，例如日麻的寶牌指示牌。
     *
     * @property tileIds 依規則順序排列的牌 ID。
     */
    data class Tiles(val tileIds: List<Uuid>) : WinSettlementDetailValue
}

/**
 * 胡牌演出之後依序顯示贏家詳情、最後顯示共用排行的 request。
 *
 * @property outcomeId 胡牌結算原因 ID。
 * @property ruleModuleId 本局使用的規則模組 ID；平台依此選擇呈現方式。
 * @property isTsumo 是否自摸。
 * @property winners 各贏家的胡牌詳情。
 * @property ranking 結算前後的分數排行。
 * @property paymentReasonIdsByPlayerId 排行中付款方式有別於一般結算的玩家，對應規則提供的付款原因 ID
 * （見 [WinSettlementResult.paymentReasonIdsByPlayerId]）；呈現層依 ID 查出顯示文字，不認識任何規則。
 * key 必須是 [ranking] 中的玩家。
 * @property includesWinnerDetails 是否提供贏家詳情；為 false 時只提供分數變動，例如中途胡牌時贏家的牌已經公開在桌上
 * （見 [ContinuingWinSettlementDetail]）。
 */
data class WinSettlementPresentationRequest(
    val outcomeId: String,
    val ruleModuleId: String,
    val isTsumo: Boolean,
    val winners: List<WinSettlementWinnerPresentation>,
    val ranking: ScoreRankingPresentation,
    val includesWinnerDetails: Boolean = true,
    val paymentReasonIdsByPlayerId: Map<Uuid, String> = emptyMap(),
) {
    init {
        require(winners.isNotEmpty())
        require(paymentReasonIdsByPlayerId.keys.all { playerId -> ranking.players.any { it.playerId == playerId } })
        require(paymentReasonIdsByPlayerId.values.all(NamespacedId::isValid)) { "Payment reason IDs must be namespaced" }
        NamespacedId.requireValid(outcomeId) { "Win settlement outcome ID must be namespaced: $outcomeId" }
        NamespacedId.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
    }
}
