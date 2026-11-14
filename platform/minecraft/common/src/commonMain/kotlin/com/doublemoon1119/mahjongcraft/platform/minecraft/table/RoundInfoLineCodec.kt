package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * 將具型別的局況資訊編碼，供 entity tracking 與 NBT 持久化使用。
 *
 * 每個參數都有必填的 `kind`、`value` 與 `tile` 欄位，不使用的欄位填入 null；牌資料使用 [TileDto]。
 * 各參數獨立解碼，未知種類、資料損壞或訊息 ID 格式無效時，以 [RoundInfoArgument.Unrecognized] 保留原位置。
 */
object RoundInfoLineCodec {
    /** 局況資訊 JSON 編解碼器。 */
    private val json = Json

    /**
     * 將局況顯示行編碼為 JSON。
     *
     * @param lines 要儲存的局況顯示行。
     * @return 編碼後的 JSON 字串。
     */
    fun encode(lines: List<RoundInfoLine>): String = json.encodeToString(
        value = lines.map { line ->
            EncodedLine(
                key = line.key,
                args = line.args.map { argument ->
                    json.encodeToJsonElement(EncodedArgument.serializer(), EncodedArgument.fromDomain(argument))
                },
            )
        },
    )

    /**
     * 將 JSON 解碼為局況顯示行；格式無效時回傳空清單，無法辨識的參數保留為佔位值。
     *
     * @param encoded 要解碼的 JSON 字串。
     * @return 成功還原的局況顯示行。
     */
    fun decode(encoded: String): List<RoundInfoLine> = runCatching {
        json.decodeFromString<List<EncodedLine>>(encoded).map { line -> line.toDomain(json) }
    }.getOrDefault(emptyList())
}

/**
 * JSON 中單一局況顯示行的型別化表示。
 *
 * @property key 用於查詢顯示定義的完整局況行 ID。
 * @property args 按顯示順序排列的 JSON 參數。
 */
@Serializable
private data class EncodedLine(
    val key: String,
    val args: List<JsonElement>,
) {
    /**
     * 還原局況顯示行；個別參數無法解碼時以佔位值保留原位置。
     *
     * @param json 局況資料使用的 JSON 編解碼器。
     * @return 保留參數順序的局況顯示行。
     */
    fun toDomain(json: Json): RoundInfoLine = RoundInfoLine(
        key = key,
        args = args.map { element ->
            runCatching { json.decodeFromJsonElement(EncodedArgument.serializer(), element).toDomain() }
                .getOrDefault(RoundInfoArgument.Unrecognized)
        },
    )
}

/**
 * JSON 中單一局況顯示參數的型別化表示。
 *
 * @property kind 參數的型別標記。
 * @property value 參數的文字編碼值；不使用時為 null。
 * @property tile 參數的牌 DTO；不使用時為 null。
 */
@Serializable
private data class EncodedArgument(
    val kind: String,
    val value: String?,
    val tile: TileDto?,
) {
    /** 依型別標記還原局況參數；無法辨識或資料無效時回傳佔位值。 */
    fun toDomain(): RoundInfoArgument = when (kind) {
        "number" -> value?.toIntOrNull()?.let(RoundInfoArgument::Number) ?: RoundInfoArgument.Unrecognized
        "wind" -> value?.let { runCatching { RoundInfoArgument.WindValue(Wind.valueOf(it)) }.getOrNull() }
            ?: RoundInfoArgument.Unrecognized
        "tile" -> tile?.let { runCatching { RoundInfoArgument.TileValue(it.toDomain()) }.getOrNull() }
            ?: RoundInfoArgument.Unrecognized
        "message" -> value?.let(RoundInfoArgument::Message) ?: RoundInfoArgument.Unrecognized
        "unrecognized" -> RoundInfoArgument.Unrecognized
        else -> RoundInfoArgument.Unrecognized
    }

    companion object {
        /**
         * 將局況參數轉成含有三個必要欄位的可序列化表示。
         *
         * @param argument 要編碼的局況參數。
         * @return 包含參數種類與內容的保存資料。
         */
        fun fromDomain(argument: RoundInfoArgument) = when (argument) {
            is RoundInfoArgument.Number -> EncodedArgument("number", argument.value.toString(), null)
            is RoundInfoArgument.WindValue -> EncodedArgument("wind", argument.value.name, null)
            is RoundInfoArgument.TileValue -> EncodedArgument("tile", null, argument.tile.toDto())
            is RoundInfoArgument.Message -> EncodedArgument("message", argument.id, null)
            RoundInfoArgument.Unrecognized -> EncodedArgument("unrecognized", null, null)
        }
    }
}
