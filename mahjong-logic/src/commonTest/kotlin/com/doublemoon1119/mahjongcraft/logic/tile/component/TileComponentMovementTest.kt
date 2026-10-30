package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.isRiichiDeclaration
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile.DiscardEntry
import com.doublemoon1119.mahjongcraft.logic.table.PlayerTileAreas
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiDeclarationComponents
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

/** 驗證同一張牌換區域與修改特性時，身分、組件及歷史版本互不混淆。 */
class TileComponentMovementTest {
    /** 提供內建移出牌接點的三人日麻規則。 */
    private val config = ThreePlayerRiichiRuleConfig()

    /** 只登記測試會用到的組件。 */
    private val registry = ComponentRegistryImpl().apply {
        BuiltInComponentOwners.ALL.forEach(::registerOwner)
        register(BuiltInTileComponents.FACE_ACCESS)
    }

    /** 牌山摸入、整理及捨牌都保留原有組件，不依牌面種類共享。 */
    @Test
    fun `draw sort and discard preserve per tile components`() {
        val open = tile()
        val hidden = open.copy(id = Uuid.random(), components = ComponentSet.EMPTY)
        val wall = TileWall(listOf(open, hidden))
        val draw = wall.draw()
        assertEquals(open, draw.tile)
        val hand = Hand(lastDrawn = draw.tile)
        assertEquals(open, hand.standingTiles.single())
        val discarded = requireNotNull(hand.discardById(open.id))
        assertEquals(open, discarded.tile)
        assertEquals(hidden, draw.wall.draw().tile)
        assertNotEquals(open.components, hidden.components)
    }

    /** 所有當前區域都能修改；被取走牌河與原桌況維持原值。 */
    @Test
    fun `component changes cover all current areas without rewriting history`() {
        val tile = tile()
        for (region in TileRegion.entries) {
            val player = FakeMahjongPlayerFactory.create(hand = Hand(), discardPile = RiichiDiscardPile(), components = riichiDeclarationComponents())
            val meldTiles = listOf(tile, tile().copy(components = ComponentSet.EMPTY), tile().copy(components = ComponentSet.EMPTY))
            val meld = Meld(MeldType.PON, meldTiles, tile, RelativeDirection.Left, meldTiles.map { it.id }.toSet(), emptySet())
            val beforePlayer = when (region) {
                TileRegion.HAND -> player.copy(hand = Hand(tiles = listOf(tile)))
                TileRegion.DRAWN -> player.copy(hand = Hand(lastDrawn = tile))
                TileRegion.MELD -> player.copy(hand = Hand(melds = listOf(meld)), discardPile = RiichiDiscardPile(listOf(DiscardEntry(tile = tile, isTaken = true))))
                TileRegion.DISCARD -> player.copy(discardPile = RiichiDiscardPile(listOf(DiscardEntry(tile = tile, components = RiichiDiscardComponents.declaration()))))
                TileRegion.SET_ASIDE -> player.copy(components = riichiDeclarationComponents(), tileAreas = PlayerTileAreas.EMPTY.withArea(RiichiPlayerTileAreaIds.NORTH, listOf(tile)))
                else -> player
            }
            val before = FakeTableStateFactory.create(config = config, players = listOf(beforePlayer)).copy(
                tileWall = TileWall(if (region == TileRegion.WALL) listOf(tile) else emptyList()),
                reservedWallTiles = if (region == TileRegion.RESERVED) listOf(tile) else emptyList(),
            )
            val patch = ComponentPatch.basedOn(tile.components, values = ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.None))
            val changed = before.applyTileComponentChange(TileComponentChange(tile.id, patch), registry)
            val updated = changed.resolveTileAreas().single { it.tile.id == tile.id }
            assertEquals(region, updated.region)
            assertEquals(TileFaceAccess.None, updated.tile.components[BuiltInTileComponents.FACE_ACCESS])
            assertEquals(TileFaceAccess.Public, before.resolveTileAreas().single { it.tile.id == tile.id }.tile.components[BuiltInTileComponents.FACE_ACCESS])
            if (region == TileRegion.MELD) {
                assertEquals(updated.tile, changed.players.single().hand.melds.single().sourceTile)
                assertEquals(tile, changed.players.single().discardPile.entries.single().tile)
            }
            if (region == TileRegion.DISCARD) assertEquals(true, changed.players.single().discardPile.entries.single().isRiichiDeclaration)
            assertFailsWith<IllegalArgumentException> { changed.applyTileComponentChange(TileComponentChange(tile.id, patch), registry) }
        }
    }

    /** 建立帶有公開特性、但不因此改變牌面或姿態的獨立牌張。 */
    private fun tile(): IdentifiedTile = IdentifiedTile(
        id = Uuid.random(),
        tile = Tile.Numeric(Tile.Suit.Character, 1),
        components = ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public),
    )
}
