package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PULL_NORTH_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiTileOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證決策卡片的每張牌都依動作帶的牌 ID 查出，不拿觸發決策的牌去推測。 */
class PlayerDecisionPreviewTilesTest {
    private val north = tile(Tile.Honor.North)
    private val drawnFive = tile(Tile.Numeric(Tile.Suit.Dot, 5))

    /** 移出手牌的動作顯示規則回報要移出的牌，不是剛摸到的牌。 */
    @Test
    fun `set aside action previews the tile it sets aside`() {
        val preview = PULL_NORTH_GAME_ACTION.preview(Hand(tiles = listOf(north), lastDrawn = drawnFive), setAsideTile = Tile.Honor.North)

        assertEquals(listOf<Tile>(Tile.Honor.North), preview.tiles)
    }

    /** 規則沒有回報要移出的牌時，擴充動作不顯示牌。 */
    @Test
    fun `extension action without a set aside tile previews nothing`() {
        val preview = PULL_NORTH_GAME_ACTION.preview(Hand(tiles = listOf(north), lastDrawn = drawnFive))

        assertEquals(emptyList(), preview.tiles)
    }

    /** 暗槓的四張都在手上時，卡片顯示這四張，不混入不屬於這組槓的剛摸到的牌。 */
    @Test
    fun `closed kan previews its own four tiles instead of the drawn tile`() {
        val kanTiles = List(4) { tile(ONE_DOT) }
        val kan = GameAction.Kan(GameAction.KanType.CLOSED_KAN, kanTiles.first().id, kanTiles.drop(1).map { it.id })

        val preview = kan.preview(Hand(tiles = kanTiles, lastDrawn = north))

        assertEquals(List(4) { ONE_DOT }, preview.tiles)
    }

    /** 明槓的第四張是正在反應的那張捨牌。 */
    @Test
    fun `open kan previews the discarded tile as the fourth tile`() {
        val handTiles = List(3) { tile(ONE_DOT) }
        val discarded = tile(ONE_DOT)
        val kan = GameAction.Kan(GameAction.KanType.OPEN_KAN, discarded.id, handTiles.map { it.id })

        val preview = kan.preview(Hand(tiles = handTiles), reactedTile = discarded)

        assertEquals(List(4) { ONE_DOT }, preview.tiles)
    }

    /** 碰顯示手上兩張與被碰的捨牌。 */
    @Test
    fun `pon previews two hand tiles and the claimed discard`() {
        val handTiles = List(2) { tile(Tile.Honor.Red) }
        val discarded = tile(Tile.Honor.Red)
        val pon = GameAction.Pon(discarded.id, handTiles.map { it.id })

        val preview = pon.preview(Hand(tiles = handTiles + north), reactedTile = discarded)

        assertEquals(List<Tile>(3) { Tile.Honor.Red }, preview.tiles)
    }

    /** 吃依牌序排列，並標出被吃的那張。 */
    @Test
    fun `chi previews the sequence in order and marks the claimed tile`() {
        val two = tile(Tile.Numeric(Tile.Suit.Bamboo, 2))
        val four = tile(Tile.Numeric(Tile.Suit.Bamboo, 4))
        val discarded = tile(Tile.Numeric(Tile.Suit.Bamboo, 3))
        val chi = GameAction.Chi(discarded.id, listOf(four.id, two.id))

        val preview = chi.preview(Hand(tiles = listOf(two, four)), reactedTile = discarded)

        assertEquals(listOf(two.tile, discarded.tile, four.tile), preview.tiles)
        assertEquals(1, preview.claimedTileIndex)
    }

    /** 榮和顯示和的那張牌。 */
    @Test
    fun `ron previews the winning tile`() {
        val discarded = tile(Tile.Honor.North)

        val preview = GameAction.Ron(discarded.id).preview(Hand(tiles = listOf(tile(ONE_DOT))), reactedTile = discarded)

        assertEquals(listOf<Tile>(Tile.Honor.North), preview.tiles)
    }

    /** 自摸顯示剛摸到的牌。 */
    @Test
    fun `tsumo previews the drawn tile`() {
        val preview = GameAction.Tsumo.preview(Hand(tiles = listOf(north), lastDrawn = drawnFive))

        assertEquals(listOf(drawnFive.tile), preview.tiles)
    }

    private fun GameAction.preview(
        hand: Hand,
        reactedTile: IdentifiedTile? = null,
        setAsideTile: Tile? = null,
    ): ActionTilePreview = previewTiles(
        hand = hand,
        reactedTile = reactedTile,
        tileOrder = RiichiTileOrder,
        setAsideTile = setAsideTile,
    )

    private fun tile(tile: Tile): IdentifiedTile = IdentifiedTile(Uuid.random(), tile)

    private companion object {
        val ONE_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 1)
    }
}
