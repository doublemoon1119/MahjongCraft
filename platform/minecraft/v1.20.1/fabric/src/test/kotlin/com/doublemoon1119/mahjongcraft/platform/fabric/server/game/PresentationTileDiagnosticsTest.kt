package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證結算演出缺少牌 entity 時的 log 彙整。 */
class PresentationTileDiagnosticsTest {
    /** 依座位排序列出缺少的張數與 ID，略過沒有缺牌的座位。 */
    @Test
    fun `missing tiles are summarized per seat`() {
        val first = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val second = Uuid.parse("00000000-0000-0000-0000-000000000002")
        val third = Uuid.parse("00000000-0000-0000-0000-000000000003")

        val text = missingPresentationTilesText(mapOf(3 to listOf(third), 0 to emptyList(), 1 to listOf(first, second)))

        assertEquals("seat 1: 2 missing [$first, $second]; seat 3: 1 missing [$third]", text)
    }

    /** 沒有缺少任何牌時不產生文字。 */
    @Test
    fun `no missing tiles produce no text`() {
        assertNull(missingPresentationTilesText(mapOf(0 to emptyList())))
        assertNull(missingPresentationTilesText(emptyMap()))
    }
}
