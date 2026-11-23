package com.doublemoon1119.mahjongcraft.platform.forge.item

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 驗證 Forge 1.20.1 將共用染料 fallback 與 Forge 的通用染料 tag 合併。 */
class ForgeDyeTagResourceTest {
    /** 麻將牌配方使用的穩定 tag 同時接受原版染料與 Forge 的 `forge:dyes`，其他 mod 的染料也能合成。 */
    @Test
    fun `forge dye tag includes vanilla fallback and forge dyes`() {
        val resourcePath = "/data/mahjongcraft/tags/items/dyes.json"
        val content = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Resource not found: $resourcePath"
        }.bufferedReader().use { it.readText() }
        val values = assertIs<JsonArray>(Json.parseToJsonElement(content).jsonObject["values"])
            .map { it.jsonPrimitive.content }

        assertEquals(listOf("#mahjongcraft:vanilla_dyes", "#forge:dyes"), values)
    }
}
