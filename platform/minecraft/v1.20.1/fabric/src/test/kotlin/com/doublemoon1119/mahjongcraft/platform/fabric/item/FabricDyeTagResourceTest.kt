package com.doublemoon1119.mahjongcraft.platform.fabric.item

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 驗證 Fabric 1.20.1 將共用染料 fallback 與 Conventional Tags v1 合併。 */
class FabricDyeTagResourceTest {
    /** 驗證麻將牌配方使用的穩定 tag 同時接受 Vanilla fallback 與 Fabric `c:dye`。 */
    @Test
    fun `fabric dye tag includes vanilla fallback and conventional dyes`() {
        val resourcePath = "/data/mahjongcraft/tags/items/dyes.json"
        val content = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Resource not found: $resourcePath"
        }.bufferedReader().use { it.readText() }
        val values = assertIs<JsonArray>(Json.parseToJsonElement(content).jsonObject["values"])
            .map { it.jsonPrimitive.content }

        assertEquals(listOf("#mahjongcraft:vanilla_dyes", "#c:dyes"), values)
    }
}
