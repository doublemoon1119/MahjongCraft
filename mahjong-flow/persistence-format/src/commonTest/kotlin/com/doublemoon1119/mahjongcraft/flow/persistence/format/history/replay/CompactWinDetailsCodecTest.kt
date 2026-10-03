package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證和牌明細精簡格式的各種值型別、錯誤輸入與第三方事實隔離。 */
class CompactWinDetailsCodecTest {
    /** 測試用 JSON 解析器。 */
    private val json = Json

    /** 文字、牌參照與條目明細應保留多位贏家及所有尾綴形式。 */
    @Test
    fun `all detail variants round trip for multiple winners`() {
        val details = json.parseToJsonElement(
            """
            [
              {
                "playerId": 0,
                "templateKey": "test:template",
                "detailFields": [
                  {"id": "test:text", "value": {"type": "text", "translationKey": "test:label", "arguments": ["a", "b"]}},
                  {"id": "test:tiles", "value": {"type": "tiles", "tileIds": [1, 2]}},
                  {"id": "test:entries", "value": {"type": "entries", "entries": [
                    {"translationKey": "test:raw", "trailingText": "", "trailingTranslationKey": null, "trailingTranslationArgument": null},
                    {"translationKey": "test:text_suffix", "trailingText": " points", "trailingTranslationKey": null, "trailingTranslationArgument": null},
                    {"translationKey": "test:translated_suffix", "trailingText": "", "trailingTranslationKey": "test:suffix", "trailingTranslationArgument": "7"}
                  ]}}
                ]
              },
              {
                "playerId": 1,
                "templateKey": "test:other_template",
                "detailFields": []
              }
            ]
            """.trimIndent(),
        )

        assertEquals(details, CompactWinDetailsCodec.decode(CompactWinDetailsCodec.encode(details)))
    }

    /** 空白參數與尾綴欄位應使用固定預設值往返。 */
    @Test
    fun `empty defaults round trip`() {
        val details = json.parseToJsonElement(
            """
            [{"playerId":0,"templateKey":"test:template","detailFields":[
              {"id":"test:text","value":{"type":"text","translationKey":"test:label","arguments":[]}},
              {"id":"test:entries","value":{"type":"entries","entries":[
                {"translationKey":"test:entry","trailingText":"","trailingTranslationKey":null,"trailingTranslationArgument":null}
              ]}}
            ]}]
            """.trimIndent(),
        )

        assertEquals(details, CompactWinDetailsCodec.decode(CompactWinDetailsCodec.encode(details)))
    }

    /** 固定代碼或欄位長度錯誤時應拒絕精簡資料。 */
    @Test
    fun `corrupt codes and lengths are rejected`() {
        val corruptCode = JsonArray(listOf(JsonPrimitive(99), JsonPrimitive(2), JsonArray(emptyList())))
        assertFailsWith<IllegalArgumentException> {
            CompactWinDetailsCodec.decode(JsonArray(listOf(JsonArray(listOf(JsonPrimitive(0), JsonPrimitive("test:template"), JsonArray(listOf(JsonArray(listOf(JsonPrimitive("test:field"), corruptCode)))))))))
        }

        val wrongWinnerLength = JsonArray(listOf(JsonArray(listOf(JsonPrimitive(0), JsonPrimitive("test:template")))))
        assertFailsWith<IllegalArgumentException> { CompactWinDetailsCodec.decode(wrongWinnerLength) }

        val wrongFieldLength = JsonArray(listOf(JsonArray(listOf(JsonPrimitive(0), JsonPrimitive("test:template"), JsonArray(listOf(JsonArray(listOf(JsonPrimitive("test:field")))))))))
        assertFailsWith<IllegalArgumentException> { CompactWinDetailsCodec.decode(wrongFieldLength) }
    }

    /** 非內建事實即使含有 winDetails 欄位，也不得套用和牌明細精簡格式。 */
    @Test
    fun `third party fact win details remain untouched`() {
        val fact = json.parseToJsonElement(
            """
            {"type":"custom:fact","winDetails":[{"playerId":0,"templateKey":"test:template","detailFields":[]}]}
            """.trimIndent(),
        ) as JsonObject
        val factTypes = linkedMapOf<String, Int>()
        val actionTypes = linkedMapOf<String, Int>()

        val encoded = CompactFactCodec.encode(fact, factTypes, actionTypes)
        val restored = CompactFactCodec.decode(encoded, factTypes.keys.toList(), actionTypes.keys.toList())

        assertEquals(fact, restored)
        assertEquals(fact[CompactWinDetailsCodec.WIN_DETAILS], encoded[1].let { (it as JsonObject)[CompactWinDetailsCodec.WIN_DETAILS] })
    }
}
