package com.doublemoon1119.mahjongcraft.flow.persistence.dto.history.replay

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** 驗證精簡 Replay 文件的 envelope 與損壞輸入防護。 */
class CompactReplayCodecTest {
    /** 未知 envelope 欄位不得被靜默忽略。 */
    @Test
    fun `unknown top-level field is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CompactReplayCodec.decodeCompact(
                JsonObject(
                    mapOf(
                        ReplayFormatKeys.FORMAT_VERSION to JsonPrimitive(1),
                        ReplayFormatKeys.PAYLOAD to JsonObject(emptyMap()),
                        "extra" to JsonPrimitive(true),
                    ),
                ),
            )
        }
    }

    /** 未知格式版本不得被當成目前格式解碼。 */
    @Test
    fun `unknown format version is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CompactReplayCodec.decodeCompact(
                JsonObject(mapOf(ReplayFormatKeys.FORMAT_VERSION to JsonPrimitive(2), ReplayFormatKeys.PAYLOAD to JsonObject(emptyMap()))),
            )
        }
    }

    /** 缺少 payload 或交易內容時必須明確失敗。 */
    @Test
    fun `missing replay payload is rejected`() {
        assertFailsWith<IllegalStateException> {
            CompactReplayCodec.decodeCompact(
                JsonObject(mapOf(ReplayFormatKeys.FORMAT_VERSION to JsonPrimitive(1), ReplayFormatKeys.PAYLOAD to JsonArray(emptyList()))),
            )
        }
    }
}
