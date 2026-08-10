package com.doublemoon1119.mahjongcraft.flow.persistence.dto.core

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證權威存檔 envelope 的 encoded round-trip。 */
class PersistenceEnvelopeDtoTest {
    /** 驗證 schema version 與世界級權威狀態通過 JSON 編解碼後不失真。 */
    @Test
    fun `persistence envelope round-trips through encoded JSON`() {
        val envelope = PersistenceEnvelopeDto(
            schemaVersion = PersistenceSchema.CURRENT_VERSION,
            state = buildJsonObject { put("roomCount", 2) },
        )

        val encoded = Json.encodeToString(PersistenceEnvelopeDto.serializer(), envelope)
        val decoded = Json.decodeFromString(PersistenceEnvelopeDto.serializer(), encoded)

        assertEquals(envelope, decoded)
    }
}
