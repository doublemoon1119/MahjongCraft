package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentReadContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewType
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證同張牌在各區域的私人授權不會變成全桌公開資訊。 */
class TileComponentVisibilityTest {
    /** 指定玩家可知牌面，不會讓旁觀者或公共事件一併取得牌面與外觀。 */
    @Test
    fun `private grants do not become public in any tile region`() {
        val recipient = Uuid.random()
        val other = Uuid.random()
        for (region in TileRegion.entries) {
            val resolved = resolved(region, TileFaceAccess.Players(setOf(recipient)))
            assertTrue(resolved.canRead(TileReadContext.Player(recipient)))
            assertFalse(resolved.canRead(TileReadContext.Player(other)))
            assertFalse(resolved.canRead(TileReadContext.Public()))
            assertFalse(resolved.canRead(TileReadContext.Spectator(Uuid.random(), emptySet())))
            assertNull(resolved.appearanceFor(TileReadContext.Player(other)))
            assertEquals("example:readable", resolved.appearanceFor(TileReadContext.Player(recipient)))
            assertEquals(TilePose.FACE_DOWN, resolved.pose)
        }
    }

    /** 知道牌面不等於牌已翻面，也不會變更公共姿態。 */
    @Test
    fun `public knowledge preserves face down pose`() {
        for (region in TileRegion.entries) {
            val resolved = resolved(region, TileFaceAccess.Public)
            assertTrue(resolved.canRead(TileReadContext.Public()))
            assertEquals(TilePose.FACE_DOWN, resolved.pose)
        }
    }

    /** 不同隱藏牌面、私有樣式與授權名單對無權限讀取者投影成相同內容。 */
    @Test
    fun `hidden differences do not affect unauthorized projections`() {
        val tileId = Uuid.random()
        val first = resolved(TileRegion.WALL, TileFaceAccess.Players(setOf(Uuid.random()))).let { it.copy(area = it.area.copy(tile = it.area.tile.copy(id = tileId))) }
        val second = first.copy(
            area = first.area.copy(tile = first.area.tile.copy(tile = Tile.Numeric(Tile.Suit.Dot, 9))),
            components = first.components
                .with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Players(setOf(Uuid.random())))
                .with(BuiltInTileComponents.APPEARANCE, "example:another"),
        )
        val context = TileReadContext.Player(Uuid.random())
        assertEquals(first.canRead(context), second.canRead(context))
        assertEquals(first.appearanceFor(context), second.appearanceFor(context))
        assertEquals(first.pose, second.pose)
    }

    /** 牌面公開也不表示牌上所有特徵都能公開；私人特徵仍依同一讀取情境裁切。 */
    @Test
    fun `known face does not expose private component values or keys`() {
        val viewer = Uuid.random()
        val secret = ComponentType(
            id = "example:tile/private_weight",
            valueClass = Int::class,
            copyValue = { it },
            target = BuiltInComponentOwners.TILE,
        )
        val view = ComponentViewType(
            id = "example:tile/allowed_weight",
            valueClass = Int::class,
            copyValue = { it },
        )
        val registry = ComponentRegistryImpl().apply {
            BuiltInComponentOwners.ALL.forEach(::registerOwner)
            register(BuiltInTileComponents.FACE_ACCESS)
            register(BuiltInTileComponents.POSE)
            register(BuiltInTileComponents.APPEARANCE)
            register(secret)
            registerView(view)
            registerProjection(
                ComponentProjectionDefinition(
                    id = "example:tile/weight_projection",
                    ownerType = BuiltInComponentOwners.TILE,
                    component = secret,
                    viewType = view,
                    appliesTo = { _, _ -> true },
                    project = { context, value -> value.takeIf { context.reader == ComponentReadContext.Player(viewer) } },
                ),
            )
        }
        val original = resolved(TileRegion.WALL, TileFaceAccess.Public)
        val resolved = original.copy(components = original.components.with(secret, 7))
        val table = FakeTableStateFactory.create().copy(tileWall = TileWall(listOf(resolved.area.tile)))
        val publicFace = requireNotNull(resolved.faceViewFor(TileReadContext.Public(), "example:rule", table, registry))
        assertEquals(resolved.area.tile.tile, publicFace.tile)
        assertTrue(publicFace.views.isEmpty)
        val privateFace = requireNotNull(resolved.faceViewFor(TileReadContext.Player(viewer), "example:rule", table, registry))
        assertEquals(7, privateFace.views[view])
        assertEquals(listOf(view), privateFace.views.types)
        val hidden = resolved.copy(components = resolved.components.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.None))
        assertNull(hidden.faceViewFor(TileReadContext.Public(), "example:rule", table, registry))
    }

    /**
     * 建立姿態與牌面授權分開的權威描述。
     *
     * @param region 要測試的所在區域。
     * @param access 牌面讀取授權。
     * @return 尚未對任何讀取者裁切的描述。
     */
    private fun resolved(region: TileRegion, access: TileFaceAccess): ResolvedTileComponents = ResolvedTileComponents(
        area = TileAreaEntry(
            tile = IdentifiedTile(Uuid.random(), Tile.Numeric(Tile.Suit.Character, 1)),
            region = region,
            ownerId = if (region == TileRegion.WALL || region == TileRegion.RESERVED) null else Uuid.random(),
            playerAreaId = "test:aside".takeIf { region == TileRegion.SET_ASIDE },
        ),
        components = ComponentSet.EMPTY
            .with(BuiltInTileComponents.FACE_ACCESS, access)
            .with(BuiltInTileComponents.POSE, TilePose.FACE_DOWN)
            .with(BuiltInTileComponents.APPEARANCE, "example:readable"),
        publicEvidence = false,
    )
}
