package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.AutomaticDecisionEvaluator
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContextResolver
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** [AutomaticDecisionDriver] 的權威反應流程測試。 */
class AutomaticDecisionDriverTest {
    /** 二三四萬、四五六筒、七八九筒、中中中：補上二條對子即為聽二條的門前手牌，中提供役牌役。 */
    private val yakuhaiHandTiles: List<Tile> = listOf(
        Tile.Numeric(Tile.Suit.Character, 2),
        Tile.Numeric(Tile.Suit.Character, 3),
        Tile.Numeric(Tile.Suit.Character, 4),
        Tile.Numeric(Tile.Suit.Dot, 4),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Dot, 6),
        Tile.Numeric(Tile.Suit.Dot, 7),
        Tile.Numeric(Tile.Suit.Dot, 8),
        Tile.Numeric(Tile.Suit.Dot, 9),
        Tile.Honor.Red,
        Tile.Honor.Red,
        Tile.Honor.Red,
    )

    /** 啟用不吃碰槓且只剩鳴牌或放過時，立即經共用 mapper 提交放過。 */
    @Test
    fun `test decline calls maps automatic pass through reaction context`() = runTest {
        val repository = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val contextResolver = PlayerActionContextResolver()
        val evaluator = AutomaticDecisionEvaluator(moduleRegistry, contextResolver)
        val driver = AutomaticDecisionDriver(
            repository,
            evaluator,
            GameActionCommandMapper(ExtensionGameActionCommandFactoryRegistry()),
            contextResolver,
        )
        val discarded = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val discarder = FakeMahjongPlayerFactory.create(
            discardPile = FakeDiscardPile().discardTile(discarded),
        )
        val matchingTiles = List(2) { FakeIdentifiedTileFactory.create(Tile.Honor.South) }
        val respondent = FakeMahjongPlayerFactory.create(
            hand = Hand(tiles = matchingTiles),
            playerRuleState = RiichiPlayerState(),
        )
        val state = FakeTableStateFactory.create(
            players = listOf(discarder, respondent),
            config = RiichiRuleConfig(),
            pendingReaction = PendingReaction(discarder.id, discarded.id, setOf(respondent.id)),
        )
        repository.setGame(
            Game(
                tableState = state,
                flowConfig = GameFlowConfig(),
                enabledAutomaticControlIdsByPlayerId = mapOf(
                    respondent.id to setOf(BuiltInAutomaticControlIds.DECLINE_CALLS),
                ),
            ),
        )

        val result = driver.resolveNextAction(state.id)

        assertEquals(respondent.id to GameCommand.RespondToDiscard(GameAction.Pass), result)
    }

    /** 啟用自動和牌且他家打出和牌張時，立即提交榮和。 */
    @Test
    fun `test auto win submits ron on a winning discard`() = runTest {
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 2))
        val discarder = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(winningTile),
        )
        val winner = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = tenpaiOnTwoBamboo()),
            discardPile = FakeDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        val state = FakeTableStateFactory.create(
            players = listOf(discarder, winner),
            config = RiichiRuleConfig(),
            pendingReaction = PendingReaction(discarder.id, winningTile.id, setOf(winner.id)),
        )
        val driver = createDriver(
            state = state,
            enabledControlIds = mapOf(winner.id to setOf(BuiltInAutomaticControlIds.AUTO_WIN)),
        )

        assertEquals(
            winner.id to GameCommand.RespondToDiscard(GameAction.Ron(winningTile.id)),
            driver.resolveNextAction(state.id),
        )
    }

    /** 啟用自動和牌且自己剛摸到和牌張時，立即提交自摸。 */
    @Test
    fun `test auto win submits tsumo on a winning draw`() = runTest {
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 2))
        val winner = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand(tiles = tenpaiOnTwoBamboo(), lastDrawn = winningTile),
            discardPile = FakeDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        val opponent = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        val state = FakeTableStateFactory.create(
            players = listOf(winner, opponent),
            config = RiichiRuleConfig(),
        )
        val driver = createDriver(
            state = state,
            enabledControlIds = mapOf(winner.id to setOf(BuiltInAutomaticControlIds.AUTO_WIN)),
        )

        assertEquals(winner.id to GameCommand.Tsumo, driver.resolveNextAction(state.id))
    }

    /** 啟用自動摸切且剛摸到的牌沒有任何特殊選項時，立即打出那一張。 */
    @Test
    fun `test auto tsumogiri discards the drawn tile`() = runTest {
        val drawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5))
        val player = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand(tiles = farFromTenpai(), lastDrawn = drawn),
            playerRuleState = RiichiPlayerState(),
        )
        val opponent = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        val state = FakeTableStateFactory.create(players = listOf(player, opponent), config = RiichiRuleConfig())
        val driver = createDriver(
            state = state,
            enabledControlIds = mapOf(player.id to setOf(BuiltInAutomaticControlIds.AUTO_TSUMOGIRI)),
        )

        assertEquals(player.id to GameCommand.Discard(drawn.id), driver.resolveNextAction(state.id))
    }

    /** 剛摸到的牌可以宣告暗槓時，自動摸切必須讓玩家自己決定。 */
    @Test
    fun `test auto tsumogiri keeps a closed kan choice with the player`() = runTest {
        val kanTiles = List(3) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)) }
        val drawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5))
        val player = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand(tiles = farFromTenpai().dropLast(3) + kanTiles, lastDrawn = drawn),
            playerRuleState = RiichiPlayerState(),
        )
        val opponent = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        val state = FakeTableStateFactory.create(players = listOf(player, opponent), config = RiichiRuleConfig())
        val driver = createDriver(
            state = state,
            enabledControlIds = mapOf(player.id to setOf(BuiltInAutomaticControlIds.AUTO_TSUMOGIRI)),
        )

        assertNull(driver.resolveNextAction(state.id))
    }

    /** 只啟用不吃碰槓時，同一張牌仍可榮和，因此不得自動放過。 */
    @Test
    fun `test decline calls keeps a winning discard with the player`() = runTest {
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 2))
        val discarder = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(winningTile),
        )
        val winner = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = shanponOnTwoBamboo()),
            discardPile = FakeDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        val state = FakeTableStateFactory.create(
            players = listOf(discarder, winner),
            config = RiichiRuleConfig(),
            pendingReaction = PendingReaction(discarder.id, winningTile.id, setOf(winner.id)),
        )
        val driver = createDriver(
            state = state,
            enabledControlIds = mapOf(winner.id to setOf(BuiltInAutomaticControlIds.DECLINE_CALLS)),
        )

        assertNull(driver.resolveNextAction(state.id))
    }

    /** 單騎聽二條的門前手牌：中中中提供役牌役。 */
    private fun tenpaiOnTwoBamboo(): List<IdentifiedTile> = (yakuhaiHandTiles + Tile.Numeric(Tile.Suit.Bamboo, 2)).map(FakeIdentifiedTileFactory::create)

    /** 雙碰聽二、三條的門前手牌：中中中提供役牌役，二條同時可碰。 */
    private fun shanponOnTwoBamboo(): List<IdentifiedTile> = (
        yakuhaiHandTiles.drop(3) +
            List(2) { Tile.Numeric(Tile.Suit.Bamboo, 2) } +
            List(2) { Tile.Numeric(Tile.Suit.Bamboo, 3) }
        ).map(FakeIdentifiedTileFactory::create)

    /** 沒有對子、沒有聽牌、也沒有可槓牌種的手牌。 */
    private fun farFromTenpai(): List<IdentifiedTile> = listOf(
        Tile.Numeric(Tile.Suit.Character, 2),
        Tile.Numeric(Tile.Suit.Character, 3),
        Tile.Numeric(Tile.Suit.Character, 4),
        Tile.Numeric(Tile.Suit.Character, 5),
        Tile.Numeric(Tile.Suit.Character, 6),
        Tile.Numeric(Tile.Suit.Character, 7),
        Tile.Numeric(Tile.Suit.Dot, 2),
        Tile.Numeric(Tile.Suit.Dot, 3),
        Tile.Numeric(Tile.Suit.Dot, 4),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Dot, 6),
        Tile.Numeric(Tile.Suit.Bamboo, 2),
        Tile.Numeric(Tile.Suit.Bamboo, 3),
    ).map(FakeIdentifiedTileFactory::create)

    /** 以這一桌的權威狀態與啟用控制建立 driver。 */
    private suspend fun createDriver(
        state: TableState,
        enabledControlIds: Map<Uuid, Set<String>>,
    ): AutomaticDecisionDriver {
        val repository = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val contextResolver = PlayerActionContextResolver()
        repository.setGame(
            Game(
                tableState = state,
                flowConfig = GameFlowConfig(),
                enabledAutomaticControlIdsByPlayerId = enabledControlIds,
            ),
        )
        return AutomaticDecisionDriver(
            repository,
            AutomaticDecisionEvaluator(moduleRegistry, contextResolver),
            GameActionCommandMapper(ExtensionGameActionCommandFactoryRegistry()),
            contextResolver,
        )
    }
}
