package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileFaceAccess
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證桌況差異能記錄並重建本局已公開的手牌。 */
class HistoryTableChangeTest {
    private val before = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create()))

    /** 更新與清空桌況組件都能以差異重建，不回退成完整檢查點。 */
    @Test
    fun `table component changes and removals round trip through deltas`() {
        val points = ComponentType(
            id = "test:table/points",
            valueClass = Int::class,
            copyValue = { it },
            target = BuiltInComponentOwners.TABLE,
        )
        val after = before.copy(components = ComponentSet.EMPTY.with(points, 3000))
        val change = assertNotNull(HistoryTableChange.between(before, after))
        assertEquals(after, change.applyTo(before))
        val cleared = assertNotNull(HistoryTableChange.between(after, before))
        assertEquals(ComponentSet.EMPTY, cleared.components)
        assertEquals(before, cleared.applyTo(after))
    }

    /** 摸牌與留在牌山的牌更新組件，可以記在同一份差異。 */
    @Test
    fun `wall component updates coexist with removals without changing tile identity`() {
        val tiles = List(4) { IdentifiedTile(Uuid.random(), Tile.Numeric(Tile.Suit.Character, it + 1)) }
        val original = before.copy(tileWall = TileWall(tiles))
        val updated = tiles[2].copy(
            components = ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public),
        )
        val after = original.copy(tileWall = TileWall(listOf(tiles[1], updated)))
        val change = assertNotNull(HistoryTableChange.between(original, after))
        val wall = assertNotNull(change.wall)
        assertEquals(1, wall.removedFront)
        assertEquals(1, wall.removedBack)
        assertEquals(listOf(updated), wall.updatedTiles)
        assertEquals(after, change.applyTo(original))
        val retyped = updated.copy(tile = Tile.Honor.White)
        assertFailsWith<IllegalArgumentException> {
            wall.copy(updatedTiles = listOf(retyped)).applyTo(original.tileWall)
        }
        assertFailsWith<IllegalArgumentException> {
            wall.copy(updatedTiles = listOf(tiles.first())).applyTo(original.tileWall)
        }
        assertNull(HistoryTableChange.between(original, after.copy(tileWall = TileWall(listOf(retyped, tiles[1])))))
    }

    /** 已公開手牌改變時，差異記下新集合，套用後還原成同一份桌況。 */
    @Test
    fun `change records and restores revealed hand tiles`() {
        val after = before.copy(publicTileEvidenceIds = setOf(Uuid.random()))

        val change = assertNotNull(HistoryTableChange.between(before, after))

        assertEquals(after.publicTileEvidenceIds, change.publicTileEvidenceIds)
        assertEquals(after, change.applyTo(before))
    }

    /** 已公開手牌沒有改變時不記錄。 */
    @Test
    fun `unchanged revealed hand tiles are not recorded`() {
        val after = before.copy(currentPlayerIndex = before.currentPlayerIndex)

        assertNull(assertNotNull(HistoryTableChange.between(before, after)).publicTileEvidenceIds)
    }

    /** 流局公開手牌與等待牌改變時，差異能記下並還原完整資料。 */
    @Test
    fun `change records and restores revealed hand settlements`() {
        val settlement = RevealedHandSettlement(
            playerId = before.players.first().id,
            waitingTiles = setOf(Tile.Honor.North),
        )
        val revealedTileId = Uuid.random()
        val after = before.copy(
            publicTileEvidenceIds = setOf(revealedTileId),
            revealedHandSettlements = listOf(settlement),
        )

        val change = assertNotNull(HistoryTableChange.between(before, after))

        assertEquals(setOf(revealedTileId), change.publicTileEvidenceIds)
        assertEquals(listOf(settlement), change.revealedHandSettlements)
        assertEquals(after, change.applyTo(before))
    }

    /** 流局公開資料清空時，空集合不能被誤認為未改變。 */
    @Test
    fun `change records clearing revealed hand settlements`() {
        val settlement = RevealedHandSettlement(
            playerId = before.players.first().id,
            waitingTiles = setOf(Tile.Honor.North),
        )
        val source = before.copy(revealedHandSettlements = listOf(settlement))
        val after = source.copy(revealedHandSettlements = emptyList())

        val change = assertNotNull(HistoryTableChange.between(source, after))

        assertEquals(emptyList(), change.revealedHandSettlements)
        assertEquals(after, change.applyTo(source))
    }

    /** 逐輪擲骰結果改變時，差異能保留非空的前後序列。 */
    @Test
    fun `change records and restores opening dice rolls`() {
        val playerId = before.players.first().id
        val first = OpeningDiceRoll(0, playerId, DiceRollResult.of(listOf(1, 6)))
        val second = OpeningDiceRoll(1, playerId, DiceRollResult.of(listOf(3, 4)))
        val source = before.copy(openingDiceRolls = listOf(first))
        val after = source.copy(openingDiceRolls = listOf(first, second))

        val change = assertNotNull(HistoryTableChange.between(source, after))

        assertEquals(listOf(first, second), change.openingDiceRolls)
        assertEquals(after, change.applyTo(source))
    }
}
