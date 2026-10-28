package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
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
class HandTileDescriptionResolverTest {
    /** 預設描述省略，公開直立與部分平放則保留，並使用穩定 UUID 順序。 */
    @Test
    fun `normalization preserves independent visibility and pose and omits defaults`() {
        val first = tile("00000000-0000-0000-0000-000000000001")
        val second = tile("00000000-0000-0000-0000-000000000002")
        val third = tile("00000000-0000-0000-0000-000000000003")
        val table = table(first, second, third)
        val upright = HandTileDescription(first.id, HandTileVisibility.Public, HandTilePose.UPRIGHT)
        val flat = HandTileDescription(second.id, HandTileVisibility.Public, HandTilePose.FACE_UP)
        val default = HandTileDescription(third.id, HandTileVisibility.Default, HandTilePose.UPRIGHT)
        assertEquals(listOf(upright, flat), module(listOf(default, flat, upright)).resolveHandTileDescriptions(table))
        assertTrue(module(listOf(default)).resolveHandTileDescriptions(table).isEmpty())
    }

    /** 重複資料在省略預設值之前就拒絕，不能被最後一筆覆蓋。 */
    @Test
    fun `duplicate tile identities are rejected even when one description is default`() {
        val tile = tile()
        val descriptions = listOf(
            HandTileDescription(tile.id, HandTileVisibility.Default, HandTilePose.UPRIGHT),
            HandTileDescription(tile.id, HandTileVisibility.Public, HandTilePose.UPRIGHT),
        )
        assertFailsWith<IllegalArgumentException> { module(descriptions).resolveHandTileDescriptions(table(tile)) }
    }

    /** 牌山、保留牌、副露與不存在的 UUID 均不能冒充當前手牌。 */
    @Test
    fun `descriptions cannot reference other tile areas`() {
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
        (listOf(wall, reserved) + meldTiles + tile()).forEach { outside ->
            val description = HandTileDescription(outside.id, HandTileVisibility.Public, HandTilePose.UPRIGHT)
            assertFailsWith<IllegalArgumentException> { module(listOf(description)).resolveHandTileDescriptions(table) }
        }
    }

    /** 未授予公開權限的牌不能要求正面朝上平放。 */
    @Test
    fun `nonpublic face up descriptions are rejected`() {
        val tile = tile()
        val description = HandTileDescription(tile.id, HandTileVisibility.Default, HandTilePose.FACE_UP)
        assertFailsWith<IllegalArgumentException> { module(listOf(description)).resolveHandTileDescriptions(table(tile)) }
    }

    /** 取得結果後修改規則提供的清單，不會改變已解析資料。 */
    @Test
    fun `resolved results detach from source lists`() {
        val tile = tile()
        val description = HandTileDescription(tile.id, HandTileVisibility.Public, HandTilePose.FACE_UP)
        val descriptions = mutableListOf(description)
        val result = module(descriptions).resolveHandTileDescriptions(table(tile))
        descriptions.clear()
        assertEquals(listOf(description), result)
        assertNotSame(descriptions, result)
    }

    /** 建立一張有獨立身分的一萬。 */
    private fun tile(id: String? = null): IdentifiedTile = IdentifiedTile(id?.let(Uuid::parse) ?: Uuid.random(), Tile.Numeric(Tile.Suit.Character, 1))

    /** 建立包含指定立牌的桌況，讓規則驗證只關注當前手牌區域。 */
    private fun table(vararg tiles: IdentifiedTile): TableState = FakeTableStateFactory.create(
        config = RiichiRuleConfig(),
        players = listOf(FakeMahjongPlayerFactory.create(hand = Hand(tiles = tiles.toList()))),
    )

    /** 建立只替換描述查詢的規則，不複製其他規則行為。 */
    private fun module(descriptions: List<HandTileDescription>): MahjongRuleModule<RiichiRuleConfig> = object : MahjongRuleModule<RiichiRuleConfig> by RiichiRuleModule("test:hand-description", RiichiRuleConfig()) {
        override fun describeHandTiles(tableState: TableState): List<HandTileDescription> = descriptions
    }
}
