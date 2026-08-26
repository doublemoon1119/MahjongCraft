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

/** 一位贏家的權威胡牌詳情快照。 */
data class WinSettlementWinnerPresentation(
    val playerId: Uuid,
    val seatIndex: Int,
    val responsiblePlayerId: Uuid?,
    val totalScore: Int,
    val handTileIds: List<Uuid>,
    val melds: List<MeldPresentation>,
    val winningTileId: Uuid?,
    val detailFields: List<WinSettlementDetailField>,
) {
    init {
        require(detailFields.map(WinSettlementDetailField::id).distinct().size == detailFields.size)
    }
}

/** 胡牌演出之後依序顯示贏家詳情、最後顯示共用排行的 request。 */
data class WinSettlementPresentationRequest(
    val outcomeId: String,
    val templateKey: String,
    val isTsumo: Boolean,
    val winners: List<WinSettlementWinnerPresentation>,
    val ranking: ScoreRankingPresentation,
) {
    init {
        require(winners.isNotEmpty())
        require(outcomeId.substringBefore(':', "").isNotBlank() && outcomeId.substringAfter(':', "").isNotBlank())
        require(templateKey.substringBefore(':', "").isNotBlank() && templateKey.substringAfter(':', "").isNotBlank())
    }
}
