package com.doublemoon1119.mahjongcraft.platform.fabric.client.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證普通名稱快取與連線清理，不觸發原生皮膚服務。 */
class ClientPlayerProfileResolverTest {
    /** 收到授權名稱後可讀取 profile，清除連線後不得保留。 */
    @Test
    fun `server names are cleared between sessions`() {
        val resolver = ClientPlayerProfileResolver()
        val id = Uuid.random()
        resolver.rememberServerName(id, "PlayerOne")
        assertEquals("PlayerOne", resolver.resolvedProfile(id)?.name)
        resolver.clearSession()
        assertNull(resolver.resolvedProfile(id))
    }

    /** 無效普通名稱不得覆寫已知身分。 */
    @Test
    fun `invalid names do not replace known names`() {
        val resolver = ClientPlayerProfileResolver()
        val id = Uuid.random()
        resolver.rememberServerName(id, "PlayerOne")
        resolver.rememberServerName(id, " ")
        resolver.rememberServerName(id, "x".repeat(17))
        assertEquals("PlayerOne", resolver.resolvedProfile(id)?.name)
    }

    /** 名稱快取超過上限時移除最久未使用的項目。 */
    @Test
    fun `profile cache evicts least recently used names`() {
        val resolver = ClientPlayerProfileResolver()
        val ids = List(257) { Uuid.random() }
        ids.take(256).forEach { resolver.rememberServerName(it, "Player") }
        assertEquals("Player", resolver.resolvedProfile(ids.first())?.name)
        resolver.rememberServerName(ids.last(), "Player")
        assertNull(resolver.resolvedProfile(ids[1]))
        assertEquals("Player", resolver.resolvedProfile(ids.first())?.name)
    }
}
