package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class HandReadinessVisibilityPolicyTest {
    private val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
    private val policy = HandReadinessVisibilityPolicy(
        moduleRegistry = moduleRegistry,
        visibilityPolicy = GameVisibilityPolicyImpl(moduleRegistry),
    )

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
            playerRuleState = RiichiPlayerState(nukiDoraTiles = List(2) { FakeIdentifiedTileFactory.create(Tile.Honor.North) }),
        )
        val config = ThreePlayerRiichiRuleConfig()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(player, puller, FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST)),
                config = config,
                dynamicRuleState = RiichiDynamicState(),
            ),
            flowConfig = GameConfig(config).flowConfig,
            hostId = player.id,
        )

        val wait = assertNotNull(policy.snapshotFor(game, player.id)).analysis.waitingTiles.single()

        assertEquals(Tile.Honor.North, wait.tile)
        assertEquals(1, wait.remainingCount)
    }

    private companion object {
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
