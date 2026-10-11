package com.doublemoon1119.mahjongcraft.flow.common.game.model

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
