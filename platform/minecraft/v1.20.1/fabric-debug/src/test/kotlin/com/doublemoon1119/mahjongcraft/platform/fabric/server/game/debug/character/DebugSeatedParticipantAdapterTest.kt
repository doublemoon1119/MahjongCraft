package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.character

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 debug 入座測試角色只聽從建立它的房主。 */
class DebugSeatedParticipantAdapterTest {
    /** 最小 adapter 不因在附近而允許加入其他房主的房間。 */
    @Test
    fun `debug participant grants permission only to its recorded host`() {
        val host = Uuid.random()
        val tags = setOf(DebugSeatedParticipantAdapter.TAG, "${DebugSeatedParticipantAdapter.OWNER_TAG_PREFIX}$host")
        assertTrue(DebugSeatedParticipantAdapter.authorized(tags, host))
        assertFalse(DebugSeatedParticipantAdapter.authorized(tags, Uuid.random()))
        assertFalse(DebugSeatedParticipantAdapter.authorized(emptySet(), host))
    }
}
