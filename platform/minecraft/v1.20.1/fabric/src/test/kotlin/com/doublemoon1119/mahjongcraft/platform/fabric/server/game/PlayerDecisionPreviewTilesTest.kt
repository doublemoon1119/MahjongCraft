package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_PULL_NORTH_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiTileOrder
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileValues
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
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
        val preview = RIICHI_PULL_NORTH_GAME_ACTION.preview(Hand(tiles = listOf(north), lastDrawn = drawnFive), setAsideTile = north)

        assertEquals(listOf<Tile>(Tile.Honor.North), preview.tiles.map { it.tile })
    }

    /** 規則沒有回報要移出的牌時，擴充動作不顯示牌。 */
    @Test
    fun `extension action without a set aside tile previews nothing`() {
        val preview = RIICHI_PULL_NORTH_GAME_ACTION.preview(Hand(tiles = listOf(north), lastDrawn = drawnFive))

        assertEquals(emptyList(), preview.tiles.map { it.tile })
    }

    /** 暗槓的四張都在手上時，卡片顯示這四張，不混入不屬於這組槓的剛摸到的牌。 */
    @Test
    fun `closed kan previews its own four tiles instead of the drawn tile`() {
        val kanTiles = List(4) { tile(ONE_DOT) }
        val kan = GameAction.Kan(GameAction.KanType.CLOSED_KAN, kanTiles.first().id, kanTiles.drop(1).map { it.id })

        val preview = kan.preview(Hand(tiles = kanTiles, lastDrawn = north))

        assertEquals(List(4) { ONE_DOT }, preview.tiles.map { it.tile })
    }

    /** 明槓的第四張是正在反應的那張捨牌。 */
    @Test
    fun `open kan previews the discarded tile as the fourth tile`() {
        val handTiles = List(3) { tile(ONE_DOT) }
        val discarded = tile(ONE_DOT)
        val kan = GameAction.Kan(GameAction.KanType.OPEN_KAN, discarded.id, handTiles.map { it.id })

        val preview = kan.preview(Hand(tiles = handTiles), reactedTile = discarded)

        assertEquals(List(4) { ONE_DOT }, preview.tiles.map { it.tile })
    }

    /** 碰顯示手上兩張與被碰的捨牌。 */
    @Test
    fun `pon previews two hand tiles and the claimed discard`() {
        val handTiles = List(2) { tile(Tile.Honor.Red) }
        val discarded = tile(Tile.Honor.Red)
        val pon = GameAction.Pon(discarded.id, handTiles.map { it.id })

        val preview = pon.preview(Hand(tiles = handTiles + north), reactedTile = discarded)

        assertEquals(List<Tile>(3) { Tile.Honor.Red }, preview.tiles.map { it.tile })
    }

    /** 碰到赤五時，預覽保留該張牌的赤五外觀。 */
    @Test
    fun `pon preview keeps the red five appearance`() {
        val red = RiichiTileValues.redFive(Tile.Suit.Dot).let { IdentifiedTile(Uuid.random(), it.tile, it.components) }
        val handTiles = listOf(red, tile(Tile.Numeric(Tile.Suit.Dot, 5)))
        val discarded = tile(Tile.Numeric(Tile.Suit.Dot, 5))
        val pon = GameAction.Pon(discarded.id, handTiles.map { it.id })

        val preview = pon.preview(Hand(tiles = handTiles), reactedTile = discarded)

        assertEquals(listOf(RiichiTileValues.RED_FIVE_APPEARANCE_ID, null, null), preview.tiles.map { it.appearanceId })
    }

    /** 吃依牌序排列，並標出被吃的那張。 */
    @Test
    fun `chi previews the sequence in order and marks the claimed tile`() {
        val two = tile(Tile.Numeric(Tile.Suit.Bamboo, 2))
        val four = tile(Tile.Numeric(Tile.Suit.Bamboo, 4))
        val discarded = tile(Tile.Numeric(Tile.Suit.Bamboo, 3))
        val chi = GameAction.Chi(discarded.id, listOf(four.id, two.id))

        val preview = chi.preview(Hand(tiles = listOf(two, four)), reactedTile = discarded)

        assertEquals(listOf(two.tile, discarded.tile, four.tile), preview.tiles.map { it.tile })
        assertEquals(1, preview.claimedTileIndex)
    }

    /** 榮和顯示和的那張牌。 */
    @Test
    fun `ron previews the winning tile`() {
        val discarded = tile(Tile.Honor.North)

        val preview = GameAction.Ron(discarded.id).preview(Hand(tiles = listOf(tile(ONE_DOT))), reactedTile = discarded)

        assertEquals(listOf<Tile>(Tile.Honor.North), preview.tiles.map { it.tile })
    }

    /** 自摸顯示剛摸到的牌。 */
    @Test
    fun `tsumo previews the drawn tile`() {
        val preview = GameAction.Tsumo.preview(Hand(tiles = listOf(north), lastDrawn = drawnFive))

        assertEquals(listOf(drawnFive.tile), preview.tiles.map { it.tile })
    }

    /**
     * 以本人可讀的手牌與正在回應的牌建立預覽；測試牌張的牌面全部可讀，外觀取自牌張組件。
     *
     * @param hand 玩家手牌。
     * @param reactedTile 正在回應的牌。
     * @param setAsideTile 規則指定移出的牌。
     * @return 動作預覽。
     */
    private fun GameAction.preview(
        hand: Hand,
        reactedTile: IdentifiedTile? = null,
        setAsideTile: IdentifiedTile? = null,
    ): ActionTilePreview = previewTiles(
        hand = hand,
        faces = (hand.standingTiles + listOfNotNull(reactedTile)).associate { tile ->
            tile.id to TileFaceView(tile.tile, appearanceId = tile.components[BuiltInTileComponents.APPEARANCE])
        },
        tileOrder = RiichiTileOrder,
        setAsideTileId = setAsideTile?.id,
    )

    private fun tile(tile: Tile): IdentifiedTile = IdentifiedTile(Uuid.random(), tile)

    private companion object {
        val ONE_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 1)
    }
}
