package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證和牌明細精簡格式的各種值型別、錯誤輸入與第三方事實隔離。 */
class CompactWinDetailsCodecTest {
    /** 測試用 JSON 解析器。 */
    private val json = Json

    /** 數值、牌參照與條目明細應保留多位贏家、有無數值的條目與空明細。 */
    @Test
    fun `all detail variants round trip for multiple winners`() {
        val details = json.parseToJsonElement(
            """
            [
              {
                "playerId": 0,
                "detailFields": [
                  {"id": "test:quantities", "value": {"type": "quantities", "quantities": [{"unitId": "test:han", "amount": 3}, {"unitId": "test:fu", "amount": 30}]}},
                  {"id": "test:tiles", "value": {"type": "tiles", "tileIds": [1, 2]}},
                  {"id": "test:entries", "value": {"type": "entries", "entries": [
                    {"id": "test:plain"},
                    {"id": "test:counted", "quantity": {"unitId": "test:han", "amount": 2}}
                  ]}}
                ]
              },
              {
                "playerId": 1,
                "detailFields": []
              }
            ]
            """.trimIndent(),
        )

        assertEquals(details, CompactWinDetailsCodec.decode(CompactWinDetailsCodec.encode(details)))
    }

    /** 空條目清單應原樣往返。 */
    @Test
    fun `empty entries round trip`() {
        val details = json.parseToJsonElement(
            """
            [{"playerId":0,"detailFields":[
              {"id":"test:entries","value":{"type":"entries","entries":[]}}
            ]}]
            """.trimIndent(),
        )

        assertEquals(details, CompactWinDetailsCodec.decode(CompactWinDetailsCodec.encode(details)))
    }

    /** 可選胡牌手牌描述應保留立牌順序、和牌張與特殊結算的 null。 */
    @Test
    fun `winning hand descriptor round trips`() {
        val details = json.parseToJsonElement(
            """
            [{"playerId":0,"detailFields":[],
              "hand":{"standingTileIds":[2,1],"winningTileId":3}},
             {"playerId":1,"detailFields":[],
              "hand":{"standingTileIds":[4],"winningTileId":null}}]
            """.trimIndent(),
        )
        assertEquals(details, CompactWinDetailsCodec.decode(CompactWinDetailsCodec.encode(details)))
    }

    /** 固定代碼或欄位長度錯誤時應拒絕精簡資料。 */
    @Test
    fun `corrupt codes and lengths are rejected`() {
        val corruptCode = JsonArray(listOf(JsonPrimitive(99), JsonArray(emptyList())))
        assertFailsWith<IllegalArgumentException> {
            CompactWinDetailsCodec.decode(JsonArray(listOf(JsonArray(listOf(JsonPrimitive(0), JsonArray(listOf(JsonArray(listOf(JsonPrimitive("test:field"), corruptCode)))))))))
        }

        val wrongWinnerLength = JsonArray(listOf(JsonArray(listOf(JsonPrimitive(0)))))
        assertFailsWith<IllegalArgumentException> { CompactWinDetailsCodec.decode(wrongWinnerLength) }

        val wrongFieldLength = JsonArray(listOf(JsonArray(listOf(JsonPrimitive(0), JsonArray(listOf(JsonArray(listOf(JsonPrimitive("test:field")))))))))
        assertFailsWith<IllegalArgumentException> { CompactWinDetailsCodec.decode(wrongFieldLength) }

        val wrongQuantityLength = json.parseToJsonElement("""[[0,[["test:field",[0,[["test:han"]]]]]]]""")
        assertFailsWith<IllegalArgumentException> { CompactWinDetailsCodec.decode(wrongQuantityLength) }

        val wrongEntryLength = json.parseToJsonElement("""[[0,[["test:field",[2,[["test:entry","test:han"]]]]]]]""")
        assertFailsWith<IllegalArgumentException> { CompactWinDetailsCodec.decode(wrongEntryLength) }

        val duplicateStanding = JsonArray(listOf(JsonArray(listOf(JsonPrimitive(0), JsonArray(emptyList()), JsonArray(listOf(JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(1))), JsonNull))))))
        assertFailsWith<IllegalArgumentException> { CompactWinDetailsCodec.decode(duplicateStanding) }

        val winningInStanding = JsonArray(listOf(JsonArray(listOf(JsonPrimitive(0), JsonArray(emptyList()), JsonArray(listOf(JsonArray(listOf(JsonPrimitive(1))), JsonPrimitive(1)))))))
        assertFailsWith<IllegalArgumentException> { CompactWinDetailsCodec.decode(winningInStanding) }
    }

    /** 非內建事實即使含有 winDetails 欄位，也不得套用和牌明細精簡格式。 */
    @Test
    fun `third party fact win details remain untouched`() {
        val fact = json.parseToJsonElement(
            """
            {"type":"custom:fact","winDetails":[{"playerId":0,"detailFields":[]}]}
            """.trimIndent(),
        ) as JsonObject
        val factTypes = linkedMapOf<String, Int>()
        val actionTypes = linkedMapOf<String, Int>()

        val encoded = CompactFactCodec.encode(fact, factTypes, actionTypes)
        val restored = CompactFactCodec.decode(encoded, factTypes.keys.toList(), actionTypes.keys.toList())

        assertEquals(fact, restored)
        assertEquals(fact[ReplaySourceKeys.WIN_DETAILS], encoded[1].let { (it as JsonObject)[ReplaySourceKeys.WIN_DETAILS] })
    }
}
