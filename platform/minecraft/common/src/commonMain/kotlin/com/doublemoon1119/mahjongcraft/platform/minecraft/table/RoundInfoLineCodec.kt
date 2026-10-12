package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 將具型別的局況資訊編碼，供 entity tracking 與 NBT 持久化使用。 */
object RoundInfoLineCodec {
    /** 局況資訊 JSON 編解碼器。 */
    private val json = Json

    /**
     * 將局況顯示行編碼為 JSON。
     *
     * @param lines 要儲存的局況顯示行。
     * @return 編碼後的 JSON 字串。
     */
    fun encode(lines: List<RoundInfoLine>): String = json.encodeToString(lines.map(EncodedLine::fromDomain))

    /**
     * 將 JSON 解碼為局況顯示行；格式無效或參數無法解析時回傳空清單。
     *
     * @param encoded 要解碼的 JSON 字串。
     * @return 成功還原的局況顯示行。
     */
    fun decode(encoded: String): List<RoundInfoLine> = runCatching {
        json.decodeFromString<List<EncodedLine>>(encoded).mapNotNull(EncodedLine::toDomain)
    }.getOrDefault(emptyList())
}

/** JSON 中單一局況顯示行的型別化表示。 */
@Serializable
private data class EncodedLine(
    /** 顯示行的翻譯 key。 */
    val key: String,
    /** 按顯示順序排列的型別化參數。 */
    val args: List<EncodedArgument>,
) {
    /** 還原局況顯示行；遇到無法識別的參數時回傳 null。 */
    fun toDomain(): RoundInfoLine? = runCatching {
        RoundInfoLine(key, args.map { it.toDomain() ?: return null })
    }.getOrNull()

    companion object {
        /** 將局況顯示行及其參數轉成可序列化表示。 */
        fun fromDomain(line: RoundInfoLine) = EncodedLine(line.key, line.args.map(EncodedArgument::fromDomain))
    }
}

/** JSON 中單一局況顯示參數的型別化表示。 */
@Serializable
private data class EncodedArgument(
    /** 參數的型別標記。 */
    val kind: String,
    /** 參數的文字編碼值。 */
    val value: String,
) {
    /** 依型別標記還原數字或風位參數；無效值回傳 null。 */
    fun toDomain(): RoundInfoArgument? = when (kind) {
        "number" -> value.toIntOrNull()?.let(RoundInfoArgument::Number)
        "wind" -> runCatching { RoundInfoArgument.WindValue(Wind.valueOf(value)) }.getOrNull()
        else -> null
    }

    companion object {
        /** 將數字或風位參數轉成可序列化表示。 */
        fun fromDomain(argument: RoundInfoArgument) = when (argument) {
            is RoundInfoArgument.Number -> EncodedArgument("number", argument.value.toString())
            is RoundInfoArgument.WindValue -> EncodedArgument("wind", argument.value.name)
        }
    }
}
