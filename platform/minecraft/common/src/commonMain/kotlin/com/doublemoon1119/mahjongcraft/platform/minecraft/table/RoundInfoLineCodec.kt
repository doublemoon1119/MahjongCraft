package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Encodes typed round information for entity tracking and NBT persistence. */
object RoundInfoLineCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(lines: List<RoundInfoLine>): String = json.encodeToString(lines.map(EncodedLine::fromDomain))

    fun decode(encoded: String): List<RoundInfoLine> = runCatching {
        json.decodeFromString<List<EncodedLine>>(encoded).mapNotNull(EncodedLine::toDomain)
    }.getOrDefault(emptyList())
}

@Serializable
private data class EncodedLine(
    val key: String,
    val args: List<EncodedArgument> = emptyList(),
) {
    fun toDomain(): RoundInfoLine? = runCatching {
        RoundInfoLine(key, args.map { it.toDomain() ?: return null })
    }.getOrNull()

    companion object {
        fun fromDomain(line: RoundInfoLine) = EncodedLine(line.key, line.args.map(EncodedArgument::fromDomain))
    }
}

@Serializable
private data class EncodedArgument(
    val kind: String,
    val value: String,
) {
    fun toDomain(): RoundInfoArgument? = when (kind) {
        "number" -> value.toIntOrNull()?.let(RoundInfoArgument::Number)
        "wind" -> runCatching { RoundInfoArgument.WindValue(Wind.valueOf(value)) }.getOrNull()
        else -> null
    }

    companion object {
        fun fromDomain(argument: RoundInfoArgument) = when (argument) {
            is RoundInfoArgument.Number -> EncodedArgument("number", argument.value.toString())
            is RoundInfoArgument.WindValue -> EncodedArgument("wind", argument.value.name)
        }
    }
}
