package com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.ResolvedTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileAreaEntry
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileFaceAccess
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileReadContext
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 赤寶牌特性只能隨已獲准的牌面交付，不讓未知牌透露特性。 */
class RiichiTileComponentsTest {
    /** 牌面及特性對本人可見，其他玩家與旁觀者在牌面隱藏時都拿不到特性。 */
    @Test
    fun `red dora traits are projected only with an authorized face`() {
        val ownerId = Uuid.random()
        val otherId = Uuid.random()
        val components = ComponentSet.EMPTY.with(RiichiTileComponents.RED_DORA, Unit)
            .with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.None)
            .with(BuiltInTileComponents.POSE, TilePose.UPRIGHT)
        val tile = IdentifiedTile(
            id = Uuid.random(),
            tile = Tile.Numeric(Tile.Suit.Dot, 5),
            components = components,
        )
        val resolved = ResolvedTileComponents(
            area = TileAreaEntry(tile = tile, region = TileRegion.HAND, ownerId = ownerId),
            components = components,
            publicEvidence = false,
        )
        val registry = ComponentRegistryImpl().apply {
            BuiltInComponentOwners.ALL.forEach(::registerOwner)
            register(BuiltInTileComponents.FACE_ACCESS)
            register(BuiltInTileComponents.POSE)
            registerRiichiTileComponents(this)
            freeze()
        }
        val table = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            players = listOf(
                FakeMahjongPlayerFactory.create(id = ownerId, hand = Hand(tiles = listOf(tile))),
                FakeMahjongPlayerFactory.create(id = otherId),
            ),
        )
        val owner = assertNotNull(resolved.faceViewFor(TileReadContext.Player(ownerId), RULE_ID, table, registry))
        assertEquals(tile.tile, owner.tile)
        assertEquals(Unit, owner.views[RiichiTileComponentViews.RED_DORA])
        assertNull(resolved.faceViewFor(TileReadContext.Player(otherId), RULE_ID, table, registry))
        assertNull(resolved.faceViewFor(TileReadContext.Spectator(otherId, emptySet()), RULE_ID, table, registry))
        assertNull(resolved.faceViewFor(TileReadContext.Public(), RULE_ID, table, registry))

        val revealed = resolved.copy(components = components.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public))
        val publicFace = assertNotNull(revealed.faceViewFor(TileReadContext.Public(), RULE_ID, table, registry))
        assertEquals(Unit, publicFace.views[RiichiTileComponentViews.RED_DORA])
        assertEquals(TilePose.UPRIGHT, revealed.pose)
    }

    /** 牌組與可見牌使用相同計算特性，姿態、外觀與指示牌用途不混入牌值分組。 */
    @Test
    fun `composition and authorized face decode to the same calculation value`() {
        val tile = Tile.Numeric(Tile.Suit.Dot, 5)
        val composition = TileValue(
            tile = tile,
            components = ComponentSet.EMPTY
                .with(RiichiTileComponents.RED_DORA, Unit)
                .with(RiichiTileComponents.DORA_INDICATOR, Unit)
                .with(BuiltInTileComponents.APPEARANCE, "test:appearance"),
        )
        val face = TileFaceView(
            tile = tile,
            views = ComponentViewSet.EMPTY
                .with(RiichiTileComponentViews.RED_DORA, Unit)
                .with(RiichiTileComponentViews.DORA_INDICATOR, Unit),
        )
        val expected = TileValue(tile, ComponentSet.EMPTY.with(RiichiTileComponents.RED_DORA, Unit))

        assertEquals(expected, RiichiTileInterpretationPolicy.valueOf(composition))
        assertEquals(expected, RiichiTileInterpretationPolicy.valueOf(face))
        assertEquals(true, RiichiTileInterpretationPolicy.isUnavailableForDraw(face))
        assertEquals(false, RiichiTileInterpretationPolicy.isUnavailableForDraw(face.copy(views = ComponentViewSet.EMPTY)))
    }

    /** 測試共用的規則識別碼。 */
    private companion object {
        /** 只用於日麻組件投影的規則情境。 */
        const val RULE_ID = "test:riichi"
    }
}
