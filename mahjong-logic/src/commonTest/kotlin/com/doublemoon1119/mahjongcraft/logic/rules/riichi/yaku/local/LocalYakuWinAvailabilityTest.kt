package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.local

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueCalculator
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueContextCalculator
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiLegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiShantenCalculator
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證古役在實際桌況下讓原本無役的手牌可以榮和。 */
class LocalYakuWinAvailabilityTest {
    /** 只有燕返一個役時，啟用古役才能以立直宣言牌榮和。 */
    @Test
    fun `tsubame gaeshi alone allows a ron only when local yaku are enabled`() {
        val declared = FakeIdentifiedTileFactory.create(m(8))
        val winner = FakeMahjongPlayerFactory.create(
            hand = FakeHandFactory.create(listOf(m(1), m(2), m(3), p(4), p(5), p(6), s(7), s(8), s(9), s(2), s(2), m(7), m(9))),
            playerRuleState = RiichiPlayerState(),
        )
        val discarder = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            discardPile = RiichiDiscardPile().discard(RiichiDiscardEntry(declared, isRiichi = true)),
            playerRuleState = RiichiPlayerState(),
        ).recordAction(GameAction.Draw).recordAction(RIICHI_GAME_ACTION).recordAction(GameAction.Discard(declared.id))
        val tableState = FakeTableStateFactory.create(
            players = listOf(winner, discarder),
            tileWall = TileWall(List(20) { FakeIdentifiedTileFactory.create(Tile.Honor.Red) }),
            config = RiichiRuleConfig(),
        )

        fun canRon(useLocalYaku: Boolean): Boolean {
            val config = RiichiRuleConfig(useLocalYaku = useLocalYaku)
            val validator = RiichiLegalActionValidator(
                shantenCalculator = RiichiShantenCalculator(),
                handValueCalculator = RiichiHandValueCalculator(useLocalYaku = useLocalYaku),
                contextCalculator = RiichiHandValueContextCalculator(config),
            )
            return validator.getLegalActions(
                tableState = tableState,
                player = winner,
                sourceAction = GameAction.Discard(declared.id),
                sourceDirection = RelativeDirection.Right,
                incomingTile = declared,
            ).any { it is GameAction.Ron && it.tileId == declared.id }
        }

        assertTrue(canRon(useLocalYaku = true))
        assertFalse(canRon(useLocalYaku = false))
    }

    /** 萬子。 */
    private fun m(value: Int): Tile = Tile.Numeric(Tile.Suit.Character, value)

    /** 筒子。 */
    private fun p(value: Int): Tile = Tile.Numeric(Tile.Suit.Dot, value)

    /** 索子。 */
    private fun s(value: Int): Tile = Tile.Numeric(Tile.Suit.Bamboo, value)
}
