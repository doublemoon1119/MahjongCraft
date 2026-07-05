package com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueContext
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 用於單元測試的立直麻將番數計算上下文工廠。
 *
 * 負責產生 [RiichiHandValueContext] 實體，並為大量參數提供預設的測試值。
 */
object FakeRiichiHandValueContextFactory {

    /**
     * 建立一個測試用的番數計算上下文。
     *
     * @param hand 手牌實體。
     * @param winningTile 和牌的那張牌。
     * @param isTsumo 是否為自摸。
     * @param isRiichi 是否立直，預設為 false。
     * @param isIppatsu 是否一發，預設為 false。
     * @param isDoubleRiichi 是否雙立直，預設為 false。
     * @param isMenzen 是否門前清，預設為 true。
     * @param allowOpenTanyao 是否允許食斷，預設為 true。
     * @param doraIndicators 寶牌指示牌列表，預設為空。
     * @param uraDoraIndicators 裡寶牌指示牌列表，預設為空。
     * @param roundWind 場風，預設為東風。
     * @param seatWind 自風，預設為東風。
     * @param isLastDraw 是否為海底摸月，預設為 false。
     * @param isLastDiscard 是否為河底撈魚，預設為 false。
     * @param isRobbingKan 是否為槍槓，預設為 false。
     * @param isRinshanKaihou 是否為嶺上開花，預設為 false。
     * @param isFirstTurn 是否為第一巡（用於判斷地和等），預設為 false。
     */
    fun create(
        hand: Hand,
        winningTile: Tile,
        isTsumo: Boolean,
        isRiichi: Boolean = false,
        isIppatsu: Boolean = false,
        isDoubleRiichi: Boolean = false,
        isMenzen: Boolean = true,
        allowOpenTanyao: Boolean = true,
        doraIndicators: List<Tile> = emptyList(),
        uraDoraIndicators: List<Tile> = emptyList(),
        roundWind: Wind = Wind.EAST,
        seatWind: Wind = Wind.EAST,
        isLastDraw: Boolean = false,
        isLastDiscard: Boolean = false,
        isRobbingKan: Boolean = false,
        isRinshanKaihou: Boolean = false,
        isFirstTurn: Boolean = false
    ): RiichiHandValueContext {
        return RiichiHandValueContext(
            hand = hand,
            winningTile = winningTile,
            isTsumo = isTsumo,
            isRiichi = isRiichi,
            isIppatsu = isIppatsu,
            isDoubleRiichi = isDoubleRiichi,
            isMenzen = isMenzen,
            allowOpenTanyao = allowOpenTanyao,
            doraIndicators = doraIndicators,
            uraDoraIndicators = uraDoraIndicators,
            roundWind = roundWind,
            seatWind = seatWind,
            isLastDraw = isLastDraw,
            isLastDiscard = isLastDiscard,
            isRobbingKan = isRobbingKan,
            isRinshanKaihou = isRinshanKaihou,
            isFirstTurn = isFirstTurn
        )
    }
}