package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.PlayerTileAreas
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiDeclarationComponents
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiTableComponents
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證手牌分析只向本人提供，且剩餘張數會扣除他家公開的拔北牌。 */
class HandReadinessVisibilityPolicyTest {
    /** 提供測試所需的內建規則。 */
    private val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }

    /** 依觀察者身分決定手牌分析可見性的政策。 */
    private val policy = HandReadinessVisibilityPolicy(
        moduleRegistry = moduleRegistry,
        visibilityPolicy = GameVisibilityPolicyImpl(moduleRegistry, componentRegistry = bundledCoreExtensionRegistries().componentRegistry),
    )

    /** 參與者可取得自己的等待牌分析，不在對局中的觀察者無法取得。 */
    @Test
    fun `participant receives only their own current hand analysis`() {
        val player = FakeMahjongPlayerFactory.create(hand = FakeHandFactory.create(tiles = tenpaiTiles))
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(player),
                config = RiichiRuleConfig(),
            ),
            flowConfig = GameConfig(RiichiRuleConfig()).flowConfig,
            hostId = player.id,
        )

        val snapshot = assertNotNull(policy.snapshotFor(game, player.id))

        assertEquals("mahjongcraft:riichi", snapshot.ruleModuleId)
        assertEquals(2, snapshot.analysis.waitingTiles.size)
        assertNull(policy.snapshotFor(game, Uuid.random()))
    }

    /** 剩餘張數扣除他家拔出、公開擺在桌上的北。 */
    @Test
    fun `remaining count excludes north tiles pulled by other players`() {
        val waitingHand = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9).map { Tile.Numeric(Tile.Suit.Dot, it) } +
            listOf(1, 2, 3).map { Tile.Numeric(Tile.Suit.Bamboo, it) } +
            Tile.Honor.North
        val player = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST, hand = FakeHandFactory.create(tiles = waitingHand))
        val puller = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            components = riichiDeclarationComponents(),
            tileAreas = PlayerTileAreas.EMPTY.withArea(RiichiPlayerTileAreaIds.NORTH, List(2) { FakeIdentifiedTileFactory.create(Tile.Honor.North) }),
        )
        val config = ThreePlayerRiichiRuleConfig()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(player, puller, FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST)),
                config = config,
                components = riichiTableComponents(potPoints = 0),
            ),
            flowConfig = GameConfig(config).flowConfig,
            hostId = player.id,
        )

        val wait = assertNotNull(policy.snapshotFor(game, player.id)).analysis.waitingTiles.single()

        assertEquals(Tile.Honor.North, wait.tile)
        assertEquals(1, wait.remainingCount)
    }

    /** 本局完成後不再提供聽牌分析，避免結算畫面期間繼續顯示聽牌 HUD。 */
    @Test
    fun `completed round hides current hand analysis`() {
        val player = FakeMahjongPlayerFactory.create(hand = FakeHandFactory.create(tiles = tenpaiTiles))
        val table = FakeTableStateFactory.create(
            players = listOf(player),
            config = RiichiRuleConfig(),
        )
        val game = Game(
            tableState = table,
            flowConfig = GameConfig(RiichiRuleConfig()).flowConfig,
            roundCompletion = RoundCompletionSummary(
                outcomeId = "test:win",
                classification = RoundCompletionClassification.WIN,
                beneficiaryPlayerIds = setOf(player.id),
                transitionDirective = RoundTransitionDirective.PassToNextSeat,
                settledScoresByPlayerId = mapOf(player.id to player.score),
            ),
            hostId = player.id,
        )

        assertNull(policy.snapshotFor(game, player.id))
    }

    /** 共用的聽牌牌型。 */
    private companion object {
        /** 三組萬子順子、五餅雀頭與四五索搭子，等待三索或六索。 */
        val tenpaiTiles = listOf(
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 2),
            Tile.Numeric(Tile.Suit.Character, 3),
            Tile.Numeric(Tile.Suit.Character, 4),
            Tile.Numeric(Tile.Suit.Character, 5),
            Tile.Numeric(Tile.Suit.Character, 6),
            Tile.Numeric(Tile.Suit.Character, 7),
            Tile.Numeric(Tile.Suit.Character, 8),
            Tile.Numeric(Tile.Suit.Character, 9),
            Tile.Numeric(Tile.Suit.Dot, 5),
            Tile.Numeric(Tile.Suit.Dot, 5),
            Tile.Numeric(Tile.Suit.Bamboo, 4),
            Tile.Numeric(Tile.Suit.Bamboo, 5),
        )
    }
}
