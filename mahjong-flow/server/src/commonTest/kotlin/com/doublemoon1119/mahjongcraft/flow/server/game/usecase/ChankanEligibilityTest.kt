package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證搶槓資格只包含規則允許榮和這張槓牌的玩家。 */
class ChankanEligibilityTest {
    private val declarerId = Uuid.random()
    private val kokushiPlayerId = Uuid.random()
    private val singleWaitPlayerId = Uuid.random()
    private val module = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }.getModule(RiichiRuleConfig())
    private val declaredWhite = FakeIdentifiedTileFactory.create(Tile.Honor.White)

    /** 暗槓只有國士無雙可以搶；單騎聽同一張牌的一般手牌沒有資格，因此不會被記為放過。 */
    @Test
    fun `only a kokushi hand can rob a closed kan`() {
        val eligible = ronEligiblePlayerIds(GameAction.Kan(GameAction.KanType.CLOSED_KAN, declaredWhite.id, emptyList()))

        assertEquals(setOf(kokushiPlayerId), eligible)
    }

    /** 加槓時所有聽這張牌的玩家都有資格搶槓。 */
    @Test
    fun `every hand waiting on the tile can rob an added kan`() {
        val eligible = ronEligiblePlayerIds(GameAction.Kan(GameAction.KanType.ADDED_KAN, declaredWhite.id, emptyList()))

        assertEquals(setOf(kokushiPlayerId, singleWaitPlayerId), eligible)
    }

    private fun ronEligiblePlayerIds(kanAction: GameAction.Kan): Set<Uuid> {
        val table = FakeTableStateFactory.create(
            players = listOf(
                FakeMahjongPlayerFactory.create(
                    id = declarerId,
                    initialSeat = Wind.EAST,
                    hand = Hand(tiles = List(3) { FakeIdentifiedTileFactory.create(Tile.Honor.White) }, lastDrawn = declaredWhite),
                ),
                FakeMahjongPlayerFactory.create(
                    id = kokushiPlayerId,
                    initialSeat = Wind.SOUTH,
                    hand = Hand(tiles = kokushiWaitingOnWhite().map(FakeIdentifiedTileFactory::create)),
                    playerRuleState = RiichiPlayerState(),
                ),
                FakeMahjongPlayerFactory.create(
                    id = singleWaitPlayerId,
                    initialSeat = Wind.WEST,
                    hand = Hand(tiles = singleWaitOnWhite().map(FakeIdentifiedTileFactory::create)),
                    playerRuleState = RiichiPlayerState(),
                ),
            ),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        return ChankanEligibility.ronEligiblePlayerIds(
            tableState = table,
            declarerId = declarerId,
            kanAction = kanAction,
            robbedTile = declaredWhite,
            module = module,
        )
    }

    /** 國士無雙，中為雀頭，只差白。 */
    private fun kokushiWaitingOnWhite() = listOf(
        Tile.Numeric(Tile.Suit.Character, 1),
        Tile.Numeric(Tile.Suit.Character, 9),
        Tile.Numeric(Tile.Suit.Dot, 1),
        Tile.Numeric(Tile.Suit.Dot, 9),
        Tile.Numeric(Tile.Suit.Bamboo, 1),
        Tile.Numeric(Tile.Suit.Bamboo, 9),
        Tile.Honor.East,
        Tile.Honor.South,
        Tile.Honor.West,
        Tile.Honor.North,
        Tile.Honor.Green,
        Tile.Honor.Red,
        Tile.Honor.Red,
    )

    /** 役牌發成立、單騎聽白的一般手牌。 */
    private fun singleWaitOnWhite() = listOf(
        Tile.Honor.Green,
        Tile.Honor.Green,
        Tile.Honor.Green,
        Tile.Numeric(Tile.Suit.Character, 2),
        Tile.Numeric(Tile.Suit.Character, 3),
        Tile.Numeric(Tile.Suit.Character, 4),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Dot, 6),
        Tile.Numeric(Tile.Suit.Dot, 7),
        Tile.Numeric(Tile.Suit.Bamboo, 6),
        Tile.Numeric(Tile.Suit.Bamboo, 7),
        Tile.Numeric(Tile.Suit.Bamboo, 8),
        Tile.Honor.White,
    )
}
