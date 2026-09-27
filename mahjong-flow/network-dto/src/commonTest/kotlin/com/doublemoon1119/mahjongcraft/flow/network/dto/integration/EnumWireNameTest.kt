package com.doublemoon1119.mahjongcraft.flow.network.dto.integration

import com.doublemoon1119.mahjongcraft.flow.network.dto.command.KanTypeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 封包中的 enum 使用名稱而非宣告順序作為 JSON 值。 */
class EnumWireNameTest {
    @Test
    fun `wind dto uses stable names on the wire`() {
        WindDto.entries.forEach { wind ->
            val encoded = Json.encodeToString(wind)

            assertEquals("\"${wind.name}\"", encoded)
            assertEquals(wind, Json.decodeFromString<WindDto>(encoded))
        }
    }

    @Test
    fun `action dto enum uses stable names on the wire`() {
        assertEquals("\"OPEN_KAN\"", Json.encodeToString(KanTypeDto.OPEN_KAN))
        assertEquals(KanTypeDto.OPEN_KAN, Json.decodeFromString<KanTypeDto>("\"OPEN_KAN\""))
    }

    @Test
    fun `unknown wind dto name is rejected`() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<WindDto>("\"UNKNOWN\"")
        }
    }
}
