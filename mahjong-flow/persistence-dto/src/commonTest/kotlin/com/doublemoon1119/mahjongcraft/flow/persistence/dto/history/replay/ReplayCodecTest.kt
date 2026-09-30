package com.doublemoon1119.mahjongcraft.flow.persistence.dto.history.replay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證獨立 replay helper 的無損往返與 malformed-input 防護。 */
class ReplayCodecTest {
    /** 測試使用的 JSON 解析器。 */
    private val json = Json

    /** 字典應保留未知欄位、波浪號字串與長字串。 */
    @Test
    fun `dictionary round trips and rejects bad reference`() {
        val value = json.parseToJsonElement("""{"custom":{"~tag":"repeated-long-value","again":"repeated-long-value"}}""")
        assertEquals(value, CompactReplayDictionary.decode(CompactReplayDictionary.encode(value)))
        assertFailsWith<IllegalArgumentException> {
            CompactReplayDictionary.decode(
                JsonObject(
                    mapOf(
                        ReplayFormatKeys.KEY_DICTIONARY to JsonArray(emptyList()),
                        ReplayFormatKeys.STRING_DICTIONARY to JsonArray(emptyList()),
                        ReplayFormatKeys.DATA to JsonPrimitive("~0"),
                    ),
                ),
            )
        }
    }

    /** 扁平差異應支援替換、列表追加與嚴格長度驗證。 */
    @Test
    fun `flat patch round trips and rejects wrong list length`() {
        val before = json.parseToJsonElement("""{"items":[1,2],"name":"old"}""")
        val patch = JsonObject(
            mapOf(
                ReplayPatchKeys.OBJECT to JsonObject(
                    mapOf(
                        "items" to JsonObject(
                            mapOf(
                                ReplayPatchKeys.SPLICE to JsonArray(
                                    listOf(JsonPrimitive(2), JsonPrimitive(0), JsonArray(listOf(JsonPrimitive(3))), JsonPrimitive(0)),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val paths = linkedMapOf<List<JsonElement>, Int>()
        val encoded = FlatPatchCodec.encode(patch, paths)
        assertEquals(json.parseToJsonElement("""{"items":[1,2,3],"name":"old"}"""), FlatPatchCodec.apply(before, encoded, paths.keys.toList()))
        assertFailsWith<IllegalArgumentException> { FlatPatchCodec.apply(json.parseToJsonElement("""{"items":[1]}"""), encoded, paths.keys.toList()) }
    }

    /** 一般事實與已接受動作應可往返，且未知索引必須失敗。 */
    @Test
    fun `fact round trips and rejects unknown type`() {
        val factTypes = linkedMapOf<String, Int>()
        val actionTypes = linkedMapOf<String, Int>()
        val fact = json.parseToJsonElement("""{"type":"action_accepted","action":{"type":"example:cast","mana":3},"directTiles":[1]}""") as JsonObject
        val encoded = CompactFactCodec.encode(fact, factTypes, actionTypes)
        assertEquals(fact, CompactFactCodec.decode(encoded, factTypes.keys.toList(), actionTypes.keys.toList()))
        assertFailsWith<IllegalArgumentException> { CompactFactCodec.decode(JsonArray(listOf(JsonPrimitive(9), JsonObject(emptyMap()))), factTypes.keys.toList(), actionTypes.keys.toList()) }
    }
}
