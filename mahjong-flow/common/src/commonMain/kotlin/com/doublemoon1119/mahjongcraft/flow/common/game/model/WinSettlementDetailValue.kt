package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView

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
     * 直接描述的公開牌面，不引用牌山或任何玩家持有的特定牌張。
     *
     * 牌面已經以公共讀取者裁切，只帶允許展示的外觀與特徵，例如日麻的赤五。
     *
     * @property faces 依規則順序排列的展示牌面，保留重複值。
     */
    data class TileFaces(val faces: List<TileFaceView>) : WinSettlementDetailValue
}
