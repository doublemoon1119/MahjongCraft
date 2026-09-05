package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.flow.common.game.service.MeldPresentation
import kotlin.uuid.Uuid

/** 平台無關、可序列化的胡牌詳情欄位。 */
sealed interface WinSettlementDetailValue {
    data class Text(val translationKey: String, val arguments: List<String> = emptyList()) : WinSettlementDetailValue
    data class Tiles(val tileIds: List<Uuid>) : WinSettlementDetailValue
    data class Entries(val entries: List<Entry>) : WinSettlementDetailValue {
        /**
         * 可逐項揭曉的條目。右側可選擇顯示既有純文字，或以 [trailingTranslationKey] 顯示本地化
         * 等級；兩者不得同時提供。
         */
        data class Entry(
            val translationKey: String,
            val trailingText: String = "",
            val trailingTranslationKey: String? = null,
            val trailingTranslationArgument: String? = null,
        ) {
            init {
                require(trailingText.isBlank() || trailingTranslationKey == null)
                require(trailingTranslationArgument == null || trailingTranslationKey != null)
            }
        }
    }
}

/** 不使用字串 Map 的單一規則擴充欄位。 */
data class WinSettlementDetailField(val id: String, val value: WinSettlementDetailValue) {
    init {
        require(id.substringBefore(':', "").isNotBlank() && id.substringAfter(':', "").isNotBlank())
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
 * @property standingTileIds 立牌 ID，**不含**副露牌——副露牌另外完整列在 [melds]，兩者不得重複，
 * 否則 renderer 會把同一組副露多畫一次在手牌裡。
 * @property melds 已公開的副露。
 * @property winningTileId 胡牌張；特殊 outcome（同上）不偽造胡牌張時為 `null`。
 * @property detailFields 規則專屬的翻符、役種等呈現詳情，欄位 id 不得重複。
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
 * @property isBrief 這次是否**跳過贏家詳情、只顯示分數變動**。
 *
 * 中途胡牌（本局在胡牌後仍繼續）用的模式：贏家的牌已經在牌桌上攤開了，面板再重現一次手牌、胡牌張、
 * 寶牌與役種明細只是讓其他仍在局中的玩家乾等，而「誰放銃給誰」從分數增減本來就看得出來。因此平台
 * 直接不建立贏家段，面板一開場就是分數變動動畫。
 *
 * 刻意做成與 [templateKey] **正交**的旗標：[templateKey] 表達的是「這是哪一套規則的面板」，跳不跳過
 * 贏家段是另一條軸，而且跳過之後那個 key 根本用不到。
 */
data class WinSettlementPresentationRequest(
    val outcomeId: String,
    val templateKey: String,
    val isTsumo: Boolean,
    val winners: List<WinSettlementWinnerPresentation>,
    val ranking: ScoreRankingPresentation,
    val isBrief: Boolean = false,
) {
    init {
        require(winners.isNotEmpty())
        require(outcomeId.substringBefore(':', "").isNotBlank() && outcomeId.substringAfter(':', "").isNotBlank())
        require(templateKey.substringBefore(':', "").isNotBlank() && templateKey.substringAfter(':', "").isNotBlank())
    }
}
