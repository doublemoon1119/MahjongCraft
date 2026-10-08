package com.doublemoon1119.mahjongcraft.flow.server.game.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.simulation.SimulationRuntime
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.completeRiichiReservedWall
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.withFirstKanPhysicalWallLayout
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 從實際對局流程送出宣告、捨牌與榮和，驗證要看對局經過的古役會寫進胡牌結算。
 *
 * 和牌者的手牌另有役牌（發）成立，因此關閉古役時同一張牌仍能榮和，可以比較兩種設定的結算差異。
 */
class RiichiLocalYakuFlowTest {
    private val gameId = Uuid.random()
    private val discarderId = Uuid.random()
    private val winnerId = Uuid.random()

    /** 暗槓補牌後打出的牌被榮和：啟用古役時結算有槓振，關閉時沒有任何古役，役牌照常計算。 */
    @Test
    fun `ron on the discard after a kan settles kanburi only when local yaku are enabled`() = runTest {
        val enabled = ronOnDiscardAfterKan(useLocalYaku = true)
        val disabled = ronOnDiscardAfterKan(useLocalYaku = false)

        assertTrue(YakuType.Kanburi in enabled, "Settled yaku: $enabled")
        assertTrue(YakuType.Dragon in enabled && YakuType.Dragon in disabled)
        assertTrue(disabled.none { it.isLocal }, "Settled yaku: $disabled")
    }

    /** 以立直宣言牌榮和：啟用古役時結算有燕返，關閉時沒有任何古役，役牌照常計算。 */
    @Test
    fun `ron on a riichi declaration tile settles tsubame gaeshi only when local yaku are enabled`() = runTest {
        val enabled = ronOnRiichiDeclaration(useLocalYaku = true)
        val disabled = ronOnRiichiDeclaration(useLocalYaku = false)

        assertTrue(YakuType.TsubameGaeshi in enabled, "Settled yaku: $enabled")
        assertTrue(YakuType.Dragon in enabled && YakuType.Dragon in disabled)
        assertTrue(disabled.none { it.isLocal }, "Settled yaku: $disabled")
    }

    /** 莊家暗槓、補嶺上牌後打出紅中，由和牌者榮和；回傳結算的役種。 */
    private suspend fun ronOnDiscardAfterKan(useLocalYaku: Boolean): Set<YakuType> {
        val kanTiles = List(4) { tile(m(1)) }
        val red = tile(Tile.Honor.Red)
        val otherTiles = listOf(p(1), p(1), p(9), p(9), s(1), s(9), Tile.Honor.South, Tile.Honor.West, Tile.Honor.North).map(::tile)
        val discarder = discarder(hand = Hand(tiles = kanTiles.take(3) + red + otherTiles, lastDrawn = kanTiles.last()))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, winner()),
            config = RiichiRuleConfig(useLocalYaku = useLocalYaku),
            reservedWallTiles = completeRiichiReservedWall(tile(s(2))),
            currentPlayerIndex = 0,
            dynamicRuleState = RiichiDynamicState(),
        ).withFirstKanPhysicalWallLayout()

        return play(
            table,
            discarderId to GameCommand.Kan(GameAction.KanType.CLOSED_KAN, kanTiles.last().id),
            discarderId to GameCommand.Discard(red.id),
            winnerId to GameCommand.RespondToDiscard(GameAction.Ron(red.id)),
        )
    }

    /** 莊家以紅中宣告立直（打完仍聽 1、4 索），由和牌者榮和這張宣言牌；回傳結算的役種。 */
    private suspend fun ronOnRiichiDeclaration(useLocalYaku: Boolean): Set<YakuType> {
        val red = tile(Tile.Honor.Red)
        val standing = listOf(m(1), m(2), m(3), p(4), p(5), p(6), s(7), s(8), s(9), Tile.Honor.East, Tile.Honor.East, s(2)).map(::tile)
        val discarder = discarder(hand = Hand(tiles = standing + red, lastDrawn = tile(s(3))))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, winner()),
            config = RiichiRuleConfig(useLocalYaku = useLocalYaku),
            currentPlayerIndex = 0,
            dynamicRuleState = RiichiDynamicState(),
        )

        return play(
            table,
            discarderId to GameCommand.Extension(RiichiGameCommand(red.id)),
            winnerId to GameCommand.RespondToDiscard(GameAction.Ron(red.id)),
        )
    }

    /** 依序送出 [commands]，每一步都必須成功；回傳和牌者在胡牌結算裡的役種。 */
    private suspend fun play(table: TableState, vararg commands: Pair<Uuid, GameCommand>): Set<YakuType> {
        val runtime = SimulationRuntime(defaultStrategyKey = "unused")
        runtime.gameRepository.setTableState(table)
        commands.forEach { (playerId, command) ->
            val result = runtime.coordinator(gameId, playerId, command)
            assertTrue(result is Outcome.Success, "$command failed: $result")
        }
        val settled = runtime.gameRepository.historyDrafts.map { it.fact }.filterIsInstance<HistoryFact.WinSettled>().single()
        val fields = settled.winDetails.single { it.playerId == winnerId }.detailFields
        val yaku = fields.single { it.id == RiichiWinSettlementIds.YAKU_FIELD }.value as WinSettlementDetailValue.Entries
        return yaku.entries.mapNotNull { RiichiWinSettlementIds.yakuType(it.id) }.toSet()
    }

    /** 已打過一巡、這一巡剛摸牌、點數足夠立直的莊家。 */
    private fun discarder(hand: Hand): MahjongPlayer {
        val earlier = tile(Tile.Honor.North)
        return FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            hand = hand,
            discardPile = RiichiDiscardPile().discardTile(earlier),
            playerRuleState = RiichiPlayerState(),
        ).copy(score = STARTING_SCORE).recordAction(GameAction.Draw).recordAction(GameAction.Discard(earlier.id)).recordAction(GameAction.Draw)
    }

    /** 已打過一巡、發刻子成立、單騎聽紅中的和牌者。 */
    private fun winner(): MahjongPlayer {
        val earlier = tile(Tile.Honor.West)
        val tiles = listOf(
            Tile.Honor.Green,
            Tile.Honor.Green,
            Tile.Honor.Green,
            m(2),
            m(3),
            m(4),
            p(5),
            p(6),
            p(7),
            s(6),
            s(7),
            s(8),
            Tile.Honor.Red,
        )
        return FakeMahjongPlayerFactory.create(
            id = winnerId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = tiles.map(::tile)),
            discardPile = RiichiDiscardPile().discardTile(earlier),
            playerRuleState = RiichiPlayerState(),
        ).copy(score = STARTING_SCORE).recordAction(GameAction.Draw).recordAction(GameAction.Discard(earlier.id))
    }

    private fun tile(tile: Tile): IdentifiedTile = FakeIdentifiedTileFactory.create(tile)

    /** 萬子。 */
    private fun m(value: Int): Tile = Tile.Numeric(Tile.Suit.Character, value)

    /** 筒子。 */
    private fun p(value: Int): Tile = Tile.Numeric(Tile.Suit.Dot, value)

    /** 索子。 */
    private fun s(value: Int): Tile = Tile.Numeric(Tile.Suit.Bamboo, value)

    private companion object {
        /** 兩位玩家的點數。 */
        const val STARTING_SCORE = 25_000
    }
}
