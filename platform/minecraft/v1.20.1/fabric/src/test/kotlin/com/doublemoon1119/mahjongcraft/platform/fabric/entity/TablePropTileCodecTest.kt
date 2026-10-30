package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 驗證公開牌展示副本的保存格式不丟失第三方命名空間。 */
class TablePropTileCodecTest {
    /** 數牌、字牌和擴充牌都保存牌種，沒有正式牌張的 UUID。 */
    @Test
    fun `public tile copies preserve built in and extension faces`() {
        val tiles = listOf(Tile.Numeric(Tile.Suit.Dot, 5), Tile.Honor.Red, Tile.Extension(TileTypeId.parse("other:custom/tile")))
        tiles.forEach { tile -> assertEquals(tile, TablePropTileCodec.decode(TablePropTileCodec.encode(tile))) }
    }

    /** 無法辨識的衍生資料交由同步移除，不會成為新的權威牌種。 */
    @Test
    fun `invalid derived tile data is discarded`() {
        assertNull(TablePropTileCodec.decode(""))
        assertNull(TablePropTileCodec.decode("not json"))
        assertNull(TablePropTileCodec.decode("{\"type\":\"unknown\"}"))
    }
}
