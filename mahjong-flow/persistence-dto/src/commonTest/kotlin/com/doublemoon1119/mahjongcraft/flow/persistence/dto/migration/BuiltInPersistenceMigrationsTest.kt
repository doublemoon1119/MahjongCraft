package com.doublemoon1119.mahjongcraft.flow.persistence.dto.migration

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證內建 persistence migration registry 的預設組裝行為。 */
class BuiltInPersistenceMigrationsTest {
    /** 驗證目前 schema 可透過內建 registry 直接讀取。 */
    @Test
    fun `current schema passes through built-in registry`() {
        val envelope = PersistenceEnvelopeDto(
            schemaVersion = PersistenceSchema.CURRENT_VERSION,
            state = buildJsonObject { put("state", "current") },
        )

        assertEquals(envelope, buildBuiltInPersistenceMigrationRegistry().migrate(envelope))
    }
}
