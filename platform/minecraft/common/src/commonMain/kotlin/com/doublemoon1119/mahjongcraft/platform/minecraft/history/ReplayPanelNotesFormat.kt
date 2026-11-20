package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicatorValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSeatAnchor
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSeatOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 牌譜呈現附註中玩家公開標記與桌上物件的精簡寫法：以短陣列代替具名物件，讓每筆交易之間的差異儘量小。
 *
 * - 公開標記：`{"座位": [[id], [id, "c", 次數], [id, "o", 選項]]}`。
 * - 桌上物件：`[id, kind, 內容, 位置]`；內容為 `["v", 變體]` 或 `["t", 牌面]`，位置為
 *   `["s", 座位, 錨點, x, y, z, 朝向偏移]` 或 `["c", 順序, 朝向]`。
 */
internal object ReplayPanelNotesFormat {
    /** 次數標記的種類代號。 */
    private const val COUNT = "c"

    /** 選項標記的種類代號。 */
    private const val OPTION = "o"

    /** 變體內容的代號。 */
    private const val VARIANT = "v"

    /** 牌面內容的代號。 */
    private const val TILE = "t"

    /** 座位位置的代號。 */
    private const val SEAT = "s"

    /** 桌子中央位置的代號。 */
    private const val CENTER = "c"

    /**
     * 寫出各座位的公開標記。
     *
     * @param indicators 各座位的公開標記。
     * @return JSON 物件。
     */
    fun encodeIndicators(indicators: Map<Int, List<PublicPlayerIndicator>>): JsonObject = JsonObject(
        content = indicators.entries.associate { (seat, list) ->
            seat.toString() to JsonArray(
                content = list.map { indicator ->
                    JsonArray(
                        content = when (val value = indicator.indicatorValue) {
                            PublicPlayerIndicatorValue.Marker -> listOf(JsonPrimitive(indicator.id))
                            is PublicPlayerIndicatorValue.Count -> listOf(JsonPrimitive(indicator.id), JsonPrimitive(COUNT), JsonPrimitive(value.value))
                            is PublicPlayerIndicatorValue.Option -> listOf(JsonPrimitive(indicator.id), JsonPrimitive(OPTION), JsonPrimitive(value.optionId))
                        },
                    )
                },
            )
        },
    )

    /**
     * 讀取各座位的公開標記。
     *
     * @param element 保存的 JSON。
     * @return 各座位的公開標記；格式不符時為 null。
     */
    fun decodeIndicators(element: JsonElement): Map<Int, List<PublicPlayerIndicator>>? {
        val root = element as? JsonObject ?: return null
        return root.entries.associate { (seat, list) ->
            val seatIndex = seat.toIntOrNull()?.takeIf { it >= 0 && seat == it.toString() } ?: return null
            val entries = list as? JsonArray ?: return null
            seatIndex to entries.map { decodeIndicator(it) ?: return null }
        }
    }

    /**
     * 讀取一個公開標記。
     *
     * @param element 保存的 JSON。
     * @return 公開標記；格式不符時為 null。
     */
    private fun decodeIndicator(element: JsonElement): PublicPlayerIndicator? {
        val parts = element as? JsonArray ?: return null
        val id = string(parts.getOrNull(0)) ?: return null
        val value = when (parts.size) {
            1 -> PublicPlayerIndicatorValue.Marker
            3 -> when (string(parts[1])) {
                COUNT -> PublicPlayerIndicatorValue.Count(integer(parts[2])?.takeIf { it >= 0 } ?: return null)
                OPTION -> PublicPlayerIndicatorValue.Option(string(parts[2]) ?: return null)
                else -> return null
            }
            else -> return null
        }
        return runCatching { PublicPlayerIndicator(id, value) }.getOrNull()
    }

    /**
     * 寫出一個桌上物件。
     *
     * @param prop 桌上物件。
     * @param registries 寫出牌面所用的網路資料登記。
     * @return JSON 陣列。
     */
    fun encodeProp(prop: TablePropPlacement, registries: NetworkDtoRegistries): JsonArray = JsonArray(
        content = listOf(
            JsonPrimitive(prop.id),
            JsonPrimitive(prop.kind),
            when (val content = prop.content) {
                is TablePropContent.CustomVariant -> JsonArray(listOf(JsonPrimitive(VARIANT), JsonPrimitive(content.value)))
                is TablePropContent.FaceUpTile -> JsonArray(
                    content = listOf(
                        JsonPrimitive(TILE),
                        Json.parseToJsonElement(NetworkJson(registries).encodeToString(TileFaceViewDto.serializer(), content.face.toDto(registries))),
                    ),
                )
            },
            when (val location = prop.location) {
                is TablePropLocation.Seat -> JsonArray(
                    content = listOf(
                        JsonPrimitive(SEAT),
                        JsonPrimitive(location.seatIndex),
                        JsonPrimitive(location.anchor.name),
                        JsonPrimitive(location.offset.x),
                        JsonPrimitive(location.offset.y),
                        JsonPrimitive(location.offset.z),
                        JsonPrimitive(location.yawOffset),
                    ),
                )
                is TablePropLocation.Center -> JsonArray(listOf(JsonPrimitive(CENTER), JsonPrimitive(location.order), JsonPrimitive(location.yaw)))
            },
        ),
    )

    /**
     * 讀取一個桌上物件。
     *
     * @param element 保存的 JSON。
     * @param registries 讀取牌面所用的網路資料登記。
     * @return 桌上物件；格式不符時為 null。
     */
    fun decodeProp(element: JsonElement, registries: NetworkDtoRegistries): TablePropPlacement? {
        val parts = (element as? JsonArray)?.takeIf { it.size == PROP_PARTS } ?: return null
        val id = string(parts[0]) ?: return null
        val kind = string(parts[1]) ?: return null
        val content = decodeContent(parts[2], registries) ?: return null
        val location = decodeLocation(parts[3]) ?: return null
        return runCatching { TablePropPlacement(id = id, kind = kind, content = content, location = location) }.getOrNull()
    }

    /**
     * 讀取桌上物件的內容。
     *
     * @param element 保存的 JSON。
     * @param registries 讀取牌面所用的網路資料登記。
     * @return 內容；格式不符時為 null。
     */
    private fun decodeContent(element: JsonElement, registries: NetworkDtoRegistries): TablePropContent? {
        val parts = (element as? JsonArray)?.takeIf { it.size == 2 } ?: return null
        return when (string(parts[0])) {
            VARIANT -> string(parts[1])?.let { runCatching { TablePropContent.CustomVariant(it) }.getOrNull() }
            TILE -> runCatching {
                TablePropContent.FaceUpTile(NetworkJson(registries).decodeFromString(TileFaceViewDto.serializer(), parts[1].toString()).toDomain(registries))
            }.getOrNull()
            else -> null
        }
    }

    /**
     * 讀取桌上物件的位置。
     *
     * @param element 保存的 JSON。
     * @return 位置；格式不符時為 null。
     */
    private fun decodeLocation(element: JsonElement): TablePropLocation? {
        val parts = element as? JsonArray ?: return null
        return when (string(parts.getOrNull(0))) {
            SEAT -> {
                if (parts.size != SEAT_PARTS) return null
                val anchor = TableSeatAnchor.entries.firstOrNull { it.name == string(parts[2]) } ?: return null
                val offset = TableSeatOffset(x = number(parts[3]) ?: return null, y = number(parts[4]) ?: return null, z = number(parts[5]) ?: return null)
                runCatching { TablePropLocation.Seat(integer(parts[1]) ?: return null, anchor, offset, number(parts[6])?.toFloat() ?: return null) }.getOrNull()
            }
            CENTER -> {
                if (parts.size != CENTER_PARTS) return null
                runCatching { TablePropLocation.Center(integer(parts[1]) ?: return null, number(parts[2])?.toFloat() ?: return null) }.getOrNull()
            }
            else -> null
        }
    }

    /**
     * 讀取字串。
     *
     * @param value 待讀取的值。
     * @return 字串；不是字串時為 null。
     */
    private fun string(value: JsonElement?): String? = (value as? JsonPrimitive)?.takeIf { it.isString }?.content

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
     * @return 有限的數字；不是數字時為 null。
     */
    private fun number(value: JsonElement): Double? = (value as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull?.takeIf { it.isFinite() }

    /** 桌上物件陣列的元素數。 */
    private const val PROP_PARTS = 4

    /** 座位位置陣列的元素數。 */
    private const val SEAT_PARTS = 7

    /** 桌子中央位置陣列的元素數。 */
    private const val CENTER_PARTS = 3
}
