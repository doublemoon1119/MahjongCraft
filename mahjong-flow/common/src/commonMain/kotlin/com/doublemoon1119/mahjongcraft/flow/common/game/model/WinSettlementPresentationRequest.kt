package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.flow.common.game.service.MeldPresentation
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
 * 有單位的數值。
 *
 * @property unitId 規則定義的 namespaced 單位 ID，例如日麻的翻、符或役滿倍數。
 * @property amount 數值。
 */
data class WinSettlementQuantity(val unitId: String, val amount: Int) {
    init {
        NamespacedId.requireValid(unitId) { "Win settlement quantity unit ID must be namespaced: $unitId" }
    }
}

/**
 * 胡牌詳情的一個條目。
 *
 * @property id 規則定義的 namespaced 條目 ID，例如日麻的役種。
 * @property quantity 條目附帶的數值；沒有數值的條目為 null。
 */
data class WinSettlementDetailEntry(val id: String, val quantity: WinSettlementQuantity? = null) {
    init {
        NamespacedId.requireValid(id) { "Win settlement detail entry ID must be namespaced: $id" }
    }
}

/** 不使用字串 Map 的單一規則擴充欄位。 */
data class WinSettlementDetailField(val id: String, val value: WinSettlementDetailValue) {
    init {
        NamespacedId.requireValid(id) { "Win settlement detail field ID must be namespaced: $id" }
    }
}

/**
 * 一位贏家的權威胡牌詳情快照。
 *
 * @property playerId 贏家玩家 ID。
 * @property seatIndex 贏家座位序。
 * @property responsiblePlayerId 放銃／被搶槓玩家；自摸或不歸咎特定玩家的特殊 outcome（見
 * [WinSettlementPresentationRequestFactory.createSpecialOutcome]）為 `null`。
 * @property totalScore 這位贏家本次胡牌獲得的總點數。
 * @property standingTileIds 立牌 ID，依規則的牌序排列，**不含**副露牌——副露牌另外完整列在 [melds]，兩者不得重複，
 * 否則 renderer 會把同一組副露多畫一次在手牌裡。
 * @property melds 已公開的副露。
 * @property winningTileId 胡牌張；特殊 outcome（同上）不偽造胡牌張時為 `null`。
 * @property detailFields 規則專屬的胡牌詳情（例如日麻的翻符、役種），欄位 id 不得重複。
 */
data class WinSettlementWinnerPresentation(
    val playerId: Uuid,
    val seatIndex: Int,
    val responsiblePlayerId: Uuid?,
    val totalScore: Int,
    val standingTileIds: List<Uuid>,
    val melds: List<MeldPresentation>,
    val winningTileId: Uuid?,
    val detailFields: List<WinSettlementDetailField>,
) {
    init {
        require(detailFields.map(WinSettlementDetailField::id).distinct().size == detailFields.size)
    }
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
 * @property isBrief 這次是否**跳過贏家詳情、只顯示分數變動**。
 *
 * 中途胡牌（本局在胡牌後仍繼續）用的模式：贏家的牌已經在牌桌上攤開了，面板再重現一次手牌、胡牌張、
 * 寶牌與役種明細只是讓其他仍在局中的玩家乾等，而「誰放銃給誰」從分數增減本來就看得出來。因此平台
 * 直接不建立贏家段，面板一開場就是分數變動動畫。
 */
data class WinSettlementPresentationRequest(
    val outcomeId: String,
    val ruleModuleId: String,
    val isTsumo: Boolean,
    val winners: List<WinSettlementWinnerPresentation>,
    val ranking: ScoreRankingPresentation,
    val isBrief: Boolean = false,
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
