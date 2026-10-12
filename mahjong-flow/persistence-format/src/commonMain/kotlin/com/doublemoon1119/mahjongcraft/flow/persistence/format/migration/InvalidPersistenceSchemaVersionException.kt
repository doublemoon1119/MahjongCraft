package com.doublemoon1119.mahjongcraft.flow.persistence.format.migration

/**
 * 表示存檔宣告了無效的 schema version。
 *
 * @param version 存檔宣告的版本。
 */
class InvalidPersistenceSchemaVersionException(
    version: Int,
) : IllegalArgumentException(
    "Persistence schema version must be positive, but was $version",
)
