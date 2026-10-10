package com.doublemoon1119.mahjongcraft.flow.persistence.format.migration

/** 表示存檔 schema version 比目前程式支援的版本更新。 */
class UnsupportedPersistenceSchemaVersionException(
    version: Int,
    currentVersion: Int,
) : IllegalStateException(
    "Persistence schema version $version is newer than supported version $currentVersion",
)
