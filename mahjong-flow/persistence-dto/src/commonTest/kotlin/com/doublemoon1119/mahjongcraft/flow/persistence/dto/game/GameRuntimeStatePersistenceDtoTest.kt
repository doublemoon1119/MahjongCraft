package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [GameRuntimeStatePersistenceDto] 的向後相容解碼測試。 */
class GameRuntimeStatePersistenceDtoTest {
    /** 舊存檔缺少本局自動操作欄位時必須還原為空 map。 */
    @Test
    fun `missing round automatic controls decode as empty`() {
        val decoded = Json.decodeFromString<GameRuntimeStatePersistenceDto>(
            """{"remainingReserveMillisByPlayerId":{}}""",
        )

        assertTrue(decoded.enabledAutomaticControlIdsByPlayerId.isEmpty())
        assertEquals(0L, decoded.automaticControlRevision)
    }
}
