package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOrientation
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證牌譜實體牌牆格位的精簡格式。 */
class ReplayWallLayoutFormatTest {
    /** 格位依局內牌索引排列：一般格位寫成整數，有偏移或特殊朝向時寫完整陣列，讀回後相同。 */
    @Test
    fun `placements follow tile indexes and round trip`() {
        val plain = TileWallPlacement(TileWallPosition(3, 16, 1))
        val shifted = TileWallPlacement(
            position = TileWallPosition(2, 3, 1),
            offset = TileWallPlacementOffset(alongWallStacks = 0.5, towardTableCenterTiles = -1.0, upwardLayers = 0.0),
            orientation = TileWallPlacementOrientation.HALF_TURN,
        )
        val plainId = Uuid.random()
        val shiftedId = Uuid.random()
        val indexes = mapOf(shiftedId to 0, plainId to 2)

        val encoded = assertIs<JsonArray>(
            value = ReplayWallLayoutFormat.RoundLayout().project(
                layout = TileWallPhysicalLayout(mapOf(plainId to plain, shiftedId to shifted)),
                wallTileIds = listOf(plainId, shiftedId),
                tileIndexes = indexes::getValue,
            ),
        )

        assertEquals(3, encoded.size)
        assertIs<JsonPrimitive>(encoded[2])
        assertEquals(7, assertIs<JsonArray>(encoded[0]).size)
        assertEquals(JsonNull, encoded[1])
        assertEquals(shifted, ReplayWallLayoutFormat.decodePlacement(encoded[0]))
        assertEquals(plain, ReplayWallLayoutFormat.decodePlacement(encoded[2]))
    }

    /** 摸走的牌保留最後的格位，保存內容不變。仍在牌牆卻沒有格位的牌改寫成 null。 */
    @Test
    fun `drawn tiles keep their last placement`() {
        val drawn = Uuid.random()
        val kept = Uuid.random()
        val indexes = mapOf(drawn to 0, kept to 1)
        val layout = ReplayWallLayoutFormat.RoundLayout()
        val placements = mapOf(drawn to TileWallPlacement(TileWallPosition(0, 0, 1)), kept to TileWallPlacement(TileWallPosition(0, 0, 0)))

        val opening = layout.project(TileWallPhysicalLayout(placements), listOf(drawn, kept), indexes::getValue)
        val afterDraw = layout.project(TileWallPhysicalLayout(placements - drawn), listOf(kept), indexes::getValue)
        val lost = assertIs<JsonArray>(layout.project(TileWallPhysicalLayout(emptyMap()), listOf(kept), indexes::getValue))

        assertEquals(opening, afterDraw)
        assertEquals(JsonNull, lost[1])
    }

    /** 數值超出整數格式範圍的格位改寫成完整陣列。 */
    @Test
    fun `out of range positions use the full form`() {
        val wide = TileWallPlacement(TileWallPosition(1, 1200, 0))
        val tileId = Uuid.random()

        val encoded = assertIs<JsonArray>(ReplayWallLayoutFormat.RoundLayout().project(TileWallPhysicalLayout(mapOf(tileId to wide)), listOf(tileId)) { 0 })

        assertIs<JsonArray>(encoded.single())
        assertEquals(wide, ReplayWallLayoutFormat.decodePlacement(encoded.single()))
    }

    /** 規則沒有實體牌牆時寫成 JSON null。 */
    @Test
    fun `missing layout encodes as null`() {
        assertEquals(JsonNull, ReplayWallLayoutFormat.RoundLayout().project(null, listOf(Uuid.random())) { 0 })
    }

    /** 內容不符的值不當成格位。 */
    @Test
    fun `malformed placements are rejected`() {
        assertNull(ReplayWallLayoutFormat.decodePlacement(JsonPrimitive(-1)))
        assertNull(ReplayWallLayoutFormat.decodePlacement(JsonPrimitive("1")))
        assertNull(ReplayWallLayoutFormat.decodePlacement(JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2)))))
        val unknownOrientation = listOf(1, 2, 3).map(::JsonPrimitive) + listOf(0.0, 0.0, 0.0).map(::JsonPrimitive) + JsonPrimitive("SIDEWAYS")
        assertNull(ReplayWallLayoutFormat.decodePlacement(JsonArray(unknownOrientation)))
    }
}
