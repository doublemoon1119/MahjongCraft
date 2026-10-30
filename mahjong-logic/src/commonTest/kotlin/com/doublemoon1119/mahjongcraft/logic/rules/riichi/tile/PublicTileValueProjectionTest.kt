package com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.base.toPublicFaceView
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentReadContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerComponentViews
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.registerRiichiPlayerComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileFaceAccess
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 純牌值可以顯示公開特徵，但不能靠省略對象取得私有內容。 */
class PublicTileValueProjectionTest {
    /** 展示牌面保留公開特徵與外觀，不輸出授權名單或建立局內牌張。 */
    @Test
    fun `public value projection keeps permitted features and omits access lists`() {
        val registry = registry()
        val value = TileValue(
            tile = Tile.Numeric(Tile.Suit.Dot, 5),
            components = ComponentSet.EMPTY
                .with(RiichiTileComponents.RED_DORA, Unit)
                .with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Players(setOf(Uuid.random())))
                .with(BuiltInTileComponents.APPEARANCE, "test:appearance"),
        )
        val table = FakeTableStateFactory.create(config = RiichiRuleConfig())

        val face = value.toPublicFaceView("test:riichi", table, registry)

        assertEquals(value.tile, face.tile)
        assertEquals(Unit, face.views[RiichiTileComponentViews.RED_DORA])
        assertEquals(listOf(RiichiTileComponentViews.RED_DORA), face.views.types)
        assertEquals("test:appearance", face.appearanceId)
    }

    /** 公共讀取與缺少對象都不能滿足「只有本人」的投影條件。 */
    @Test
    fun `missing owner does not grant private player projections`() {
        val registry = registry()
        val table = FakeTableStateFactory.create(config = RiichiRuleConfig())
        val components = ComponentSet.EMPTY.with(RiichiPlayerComponents.PERMANENT_FURITEN, Unit)
        val readers = listOf(ComponentReadContext.Public, ComponentReadContext.Player(table.players.first().id))

        readers.forEach { reader ->
            val views = registry.project(
                context = ComponentProjectionContext(
                    ruleModuleId = "test:riichi",
                    tableState = table,
                    ownerType = BuiltInComponentOwners.PLAYER,
                    owner = null,
                    reader = reader,
                ),
                components = components,
            )
            assertTrue(views.isEmpty)
            assertNull(views[RiichiPlayerComponentViews.PERMANENT_FURITEN])
        }
    }

    /** 建立牌張公開特徵與玩家私有特徵共用的正式登記。 */
    private fun registry(): ComponentRegistryImpl = ComponentRegistryImpl().apply {
        BuiltInComponentOwners.ALL.forEach(::registerOwner)
        register(BuiltInTileComponents.FACE_ACCESS)
        register(BuiltInTileComponents.POSE)
        register(BuiltInTileComponents.APPEARANCE)
        registerRiichiTileComponents(this)
        registerRiichiPlayerComponents(this)
        freeze()
    }
}
