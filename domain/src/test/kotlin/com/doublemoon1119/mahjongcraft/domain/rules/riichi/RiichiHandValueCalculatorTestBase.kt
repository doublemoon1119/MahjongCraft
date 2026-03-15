package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.domain.base.Meld
import com.doublemoon1119.mahjongcraft.domain.base.MeldType
import com.doublemoon1119.mahjongcraft.domain.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiYakuContext
import com.doublemoon1119.mahjongcraft.domain.table.Wind
import java.util.UUID

/**
 * 立直麻將手牌番數計算機測試基底類別。
 *
 * @see RiichiHandValueCalculator
 */
abstract class RiichiHandValueCalculatorTestBase {

    protected val calculator = RiichiHandValueCalculator()

    protected fun createHand(tiles: List<Tile>, hasExposedMelds: Boolean = false): Hand {
        val identifiedTiles = tiles.map { IdentifiedTile(UUID.randomUUID(), it) }
        val melds = if (hasExposedMelds) {
            mutableListOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        } else {
            mutableListOf()
        }
        return Hand(identifiedTiles.toMutableList(), melds = melds)
    }

    protected fun createContext(
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
        revealedExposedKans: List<Tile> = emptyList(),
        roundWind: Wind = Wind.EAST,
        seatWind: Wind = Wind.EAST,
        isLastDraw: Boolean = false,
        isLastDiscard: Boolean = false,
        isRobbingKan: Boolean = false,
        isRinshanKaihou: Boolean = false
    ): RiichiYakuContext {
        return RiichiYakuContext(
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
            revealedExposedKans = revealedExposedKans,
            roundWind = roundWind,
            seatWind = seatWind,
            isLastDraw = isLastDraw,
            isLastDiscard = isLastDiscard,
            isRobbingKan = isRobbingKan,
            isRinshanKaihou = isRinshanKaihou
        )
    }
}
