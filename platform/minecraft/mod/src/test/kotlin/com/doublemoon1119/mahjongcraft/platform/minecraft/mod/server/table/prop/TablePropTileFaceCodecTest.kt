package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.prop

import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentViewPersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.registerBuiltInComponentViewPersistence
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponentViews
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 驗證公開牌展示副本的保存格式保留牌面與組件公開效果，且不接受無法辨識的資料。 */
class TablePropTileFaceCodecTest {
    private val codec = TablePropTileFaceCodec(ComponentViewPersistenceRegistry().apply(::registerBuiltInComponentViewPersistence))

    /** 數牌、字牌和擴充牌都保存牌面，沒有正式牌張的 UUID。 */
    @Test
    fun `public tile copies preserve built in and extension faces`() {
        val faces = listOf(Tile.Numeric(Tile.Suit.Dot, 5), Tile.Honor.Red, Tile.Extension(TileTypeId.parse("other:custom/tile"))).map(::TileFaceView)
        faces.forEach { face -> assertEquals(face, codec.decode(codec.encode(face))) }
    }

    /** 組件的公開效果與外觀一起保存，例如透明牌。 */
    @Test
    fun `component views and appearance survive a round trip`() {
        val face = TileFaceView(
            tile = Tile.Honor.East,
            views = ComponentViewSet.EMPTY.with(BuiltInTileComponentViews.TRANSPARENT, Unit),
            appearanceId = "other:style",
        )

        assertEquals(face, codec.decode(codec.encode(face)))
    }

    /** 無法辨識或含有未登記效果的衍生資料交由同步移除，不會變成新的牌面。 */
    @Test
    fun `invalid derived face data is discarded`() {
        val transparent = TileFaceView(Tile.Honor.East, ComponentViewSet.EMPTY.with(BuiltInTileComponentViews.TRANSPARENT, Unit))
        val withoutViews = TablePropTileFaceCodec(ComponentViewPersistenceRegistry())

        assertNull(codec.decode(""))
        assertNull(codec.decode("not json"))
        assertNull(codec.decode("{\"tile\":{\"type\":\"unknown\"},\"views\":[],\"appearanceId\":null}"))
        assertNull(withoutViews.decode(codec.encode(transparent)))
    }
}
