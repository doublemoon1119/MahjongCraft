package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 驗證局況顯示行在同步與保存時使用的參數格式。 */
class RoundInfoLineCodecTest {
    /** 數字、風位、內建與擴充牌及規則文字都能編碼往返，並保留必要欄位。 */
    @Test
    fun `round trip preserves every parameter and extension tiles`() {
        val lines = listOf(
            RoundInfoLine(
                "example:title",
                listOf(
                    RoundInfoArgument.WindValue(Wind.SOUTH),
                    RoundInfoArgument.Number(2),
                    RoundInfoArgument.TileValue(Tile.Honor.East),
                    RoundInfoArgument.TileValue(Tile.Extension(TileTypeId.parse("thirdparty:flower"))),
                    RoundInfoArgument.Message("thirdparty:rule_text"),
                    RoundInfoArgument.Unrecognized,
                ),
            ),
            RoundInfoLine("example:count", listOf(RoundInfoArgument.Number(7))),
        )

        val encoded = RoundInfoLineCodec.encode(lines)

        assertEquals(lines, RoundInfoLineCodec.decode(encoded))
        val encodedArguments = Json.parseToJsonElement(encoded).jsonArray.first().jsonObject.getValue("args").jsonArray
        assertTrue(encodedArguments.all { it.jsonObject.keys == setOf("kind", "value", "tile") })
    }

    /** 整份 JSON 損壞時不回傳不完整的顯示行。 */
    @Test
    fun `malformed JSON returns empty list`() {
        assertTrue(RoundInfoLineCodec.decode("not-json").isEmpty())
    }

    /** 無法辨識或內容無效的參數只替換自己的位置，不影響後面的參數。 */
    @Test
    fun `unknown and invalid arguments preserve line and argument order`() {
        val encoded = """
            [{"key":"example:line","args":[
                {"kind":"number","value":"4","tile":null},
                {"kind":"future","value":"x","tile":null},
                {"kind":"wind","value":"CENTER","tile":null},
                {"kind":"number","value":"9","tile":null}
            ]}]
        """.trimIndent()

        assertEquals(
            listOf(
                RoundInfoLine(
                    "example:line",
                    listOf(
                        RoundInfoArgument.Number(4),
                        RoundInfoArgument.Unrecognized,
                        RoundInfoArgument.Unrecognized,
                        RoundInfoArgument.Number(9),
                    ),
                ),
            ),
            RoundInfoLineCodec.decode(encoded),
        )
    }

    /** 參數缺少必填欄位時保留佔位值，不默默補上預設內容。 */
    @Test
    fun `an argument with a missing required field becomes unrecognized`() {
        val encoded = "[{\"key\":\"example:line\",\"args\":[{\"kind\":\"number\",\"value\":\"4\"}]}]"

        assertEquals(
            listOf(RoundInfoLine("example:line", listOf(RoundInfoArgument.Unrecognized))),
            RoundInfoLineCodec.decode(encoded),
        )
    }

    /** 訊息 ID 必須符合 namespaced 格式，規則直接建立無效值時立即拒絕。 */
    @Test
    fun `message arguments reject malformed ids`() {
        listOf("missing_namespace", "", "UPPER:case", "example:", ":path").forEach { id ->
            assertFailsWith<IllegalArgumentException> { RoundInfoArgument.Message(id) }
        }
    }

    /** 訊息 ID 格式無效時只替換該參數，合法但未登記的 ID 仍保留原值。 */
    @Test
    fun `malformed message ids become placeholders without losing other arguments`() {
        val encoded = """
            [{"key":"example:line","args":[
                {"kind":"number","value":"1","tile":null},
                {"kind":"message","value":"missing_namespace","tile":null},
                {"kind":"message","value":"thirdparty:unregistered","tile":null},
                {"kind":"number","value":"2","tile":null}
            ]}]
        """.trimIndent()

        assertEquals(
            listOf(
                RoundInfoLine(
                    "example:line",
                    listOf(
                        RoundInfoArgument.Number(1),
                        RoundInfoArgument.Unrecognized,
                        RoundInfoArgument.Message("thirdparty:unregistered"),
                        RoundInfoArgument.Number(2),
                    ),
                ),
            ),
            RoundInfoLineCodec.decode(encoded),
        )
    }

    /** 牌資料損壞或缺少牌資料時，只替換該參數，其餘參數仍維持原順序。 */
    @Test
    fun `malformed tile data preserves the surrounding arguments`() {
        val encoded = """
            [{"key":"example:line","args":[
                {"kind":"number","value":"1","tile":null},
                {"kind":"tile","value":null,"tile":{}},
                {"kind":"tile","value":null,"tile":null},
                {"kind":"number","value":"2","tile":null}
            ]}]
        """.trimIndent()

        assertEquals(
            listOf(
                RoundInfoLine(
                    "example:line",
                    listOf(
                        RoundInfoArgument.Number(1),
                        RoundInfoArgument.Unrecognized,
                        RoundInfoArgument.Unrecognized,
                        RoundInfoArgument.Number(2),
                    ),
                ),
            ),
            RoundInfoLineCodec.decode(encoded),
        )
    }
}
