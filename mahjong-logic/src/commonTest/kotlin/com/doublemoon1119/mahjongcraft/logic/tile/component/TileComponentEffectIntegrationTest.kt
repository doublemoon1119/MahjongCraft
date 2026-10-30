package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentEffectDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證正式牌區解算同時收集主規則與其他擴充，不依觀察者重跑效果。 */
class TileComponentEffectIntegrationTest {
    /** 兩個來源可授權不同玩家，但不會把該牌變成公開或翻面。 */
    @Test
    fun `rule and extension effects combine in the actual tile resolver`() {
        val config = ThreePlayerRiichiRuleConfig()
        val base = ThreePlayerRiichiRuleModule("test:rule", config)
        val players = List(3) { FakeMahjongPlayerFactory.create(hand = Hand()) }
        val tile = IdentifiedTile(Uuid.random(), Tile.Numeric(Tile.Suit.Dot, 5))
        val table = FakeTableStateFactory.create(players = players, config = config).copy(tileWall = TileWall(listOf(tile)))
        val rule = object : MahjongRuleModule<ThreePlayerRiichiRuleConfig> by base {
            override fun describeTileComponents(tableState: TableState): List<TileComponentDescription> = listOf(
                TileComponentDescription(
                    tileId = tile.id,
                    components = ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Players(setOf(players[0].id))),
                ),
            )
        }
        val registry = registry()
        var calls = 0
        registry.registerEffect(
            ComponentEffectDefinition(
                id = "test:extra_access",
                ownerType = BuiltInComponentOwners.TILE,
                appliesTo = { ruleId, _ -> ruleId == rule.id },
                resolve = { context ->
                    assertEquals(table, context.tableState)
                    calls++
                    ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Players(setOf(players[1].id)))
                },
            ),
        )
        val resolved = rule.resolveTileComponents(table, registry).single()
        assertTrue(resolved.canRead(TileReadContext.Player(players[0].id)))
        assertTrue(resolved.canRead(TileReadContext.Player(players[1].id)))
        assertFalse(resolved.canRead(TileReadContext.Player(players[2].id)))
        assertFalse(resolved.canRead(TileReadContext.Public()))
        assertEquals(TilePose.FACE_DOWN, resolved.pose)
        assertEquals(1, calls)
        assertTrue(tile.components.isEmpty)
    }

    /** 適用的來源失敗時明確拒絕，不交付缺少必要效果的結果。 */
    @Test
    fun `effect failure is not silently ignored by tile resolution`() {
        val config = ThreePlayerRiichiRuleConfig()
        val rule = ThreePlayerRiichiRuleModule("test:rule", config)
        val tile = IdentifiedTile(Uuid.random(), Tile.Honor.North)
        val table = FakeTableStateFactory.create(config = config).copy(tileWall = TileWall(listOf(tile)))
        val registry = registry()
        registry.registerEffect(
            ComponentEffectDefinition(
                id = "test:broken_source",
                ownerType = BuiltInComponentOwners.TILE,
                appliesTo = { _, _ -> true },
                resolve = { error("Required effect failed") },
            ),
        )
        assertFailsWith<IllegalStateException> { rule.resolveTileComponents(table, registry) }
    }

    /** 只登記共同解算所需的基礎組件。 */
    private fun registry(): ComponentRegistryImpl = ComponentRegistryImpl().apply {
        BuiltInComponentOwners.ALL.forEach(::registerOwner)
        register(BuiltInTileComponents.FACE_ACCESS)
        register(BuiltInTileComponents.POSE)
        register(BuiltInTileComponents.APPEARANCE)
    }
}
