package com.doublemoon1119.mahjongcraft.flow.persistence.dto

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證權威存檔 migration 的版本檢查與連續轉換行為。 */
class PersistenceMigrationRegistryTest {
    /** 驗證目前 schema version 不會執行 migration 或改變內容。 */
    @Test
    fun `current schema passes through unchanged`() {
        val envelope = envelope(version = 1)

        assertEquals(envelope, PersistenceMigrationRegistry().migrate(envelope))
    }

    /** 驗證舊存檔會依來源版本連續執行所有 migration。 */
    @Test
    fun `older schema migrates sequentially to current version`() {
        val registry = PersistenceMigrationRegistry(
            currentVersion = 3,
            migrations = mapOf(
                1 to PersistenceMigration { state -> buildJsonObject { put("step1", state.getValue("original")) } },
                2 to PersistenceMigration { state -> buildJsonObject { put("step2", state.getValue("step1")) } },
            ),
        )

        val migrated = registry.migrate(
            PersistenceEnvelopeDto(schemaVersion = 1, state = buildJsonObject { put("original", "value") }),
        )

        assertEquals(3, migrated.schemaVersion)
        assertEquals(buildJsonObject { put("step2", "value") }, migrated.state)
    }

    /** 驗證未知的未來 schema version 會明確失敗。 */
    @Test
    fun `future schema is rejected`() {
        assertFailsWith<UnsupportedPersistenceSchemaVersionException> {
            PersistenceMigrationRegistry().migrate(envelope(version = 2))
        }
    }

    /** 驗證非正數 schema version 會明確失敗。 */
    @Test
    fun `non-positive schema is rejected`() {
        assertFailsWith<InvalidPersistenceSchemaVersionException> {
            PersistenceMigrationRegistry().migrate(envelope(version = 0))
        }
    }

    /** 驗證 migration 鏈缺少任一版本時會明確失敗。 */
    @Test
    fun `missing sequential migration is rejected`() {
        val registry = PersistenceMigrationRegistry(
            currentVersion = 3,
            migrations = mapOf(1 to PersistenceMigration { it }),
        )

        assertFailsWith<MissingPersistenceMigrationException> {
            registry.migrate(envelope(version = 1))
        }
    }

    /** 建立只包含測試標記的指定版本 envelope。 */
    private fun envelope(version: Int): PersistenceEnvelopeDto = PersistenceEnvelopeDto(
        schemaVersion = version,
        state = buildJsonObject { put("value", true) },
    )
}
