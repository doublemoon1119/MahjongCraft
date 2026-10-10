package com.doublemoon1119.mahjongcraft.flow.persistence.format.migration

/** 表示兩個連續 schema version 之間缺少必要的 migration。 */
class MissingPersistenceMigrationException(
    sourceVersion: Int,
    targetVersion: Int,
) : IllegalStateException(
    "Missing persistence migration from schema version $sourceVersion to $targetVersion",
)
