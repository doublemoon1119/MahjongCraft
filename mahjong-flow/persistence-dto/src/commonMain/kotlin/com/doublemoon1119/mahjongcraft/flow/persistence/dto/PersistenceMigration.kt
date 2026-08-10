package com.doublemoon1119.mahjongcraft.flow.persistence.dto

import kotlinx.serialization.json.JsonObject

/** 將單一舊版權威狀態轉換成下一個連續 schema version。 */
fun interface PersistenceMigration {
    /**
     * 轉換指定舊版權威狀態。
     *
     * @param state 來源 schema version 的世界級權威狀態。
     * @return 下一個 schema version 的世界級權威狀態。
     */
    fun migrate(state: JsonObject): JsonObject
}
