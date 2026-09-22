package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class HandReadinessVisibilityPolicyTest {
    private val policy = HandReadinessVisibilityPolicy(
        MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() },
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
