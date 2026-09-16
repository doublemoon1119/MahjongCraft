package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoLiability
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoYaku
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證包牌碰牌情境停在「碰了就成立包牌」的前一刻，且後續摸牌能讓呼叫者和牌。 */
class RiichiBeforePaoPonScenarioTest {
    private val config = RiichiRuleConfig()
    private val module = RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, config)

    /** 情境停在上家剛打出白、只有呼叫者能回應的反應視窗。 */
    @Test
    fun `waits for the invoking player to answer the discarded white`() {
        val (state, invoking) = buildScenario(invokingSeat = 1)
        val discarder = state.players[0]
        val pending = checkNotNull(state.pendingReaction)

        assertEquals(discarder.id, pending.discarderId)
        assertEquals(setOf(invoking.id), pending.eligiblePlayerIds)
        assertEquals(Tile.Honor.White, discarder.discardPile.entries.single().tile.tile)
        assertEquals(pending.tileId, discarder.discardPile.entries.single().tile.id)
    }

    /** 呼叫者可以合法碰上家的白。 */
    @Test
    fun `lets the invoking player pon the white`() {
        val (state, invoking) = buildScenario(invokingSeat = 2)
        val discarder = state.players[1]
        val calledTile = discarder.discardPile.entries.single().tile

        val actions = module.createLegalActionValidator().getLegalActions(
            tableState = state,
            player = invoking,
            sourceAction = GameAction.Discard(calledTile.id),
            sourceDirection = state.relativeDirectionOf(invoking.id, discarder.id),
            incomingTile = calledTile,
        )

        assertTrue(actions.any { it is GameAction.Pon }, "Expected a pon among $actions.")
    }

    /** 呼叫者已碰出發、中兩組，碰白即為第 3 組三元牌副露，責任歸上家。 */
    @Test
    fun `makes the discarder liable once the white is ponned`() {
        val (state, invoking) = buildScenario(invokingSeat = 0)
        val discarder = state.players[3]
        val calledTile = discarder.discardPile.entries.single().tile
        val direction = state.relativeDirectionOf(invoking.id, discarder.id)

        val claimed = module.beforeDiscardClaimed(invoking, MeldType.PON, calledTile, direction)

        assertEquals(
            listOf(Tile.Honor.Green, Tile.Honor.Red),
            invoking.hand.exposedMelds.map { meld -> meld.tiles.map { it.tile }.distinct().single() },
        )
        assertEquals(PaoLiability(PaoYaku.Daisangen, direction), (claimed.playerRuleState as RiichiPlayerState).paoLiability)
    }

    /** 活牌最前端交錯排著四萬與一萬，呼叫者輪到時第一張就是和牌張。 */
    @Test
    fun `puts the winning tiles at the front of the live wall`() {
        val (state, _) = buildScenario(invokingSeat = 0)

        assertEquals(
            listOf(4, 1, 4, 1, 4, 1).map { Tile.Numeric(Tile.Suit.Character, it) },
            state.tileWall.getAllTiles().take(6).map { it.tile },
        )
    }

    /** 其他三家無法碰呼叫者打出的北，也無法碰吃和牌張。 */
    @Test
    fun `keeps the opponents from claiming the north or the winning tiles`() {
        val (state, invoking) = buildScenario(invokingSeat = 0)
        val guarded = listOf(Tile.Honor.North, Tile.Numeric(Tile.Suit.Character, 1), Tile.Numeric(Tile.Suit.Character, 4))

        state.players.filter { it.id != invoking.id }.forEach { opponent ->
            val tiles = opponent.hand.standingTiles.map { it.tile }
            guarded.forEach { tile ->
                assertTrue(tiles.count { it == tile } <= 1, "Expected an opponent to hold at most one $tile.")
            }
            assertTrue(Tile.Numeric(Tile.Suit.Character, 2) !in tiles, "Expected an opponent not to hold 2m for a chi.")
            assertTrue(Tile.Numeric(Tile.Suit.Character, 5) !in tiles, "Expected an opponent not to hold 5m for a chi.")
        }
    }

    /** 每張牌都只出現在一個位置，總數與整副牌相同。 */
    @Test
    fun `places every tile exactly once`() {
        val (state, _) = buildScenario(invokingSeat = 3)
        val placed = state.players.flatMap { player -> player.hand.allTiles + player.discardPile.entries.map { it.tile } } +
            state.tileWall.getAllTiles() + state.reservedWallTiles

        assertEquals(placed.size, placed.map { it.id }.toSet().size)
        assertEquals(module.createWallFactory().create().getAllTiles().size, placed.size)
    }

    /** 以四人日麻桌建立情境，回傳建立後的桌況與呼叫者。 */
    private fun buildScenario(invokingSeat: Int): Pair<TableState, MahjongPlayer> {
        val players = Wind.entries.map { FakeMahjongPlayerFactory.create(initialSeat = it, playerRuleState = RiichiPlayerState()) }
        val game = Game(tableState = FakeTableStateFactory.create(players = players, config = config), flowConfig = GameFlowConfig())
        val scenario = RiichiDebugGameScenarios.all.single { it.id == "mahjongcraft:riichi_before_pao_pon" }

        val state = scenario.build(DebugGameScenarioContext(game, players[invokingSeat].id)).game.tableState

        return state to state.players[invokingSeat]
    }
}
