package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileFaceAccess
import com.doublemoon1119.mahjongcraft.logic.tile.component.resolveTileAreas
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證玩家多牌區的身分、順序、資料隔離與唯一歸屬。 */
class PlayerTileAreasTest {
    /** 更新較早的區域時，後面的其他區域保持位置與內容。 */
    @Test
    fun `updating an area preserves other area order and tile versions`() {
        val first = tile()
        val second = tile()
        val extra = tile()
        val original = PlayerTileAreas.EMPTY.withArea("test:north", listOf(first)).withArea("test:flowers", listOf(second))
        val updated = original.withArea("test:north", listOf(first, extra))
        assertEquals(listOf("test:north", "test:flowers"), updated.areas.map { it.id })
        assertEquals(listOf(first, extra, second), updated.allTiles)
        assertEquals(listOf(first, second), original.allTiles)
        assertNull(updated["test:missing"])
    }

    /** 來源與回傳清單的修改不能改變已建立的牌區。 */
    @Test
    fun `tile areas detach caller and returned collections`() {
        val first = tile()
        val second = tile()
        val source = mutableListOf(first, second)
        val area = PlayerTileArea("test:area", source)
        source.clear()
        assertEquals(listOf(first, second), area.tiles)
        val returned = area.tiles as MutableList<IdentifiedTile>
        returned.clear()
        assertEquals(listOf(first, second), area.tiles)
        val sourceAreas = mutableListOf(area, PlayerTileArea("test:other", emptyList()))
        val areas = PlayerTileAreas(sourceAreas)
        sourceAreas.clear()
        (areas.areas as MutableList<PlayerTileArea>).clear()
        assertEquals(listOf("test:area", "test:other"), areas.areas.map { it.id })
    }

    /** 非法區域身分、重複區域及跨區重複牌張都會拒絕。 */
    @Test
    fun `area identifiers and current tile ownership must be unique`() {
        val tile = tile()
        assertFailsWith<IllegalArgumentException> { PlayerTileArea("not-namespaced", listOf(tile)) }
        assertFailsWith<IllegalArgumentException> { PlayerTileArea("test:area", listOf(tile, tile)) }
        val first = PlayerTileArea("test:first", listOf(tile))
        assertFailsWith<IllegalArgumentException> { PlayerTileAreas(listOf(first, first)) }
        assertFailsWith<IllegalArgumentException> { PlayerTileAreas(listOf(first, PlayerTileArea("test:second", listOf(tile)))) }
        val player = FakeMahjongPlayerFactory.create(
            hand = Hand(tiles = listOf(tile)),
            tileAreas = PlayerTileAreas(listOf(first)),
        )
        assertFailsWith<IllegalArgumentException> { FakeTableStateFactory.create(players = listOf(player)).resolveTileAreas() }
    }

    /** 替換組件版本不得順便改牌面、換身分或搬動區域。 */
    @Test
    fun `component replacement preserves the exact area and physical identity`() {
        val originalTile = tile()
        val areas = PlayerTileAreas.EMPTY.withArea("test:area", listOf(originalTile))
        val replacement = originalTile.copy(components = ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public))
        val updated = areas.replaceTile(originalTile.id, replacement)
        assertEquals(listOf(replacement), updated["test:area"]?.tiles)
        assertEquals(listOf(originalTile), areas["test:area"]?.tiles)
        assertFailsWith<IllegalArgumentException> { areas.replaceTile(originalTile.id, replacement.copy(id = Uuid.random())) }
        assertFailsWith<IllegalArgumentException> { areas.replaceTile(originalTile.id, replacement.copy(tile = Tile.Honor.White)) }
        assertFailsWith<IllegalArgumentException> { areas.replaceTile(Uuid.random(), replacement) }
    }

    /** 建立獨立的北牌，不與其他案例共用局內身分。 */
    private fun tile(): IdentifiedTile = IdentifiedTile(Uuid.random(), Tile.Honor.North)
}
