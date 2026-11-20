package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOrientation
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.uuid.Uuid

/**
 * 牌譜中實體牌牆格位的精簡格式。
 *
 * 格位依局內牌索引排成一個陣列，所以不必再寫牌的索引。牌離開牌牆後仍保留最後的格位，摸牌不會讓保存內容改變，
 * 只有牌被移到新格位、或仍在牌牆卻沒有格位時才會更新；讀取端只取當時仍在牌牆與保留區的牌。
 *
 * 每一項是一個整數，由邊、墩、層組成；牌有偏移或不是預設朝向，或數值超出整數的範圍時，改寫成邊、墩、層、
 * 沿牌牆偏移、往桌心偏移、往上偏移與朝向名稱的陣列；沒有格位的牌寫 JSON null。
 */
object ReplayWallLayoutFormat {
    /** 整數格式中層的個數上限。 */
    private const val LAYER_LIMIT: Int = 8

    /** 整數格式中墩的個數上限。 */
    private const val STACK_LIMIT: Int = 1000

    /** 整數格式中邊的個數上限。 */
    private const val SIDE_LIMIT: Int = 8

    /** 完整陣列的長度。 */
    private const val FULL_SIZE: Int = 7

    /** 一局之中逐筆交易保存的格位，依局內牌索引排列。 */
    class RoundLayout {
        /** 各張牌最後保存的格位；還沒有格位的牌為 JSON null。 */
        private val placements = mutableListOf<JsonElement>()

        /**
         * 取得這筆交易要保存的格位。
         *
         * @param layout 交易完成後的實體牌牆布局；規則沒有實體牌牆時為 null。
         * @param wallTileIds 交易完成後仍在牌牆與保留區的牌。
         * @param tileIndexes 牌 UUID 到局內牌索引的對照，涵蓋 [wallTileIds] 的每張牌。
         * @return 依局內牌索引排列的格位；沒有實體牌牆時為 JSON null。
         */
        fun project(layout: TileWallPhysicalLayout?, wallTileIds: List<Uuid>, tileIndexes: (Uuid) -> Int): JsonElement {
            if (layout == null) return JsonNull
            wallTileIds.forEach { tileId ->
                val index = tileIndexes(tileId)
                while (placements.size <= index) placements += JsonNull
                placements[index] = layout.placements[tileId]?.let(::encodePlacement) ?: JsonNull
            }
            return JsonArray(placements.toList())
        }
    }

    /**
     * 讀取一張牌的格位。
     *
     * @param value 整數或完整陣列。
     * @return 格位；格式不符時為 null。
     */
    fun decodePlacement(value: JsonElement): TileWallPlacement? {
        integer(value)?.let { code ->
            if (code < 0) return null
            val layer = code % LAYER_LIMIT
            val stack = code / LAYER_LIMIT % STACK_LIMIT
            val side = code / LAYER_LIMIT / STACK_LIMIT
            return if (side < SIDE_LIMIT) TileWallPlacement(TileWallPosition(side, stack, layer)) else null
        }
        val array = value as? JsonArray ?: return null
        if (array.size != FULL_SIZE) return null
        val side = integer(array[0]) ?: return null
        val stack = integer(array[1]) ?: return null
        val layer = integer(array[2]) ?: return null
        val along = number(array[3]) ?: return null
        val toward = number(array[4]) ?: return null
        val upward = number(array[5]) ?: return null
        val orientationName = (array[6] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val orientation = TileWallPlacementOrientation.entries.firstOrNull { it.name == orientationName } ?: return null
        return TileWallPlacement(TileWallPosition(side, stack, layer), TileWallPlacementOffset(along, toward, upward), orientation)
    }

    /**
     * 寫出一張牌的格位：沒有偏移、是預設朝向且數值在範圍內時寫整數，否則寫完整陣列。
     *
     * @param placement 格位。
     * @return 整數或完整陣列。
     */
    fun encodePlacement(placement: TileWallPlacement): JsonElement {
        val position = placement.position
        val plain = placement.offset == TileWallPlacementOffset.Zero && placement.orientation == TileWallPlacementOrientation.DEFAULT
        val fits = position.side in 0 until SIDE_LIMIT && position.stack in 0 until STACK_LIMIT && position.layer in 0 until LAYER_LIMIT
        if (plain && fits) return JsonPrimitive((position.side * STACK_LIMIT + position.stack) * LAYER_LIMIT + position.layer)
        val offset = placement.offset
        return JsonArray(
            content = listOf(
                JsonPrimitive(position.side),
                JsonPrimitive(position.stack),
                JsonPrimitive(position.layer),
                JsonPrimitive(offset.alongWallStacks),
                JsonPrimitive(offset.towardTableCenterTiles),
                JsonPrimitive(offset.upwardLayers),
                JsonPrimitive(placement.orientation.name),
            ),
        )
    }

    /**
     * 讀取整數。
     *
     * @param value 待讀取的值。
     * @return 整數；不是整數時為 null。
     */
    private fun integer(value: JsonElement): Int? = (value as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

    /**
     * 讀取數字。
     *
     * @param value 待讀取的值。
     * @return 數字；不是數字時為 null。
     */
    private fun number(value: JsonElement): Double? = (value as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
}
