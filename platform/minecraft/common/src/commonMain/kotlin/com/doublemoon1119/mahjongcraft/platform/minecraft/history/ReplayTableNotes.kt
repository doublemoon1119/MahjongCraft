package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayWallLayoutFormat
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLine
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineCodec
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPlacement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 牌譜為每份桌況保存的呈現附註，回放時與遊戲中用同樣的資料擺牌與畫出桌上的資訊。
 *
 * 這些資料在對局中由規則或桌子依完整桌況算出，牌譜本身沒有足夠資料重算，所以保存結果。
 *
 * @property extraWallReach 手牌與副露要讓開、但目前沒有牌牆牌的牌牆格位，例如之後補牌會用到的位置。
 * @property scatterPatternSeed 散放牌河這一局使用的位置表種子。牌河不散放時為 null。
 * @property cornerWidthBySeat 各座位副露角落被桌上物件占用的寬度。沒有列出的座位為 0。
 * @property roundInfo 本局資訊面板的內容，由規則提供。
 * @property playerIndicators 各座位玩家資訊上的公開標記，例如立直。沒有列出的座位沒有標記。
 * @property tableProps 規則描述的桌上物件，例如點棒。
 * @property drawHandReveals 流局結束時各座位的手牌是否公開：公開的倒下、其他的蓋起來。本局先胡牌退場的座位與不是流局
 * 結束的桌況不列出。
 */
data class ReplayTableNotes(
    val extraWallReach: Set<TileWallPlacement>,
    val scatterPatternSeed: Int?,
    val cornerWidthBySeat: Map<Int, Double>,
    val roundInfo: List<RoundInfoLine>,
    val playerIndicators: Map<Int, List<PublicPlayerIndicator>>,
    val tableProps: List<TablePropPlacement>,
    val drawHandReveals: Map<Int, Boolean>,
) {
    /**
     * 寫成牌譜保存的 JSON 物件。空的項目不寫。
     *
     * @param registries 寫出桌上物件中牌面所用的網路資料登記。
     * @return 附註物件。沒有任何內容時為 null。
     */
    fun encode(registries: NetworkDtoRegistries): JsonObject? {
        val fields = buildMap {
            if (extraWallReach.isNotEmpty()) put(WALL_REACH, JsonArray(extraWallReach.map(ReplayWallLayoutFormat::encodePlacement)))
            scatterPatternSeed?.let { put(SCATTER_SEED, JsonPrimitive(it)) }
            val widths = cornerWidthBySeat.filterValues { it != 0.0 }
            if (widths.isNotEmpty()) put(CORNER_WIDTHS, JsonObject(widths.entries.associate { (seat, width) -> seat.toString() to JsonPrimitive(width) }))
            if (roundInfo.isNotEmpty()) put(ROUND_INFO, Json.parseToJsonElement(RoundInfoLineCodec.encode(roundInfo)))
            val indicators = playerIndicators.filterValues { it.isNotEmpty() }
            if (indicators.isNotEmpty()) put(INDICATORS, ReplayPanelNotesFormat.encodeIndicators(indicators))
            if (tableProps.isNotEmpty()) put(TABLE_PROPS, JsonArray(tableProps.map { ReplayPanelNotesFormat.encodeProp(it, registries) }))
            if (drawHandReveals.isNotEmpty()) put(DRAW_HANDS, JsonObject(drawHandReveals.entries.associate { (seat, revealed) -> seat.toString() to JsonPrimitive(revealed) }))
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

        /** 本局資訊欄位。 */
        private const val ROUND_INFO: String = "roundInfo"

        /** 玩家公開標記欄位。 */
        private const val INDICATORS: String = "indicators"

        /** 桌上物件欄位。 */
        private const val TABLE_PROPS: String = "props"

        /** 流局手牌公開欄位。 */
        private const val DRAW_HANDS: String = "drawHands"

        /** 所有欄位名稱。 */
        private val FIELDS: Set<String> = setOf(WALL_REACH, SCATTER_SEED, CORNER_WIDTHS, ROUND_INFO, INDICATORS, TABLE_PROPS, DRAW_HANDS)

        /** 沒有任何附註時的內容。 */
        val EMPTY: ReplayTableNotes = ReplayTableNotes(
            extraWallReach = emptySet(),
            scatterPatternSeed = null,
            cornerWidthBySeat = emptyMap(),
            roundInfo = emptyList(),
            playerIndicators = emptyMap(),
            tableProps = emptyList(),
            drawHandReveals = emptyMap(),
        )

        /**
         * 讀取牌譜保存的附註原文。
         *
         * @param text 附註原文。沒有附註時為 null。
         * @param registries 讀取桌上物件中牌面所用的網路資料登記。
         * @return 附註。原文格式不符時為 null。
         */
        fun decode(text: String?, registries: NetworkDtoRegistries): ReplayTableNotes? {
            if (text == null) return EMPTY
            val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
            if (!root.keys.all { it in FIELDS }) return null
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
            val roundInfo = when (val value = root[ROUND_INFO]) {
                null -> emptyList()
                is JsonArray -> RoundInfoLineCodec.decode(value.toString()).takeIf { it.size == value.size } ?: return null
                else -> return null
            }
            val indicators = root[INDICATORS]?.let { ReplayPanelNotesFormat.decodeIndicators(it) ?: return null }.orEmpty()
            val props = when (val value = root[TABLE_PROPS]) {
                null -> emptyList()
                is JsonArray -> value.map { ReplayPanelNotesFormat.decodeProp(it, registries) ?: return null }
                else -> return null
            }
            val drawHands = when (val value = root[DRAW_HANDS]) {
                null -> emptyMap()
                is JsonObject -> value.entries.associate { (seat, revealed) ->
                    val seatIndex = seat.toIntOrNull()?.takeIf { it >= 0 && seat == it.toString() } ?: return null
                    seatIndex to ((revealed as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: return null)
                }
                else -> return null
            }
            return ReplayTableNotes(
                extraWallReach = reach,
                scatterPatternSeed = seed,
                cornerWidthBySeat = widths,
                roundInfo = roundInfo,
                playerIndicators = indicators,
                tableProps = props,
                drawHandReveals = drawHands,
            )
        }

        /**
         * 讀取整數。
         *
         * @param value 待讀取的值。
         * @return 整數。不是整數時為 null。
         */
        private fun integer(value: JsonElement): Int? = (value as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

        /**
         * 讀取數字。
         *
         * @param value 待讀取的值。
         * @return 數字。不是數字時為 null。
         */
        private fun number(value: JsonElement): Double? = (value as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
    }
}
