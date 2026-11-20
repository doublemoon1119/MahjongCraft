package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayWallLayoutFormat
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 牌譜為每份桌況保存的牌桌擺放附註，回放時與遊戲中用同樣的資料擺牌。
 *
 * 這些資料在對局中由規則或桌子依完整桌況算出，牌譜本身沒有足夠資料重算，所以保存結果。
 *
 * @property extraWallReach 手牌與副露要讓開、但目前沒有牌牆牌的牌牆格位，例如之後補牌會用到的位置。
 * @property scatterPatternSeed 散放牌河這一局使用的位置表種子；牌河不散放時為 null。
 * @property cornerWidthBySeat 各座位副露角落被桌上物件占用的寬度；沒有列出的座位為 0。
 */
data class ReplayTableNotes(
    val extraWallReach: Set<TileWallPlacement>,
    val scatterPatternSeed: Int?,
    val cornerWidthBySeat: Map<Int, Double>,
) {
    /**
     * 寫成牌譜保存的 JSON 物件；空的項目不寫。
     *
     * @return 附註物件；沒有任何內容時為 null。
     */
    fun encode(): JsonObject? {
        val fields = buildMap {
            if (extraWallReach.isNotEmpty()) put(WALL_REACH, JsonArray(extraWallReach.map(ReplayWallLayoutFormat::encodePlacement)))
            scatterPatternSeed?.let { put(SCATTER_SEED, JsonPrimitive(it)) }
            val widths = cornerWidthBySeat.filterValues { it != 0.0 }
            if (widths.isNotEmpty()) put(CORNER_WIDTHS, JsonObject(widths.entries.associate { (seat, width) -> seat.toString() to JsonPrimitive(width) }))
        }
        return fields.takeIf { it.isNotEmpty() }?.let(::JsonObject)
    }

    /** 附註的欄位名稱與讀取。 */
    companion object {
        /** 讓位範圍欄位。 */
        private const val WALL_REACH: String = "wallReach"

        /** 位置表種子欄位。 */
        private const val SCATTER_SEED: String = "scatterSeed"

        /** 角落寬度欄位。 */
        private const val CORNER_WIDTHS: String = "cornerWidths"

        /** 沒有任何附註時的內容。 */
        val EMPTY: ReplayTableNotes = ReplayTableNotes(extraWallReach = emptySet(), scatterPatternSeed = null, cornerWidthBySeat = emptyMap())

        /**
         * 讀取牌譜保存的附註原文。
         *
         * @param text 附註原文；沒有附註時為 null。
         * @return 附註；原文格式不符時為 null。
         */
        fun decode(text: String?): ReplayTableNotes? {
            if (text == null) return EMPTY
            val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
            if (!root.keys.all { it in setOf(WALL_REACH, SCATTER_SEED, CORNER_WIDTHS) }) return null
            val reach = when (val value = root[WALL_REACH]) {
                null -> emptySet()
                is JsonArray -> value.map { ReplayWallLayoutFormat.decodePlacement(it) ?: return null }.toSet()
                else -> return null
            }
            val seed = root[SCATTER_SEED]?.let { integer(it) ?: return null }
            val widths = when (val value = root[CORNER_WIDTHS]) {
                null -> emptyMap()
                is JsonObject -> value.entries.associate { (seat, width) ->
                    val seatIndex = seat.toIntOrNull()?.takeIf { it >= 0 && seat == it.toString() } ?: return null
                    seatIndex to (number(width)?.takeIf { it.isFinite() && it >= 0.0 } ?: return null)
                }
                else -> return null
            }
            return ReplayTableNotes(extraWallReach = reach, scatterPatternSeed = seed, cornerWidthBySeat = widths)
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
}
