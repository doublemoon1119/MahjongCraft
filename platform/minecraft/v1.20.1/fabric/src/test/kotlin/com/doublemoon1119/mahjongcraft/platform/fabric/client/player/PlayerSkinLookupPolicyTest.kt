package com.doublemoon1119.mahjongcraft.platform.fabric.client.player

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/** 驗證 offline-mode 與未知識別碼不進入原生外部皮膚解析。 */
class PlayerSkinLookupPolicyTest {
    /** 標準 offline-mode 名稱衍生 UUID 必須維持預設外觀。 */
    @Test
    fun `test offline mode and unknown uuid versions skip lookup`() {
        val offline = UUID.nameUUIDFromBytes("OfflinePlayer:ExamplePlayer".toByteArray(Charsets.UTF_8)).toKotlinUuid()
        assertFalse(supportsNativePlayerSkinLookup(offline))
        assertFalse(supportsNativePlayerSkinLookup(Uuid.NIL))
    }

    /** 正版帳號格式的 UUID 可使用原生解析，是否線上不影響其身分。 */
    @Test
    fun `test online account uuid permits native lookup`() {
        assertTrue(supportsNativePlayerSkinLookup(Uuid.random()))
    }
}
