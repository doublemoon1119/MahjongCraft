package com.doublemoon1119.mahjongcraft.flow.persistence.format.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** [persistenceJson] 決定已寫入存檔格式的設定測試。 */
class PersistenceJsonTest {
    /** 設定維持序列化函式庫的預設值；改動任何一項都會改變已寫入資料的格式。 */
    @Test
    fun `settings stay at serialization defaults`() {
        assertEquals(Json.Default.configuration.toString(), persistenceJson.configuration.toString())
    }
}
