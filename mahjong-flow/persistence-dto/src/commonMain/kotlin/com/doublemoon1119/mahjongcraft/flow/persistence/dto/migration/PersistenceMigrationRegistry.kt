package com.doublemoon1119.mahjongcraft.flow.persistence.dto.migration

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceSchema

/**
 * 依 schema version 逐版套用權威存檔 migration 的註冊表。
 *
 * [migrations] 的 key 是來源版本；每個 migration 必須只負責轉換到下一個連續版本。
 *
 * @property currentVersion 目前程式支援的 schema version。
 * @property migrations 以來源 schema version 索引的 migration。
 */
class PersistenceMigrationRegistry(
    private val currentVersion: Int = PersistenceSchema.CURRENT_VERSION,
    private val migrations: Map<Int, PersistenceMigration> = emptyMap(),
) {
    init {
        require(currentVersion >= FIRST_SCHEMA_VERSION) { "Current schema version must be positive" }
        require(migrations.keys.all { it in FIRST_SCHEMA_VERSION until currentVersion }) {
            "Migration source versions must be positive and older than the current schema version"
        }
    }

    /**
     * 將 [envelope] 逐版轉換成 [currentVersion]。
     *
     * @throws InvalidPersistenceSchemaVersionException 若存檔版本小於第一版。
     * @throws UnsupportedPersistenceSchemaVersionException 若存檔來自較新的未知版本。
     * @throws MissingPersistenceMigrationException 若任一連續 migration 尚未註冊。
     */
    fun migrate(envelope: PersistenceEnvelopeDto): PersistenceEnvelopeDto {
        if (envelope.schemaVersion < FIRST_SCHEMA_VERSION) {
            throw InvalidPersistenceSchemaVersionException(envelope.schemaVersion)
        }
        if (envelope.schemaVersion > currentVersion) {
            throw UnsupportedPersistenceSchemaVersionException(envelope.schemaVersion, currentVersion)
        }

        var version = envelope.schemaVersion
        var state = envelope.state
        while (version < currentVersion) {
            val migration = migrations[version] ?: throw MissingPersistenceMigrationException(version, version + 1)
            state = migration.migrate(state)
            version++
        }
        return PersistenceEnvelopeDto(schemaVersion = version, state = state)
    }

    /** 存檔 schema 的第一個有效版本。 */
    private companion object {
        const val FIRST_SCHEMA_VERSION: Int = 1
    }
}

/** 表示存檔宣告了無效的 schema version。 */
class InvalidPersistenceSchemaVersionException(
    version: Int,
) : IllegalArgumentException(
    "Persistence schema version must be positive, but was $version",
)

/** 表示存檔 schema version 比目前程式支援的版本更新。 */
class UnsupportedPersistenceSchemaVersionException(
    version: Int,
    currentVersion: Int,
) : IllegalStateException(
    "Persistence schema version $version is newer than supported version $currentVersion",
)

/** 表示兩個連續 schema version 之間缺少必要的 migration。 */
class MissingPersistenceMigrationException(
    sourceVersion: Int,
    targetVersion: Int,
) : IllegalStateException(
    "Missing persistence migration from schema version $sourceVersion to $targetVersion",
)
