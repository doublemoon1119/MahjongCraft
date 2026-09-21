package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.AutomaticDecisionEvaluator
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContextResolver
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [GetPlayerDecisionOptionsUseCase] 的完整規則查詢邊界測試。 */
class GetPlayerDecisionOptionsUseCaseTest {
    /** 每個測試使用的權威對局 Uuid。 */
    private val gameId = Uuid.random()

    /** 建立包含內建規則與記憶體 repository 的測試環境。 */
    private class Fixtures {
        /** 測試用權威對局 repository。 */
        val gameRepository = FakeGameRepository()

        /** 已註冊內建規則的 module registry。 */
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }

        /** 待測的完整決策查詢 use case。 */
        val useCase = GetPlayerDecisionOptionsUseCase(
            gameRepository,
            AutomaticDecisionEvaluator(moduleRegistry, PlayerActionContextResolver()),
        )
    }

    /** 驗證自己回合一次取得立直選牌需求、一般分析、動作分析與摸牌參考牌。 */
    @Test
    fun `test own turn returns complete riichi decision options`() = runTest {
        val fixtures = Fixtures()
        val characterTiles = listOf(1, 2, 3, 4, 5, 6).map {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, it))
        }
        val bambooTiles = listOf(7, 8, 9).map {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, it))
        }
        val dotTiles = listOf(1, 2, 3).map {
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, it))
        }
        val east = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val north = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val playerId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = characterTiles + bambooTiles + dotTiles + east, lastDrawn = north),
            discardPile = RiichiDiscardPile(),
            playerRuleState = RiichiPlayerState(),
        ).copy(score = 25000)
        fixtures.gameRepository.setTableState(
            FakeTableStateFactory.create(
                id = gameId,
                players = listOf(player),
                config = RiichiRuleConfig(),
                currentPlayerIndex = 0,
                tileWall = TileWall(List(3) { FakeIdentifiedTileFactory.create(Tile.Honor.East) }),
                dynamicRuleState = RiichiDynamicState(),
            ),
        )

        val result = fixtures.useCase(gameId, playerId)

        assertTrue(result is Outcome.Success)
        val options = result.value
        val riichi = assertNotNull(options.actions.firstOrNull { it.action == RIICHI_GAME_ACTION })
        val requirement = assertNotNull(riichi.tileSelectionRequirement)
        assertTrue(requirement.eligibleTileIds.isNotEmpty())
        assertTrue(riichi.discardAnalyses.isNotEmpty())
        assertTrue(riichi.discardAnalyses.all { it.discardTileId in requirement.eligibleTileIds })
        assertTrue(options.discardAnalyses.isNotEmpty())
        assertEquals(north.tile, options.referenceTile)
    }

    /** 驗證捨牌反應只回傳反應動作與參考牌，不產生自己回合的捨牌分析。 */
    @Test
    fun `test discard reaction returns actions without own turn analyses`() = runTest {
        val fixtures = Fixtures()
        val discarderId = Uuid.random()
        val respondentId = Uuid.random()
        val discarded = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discarded),
        )
        val matchingTiles = List(2) { FakeIdentifiedTileFactory.create(Tile.Honor.South) }
        val respondent = FakeMahjongPlayerFactory.create(
            id = respondentId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = matchingTiles),
            playerRuleState = RiichiPlayerState(),
        )
        fixtures.gameRepository.setTableState(
            FakeTableStateFactory.create(
                id = gameId,
                players = listOf(discarder, respondent),
                config = RiichiRuleConfig(),
                pendingReaction = PendingReaction(discarderId, discarded.id, setOf(respondentId)),
            ),
        )

        val result = fixtures.useCase(gameId, respondentId)

        assertTrue(result is Outcome.Success)
        assertEquals(
            listOf(GameAction.Pon(discarded.id, matchingTiles.map { it.id }), GameAction.Pass),
            result.value.actions.map { it.action },
        )
        assertTrue(result.value.discardAnalyses.isEmpty())
        assertTrue(result.value.actions.all { it.discardAnalyses.isEmpty() })
        assertEquals(discarded.tile, result.value.referenceTile)
    }

    /** 驗證不吃碰槓由同一查詢邊界隱藏鳴牌選項，而不是交由平台自行過濾。 */
    @Test
    fun `test decline calls filters call actions from decision options`() = runTest {
        val fixtures = Fixtures()
        val discarded = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val discarder = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discarded),
        )
        val matchingTiles = List(2) { FakeIdentifiedTileFactory.create(Tile.Honor.South) }
        val respondent = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = matchingTiles),
            playerRuleState = RiichiPlayerState(),
        )
        val state = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, respondent),
            config = RiichiRuleConfig(),
            pendingReaction = PendingReaction(discarder.id, discarded.id, setOf(respondent.id)),
        )
        fixtures.gameRepository.setGame(
            Game(
                tableState = state,
                flowConfig = GameFlowConfig(),
                enabledAutomaticControlIdsByPlayerId = mapOf(
                    respondent.id to setOf(BuiltInAutomaticControlIds.DECLINE_CALLS),
                ),
            ),
        )

        val result = fixtures.useCase(gameId, respondent.id)

        assertTrue(result is Outcome.Success)
        assertEquals(listOf(GameAction.Pass), result.value.actions.map { it.action })
    }

    /** 驗證未提供捨牌分析器的規則仍可安全回傳決策結果。 */
    @Test
    fun `test rule without analyzer returns empty analyses`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val drawn = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val player = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = drawn),
        )
        fixtures.gameRepository.setTableState(
            FakeTableStateFactory.create(
                id = gameId,
                players = listOf(player),
                config = TaiwanRuleConfig(),
                currentPlayerIndex = 0,
            ),
        )

        val result = fixtures.useCase(gameId, playerId)

        assertTrue(result is Outcome.Success)
        assertTrue(result.value.discardAnalyses.isEmpty())
        assertTrue(result.value.actions.all { it.discardAnalyses.isEmpty() })
        assertEquals(drawn.tile, result.value.referenceTile)
    }

    /** 驗證對局不存在時回傳既有的查詢錯誤。 */
    @Test
    fun `test missing game returns game not found`() = runTest {
        val fixtures = Fixtures()

        val result = fixtures.useCase(gameId, Uuid.random())

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.GameNotFound(gameId), result.error)
    }

    /** 驗證玩家不屬於指定對局時回傳既有的玩家錯誤。 */
    @Test
    fun `test missing player returns player not in game`() = runTest {
        val fixtures = Fixtures()
        val player = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val missingPlayerId = Uuid.random()
        fixtures.gameRepository.setTableState(
            FakeTableStateFactory.create(id = gameId, players = listOf(player), config = RiichiRuleConfig()),
        )

        val result = fixtures.useCase(gameId, missingPlayerId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.PlayerNotInGame(missingPlayerId, gameId), result.error)
    }
}
