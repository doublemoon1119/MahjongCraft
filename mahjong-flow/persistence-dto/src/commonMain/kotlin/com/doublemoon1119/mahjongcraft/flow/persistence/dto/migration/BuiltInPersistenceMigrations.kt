package com.doublemoon1119.mahjongcraft.flow.persistence.dto.migration

/**
 * 以來源 schema version 索引的內建存檔 migration。
 *
 * 每個 migration 只負責將該版本轉換到下一個連續版本。
 */
private val builtInPersistenceMigrations: Map<Int, PersistenceMigration> = emptyMap()

/** 建立已註冊所有內建存檔 migration 的 registry。 */
fun buildBuiltInPersistenceMigrationRegistry(): PersistenceMigrationRegistry = PersistenceMigrationRegistry(
    migrations = builtInPersistenceMigrations,
)
