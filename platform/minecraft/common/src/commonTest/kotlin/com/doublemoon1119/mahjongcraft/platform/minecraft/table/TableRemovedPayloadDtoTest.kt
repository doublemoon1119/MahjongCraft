package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證桌子移除通知的最小線路契約。 */
class TableRemovedPayloadDtoTest {
    /** 桌子 UUID 會被保留並可在相同 DTO serializer 中往返。 */
    @Test
    fun `table id survives serialization round trip`() {
        val payload = TableRemovedPayloadDto("00000000-0000-0000-0000-000000000001")
        val encoded = Json.encodeToString(TableRemovedPayloadDto.serializer(), payload)

        assertEquals(payload, Json.decodeFromString(TableRemovedPayloadDto.serializer(), encoded))
    }
}
