package com.doublemoon1119.mahjongcraft.flow.persistence.dto.migration

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceEnvelopeDto
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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

    /** 驗證 migration 可在反序列化當前 DTO 前集中改寫 Room 與 Game JSON。 */
    @Test
    fun `migration transforms room and game state before current DTO decoding`() {
        val defaultFlowConfig = buildJsonObject { put("spectatingPolicy", "ENABLED") }
        val registry = PersistenceMigrationRegistry(
            currentVersion = 2,
            migrations = mapOf(
                1 to PersistenceMigration { state ->
                    val migratedRooms = state.getValue("rooms").jsonObject.mapValues { (_, roomElement) ->
                        buildJsonObject {
                            roomElement.jsonObject.forEach(::put)
                            put("flowConfig", defaultFlowConfig)
                        }
                    }
                    val gameFlowConfigs = state.getValue("games").jsonObject.keys.associateWith { defaultFlowConfig }
                    buildJsonObject {
                        state.forEach(::put)
                        put("rooms", buildJsonObject { migratedRooms.forEach(::put) })
                        put("gameFlowConfigs", buildJsonObject { gameFlowConfigs.forEach(::put) })
                    }
                },
            ),
        )
        val oldState = buildJsonObject {
            putJsonObject("rooms") {
                putJsonObject("room-1") { put("config", "rule") }
            }
            putJsonObject("games") {
                putJsonObject("game-1") { put("config", "rule") }
            }
        }

        val migrated = registry.migrate(PersistenceEnvelopeDto(schemaVersion = 1, state = oldState))

        assertEquals(2, migrated.schemaVersion)
        assertEquals(
            defaultFlowConfig,
            migrated.state.getValue("rooms").jsonObject.getValue("room-1").jsonObject["flowConfig"]
        )
        assertEquals(defaultFlowConfig, migrated.state.getValue("gameFlowConfigs").jsonObject["game-1"])
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
