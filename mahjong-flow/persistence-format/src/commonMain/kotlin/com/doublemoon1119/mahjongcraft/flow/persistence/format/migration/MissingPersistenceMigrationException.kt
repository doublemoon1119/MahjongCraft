package com.doublemoon1119.mahjongcraft.flow.persistence.format.migration

/**
 * 表示兩個連續 schema version 之間缺少必要的 migration。
 *
 * @param sourceVersion 缺少轉換的起始版本。
 * @param targetVersion 預期轉換到的下一個版本。
 */
class MissingPersistenceMigrationException(
    sourceVersion: Int,
    targetVersion: Int,
) : IllegalStateException(
    "Missing persistence migration from schema version $sourceVersion to $targetVersion",
)
