package com.doublemoon1119.mahjongcraft.platform.forge.item

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 驗證 Forge 1.20.1 的染料標籤指向 Forge 的通用染料標籤。 */
class ForgeDyeTagResourceTest {
    /** 麻將牌配方使用的染料標籤只引用 Forge 的 `forge:dyes`；它已包含原版染料，其他 mod 的染料也能合成。 */
    @Test
    fun `forge dye tag uses the forge dyes tag`() {
        val resourcePath = "/data/mahjongcraft/tags/items/dyes.json"
        val content = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Resource not found: $resourcePath"
        }.bufferedReader().use { it.readText() }
        val values = assertIs<JsonArray>(Json.parseToJsonElement(content).jsonObject["values"])
            .map { it.jsonPrimitive.content }

        assertEquals(listOf("#forge:dyes"), values)
    }
}
