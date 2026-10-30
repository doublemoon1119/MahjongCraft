package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證逐張描述的共用契約，不依賴平台、Flow 或第二份判定實作。 */
class TileComponentDescriptionResolverTest {
    /** 額外授權與姿態分開作用，沒有描述的牌沿用區域預設。 */
    @Test
    fun `resolution preserves independent visibility and pose with region defaults`() {
        val first = tile("00000000-0000-0000-0000-000000000001")
        val second = tile("00000000-0000-0000-0000-000000000002")
        val third = tile("00000000-0000-0000-0000-000000000003")
        val table = table(first, second, third)
        val upright = description(first.id, TileFaceAccess.Public, TilePose.UPRIGHT)
        val flat = description(second.id, TileFaceAccess.Public, TilePose.FACE_UP)
        val default = description(third.id, TileFaceAccess.None, TilePose.UPRIGHT)
        val resolved = module(listOf(default, flat, upright)).resolveTileComponents(table, registry()).associateBy { it.area.tile.id }
        assertTrue(resolved.getValue(first.id).isPublic)
        assertEquals(TilePose.UPRIGHT, resolved.getValue(first.id).pose)
        assertTrue(resolved.getValue(second.id).isPublic)
        assertEquals(TilePose.FACE_UP, resolved.getValue(second.id).pose)
        assertTrue(module(listOf(default)).resolveTileComponents(table, registry()).filter { it.area.region == TileRegion.HAND }.none { it.isPublic })
    }

    /** 重複資料在省略預設值之前就拒絕，不能被最後一筆覆蓋。 */
    @Test
    fun `duplicate tile identities are rejected even when one description is default`() {
        val tile = tile()
        val descriptions = listOf(
            description(tile.id, TileFaceAccess.None, TilePose.UPRIGHT),
            description(tile.id, TileFaceAccess.Public, TilePose.UPRIGHT),
        )
        assertFailsWith<IllegalArgumentException> { module(descriptions).resolveTileComponents(table(tile), registry()) }
    }

    /** 各區域使用同一份授權與姿態契約，不存在的牌張仍拒絕。 */
    @Test
    fun `descriptions accept all current regions and reject unknown identities`() {
        val standing = tile()
        val wall = tile()
        val reserved = tile()
        val meldTiles = List(3) { tile() }
        val table = table(standing).let { original ->
            original.copy(
                tileWall = TileWall(listOf(wall)),
                reservedWallTiles = listOf(reserved),
                players = original.players.map { player ->
                    player.copy(
                        hand = player.hand.copy(
                            melds = listOf(
                                Meld(
                                    type = MeldType.PON,
                                    tiles = meldTiles,
                                    sourceTile = meldTiles.first(),
                                    sourceDirection = RelativeDirection.Left,
                                    publiclyRevealedTileIds = meldTiles.mapTo(mutableSetOf()) { it.id },
                                    faceDownTileIds = emptySet(),
                                ),
                            ),
                        ),
                    )
                },
            )
        }
        (listOf(wall, reserved) + meldTiles).forEach { outside ->
            val description = description(outside.id, TileFaceAccess.Public, TilePose.FACE_DOWN)
            val result = module(listOf(description)).resolveTileComponents(table, registry()).single { it.area.tile.id == outside.id }
            assertTrue(result.isPublic)
            assertEquals(TilePose.FACE_DOWN, result.pose)
        }
        assertFailsWith<IllegalArgumentException> {
            module(listOf(description(Uuid.random(), TileFaceAccess.Public, TilePose.UPRIGHT))).resolveTileComponents(table, registry())
        }
    }

    /** 未授予公開權限的牌不能要求正面朝上平放。 */
    @Test
    fun `nonpublic face up descriptions are rejected`() {
        val tile = tile()
        val description = description(tile.id, TileFaceAccess.None, TilePose.FACE_UP)
        assertFailsWith<IllegalArgumentException> { module(listOf(description)).resolveTileComponents(table(tile), registry()) }
    }

    /** 取得結果後修改規則提供的清單，不會改變已解析資料。 */
    @Test
    fun `resolved results detach from source lists`() {
        val tile = tile()
        val description = description(tile.id, TileFaceAccess.Public, TilePose.FACE_UP)
        val descriptions = mutableListOf(description)
        val result = module(descriptions).resolveTileComponents(table(tile), registry())
        descriptions.clear()
        assertEquals(TilePose.FACE_UP, result.single { it.area.tile.id == tile.id }.pose)
        assertTrue(result.single { it.area.tile.id == tile.id }.isPublic)
        assertNotSame<Any>(descriptions, result)
    }

    /**
     * 建立測試授權與姿態。
     *
     * @param tileId 目標牌張。
     * @param access 讀取權限。
     * @param pose 共同姿態。
     * @return 使用正式組件的描述。
     */
    private fun description(
        tileId: Uuid,
        access: TileFaceAccess,
        pose: TilePose,
    ): TileComponentDescription = TileComponentDescription(
        tileId = tileId,
        components = ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, access).with(BuiltInTileComponents.POSE, pose),
    )

    /** 建立這些測試使用的基礎定義。 */
    private fun registry(): ComponentRegistryImpl = ComponentRegistryImpl().apply {
        BuiltInComponentOwners.ALL.forEach(::registerOwner)
        register(BuiltInTileComponents.FACE_ACCESS)
        register(BuiltInTileComponents.POSE)
        register(BuiltInTileComponents.APPEARANCE)
        freeze()
    }

    /** 建立一張有獨立身分的一萬。 */
    private fun tile(id: String? = null): IdentifiedTile = IdentifiedTile(id?.let(Uuid::parse) ?: Uuid.random(), Tile.Numeric(Tile.Suit.Character, 1))

    /** 建立包含指定立牌的桌況，讓規則驗證只關注當前手牌區域。 */
    private fun table(vararg tiles: IdentifiedTile): TableState = FakeTableStateFactory.create(
        config = RiichiRuleConfig(),
        players = listOf(FakeMahjongPlayerFactory.create(hand = Hand(tiles = tiles.toList()))),
    )

    /** 建立只替換描述查詢的規則，不複製其他規則行為。 */
    private fun module(descriptions: List<TileComponentDescription>): MahjongRuleModule<RiichiRuleConfig> = object : MahjongRuleModule<RiichiRuleConfig> by RiichiRuleModule("test:hand-description", RiichiRuleConfig()) {
        override fun describeTileComponents(tableState: TableState): List<TileComponentDescription> = descriptions
    }
}
