package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 portrait provider 的穩定排序、驗證與凍結契約。 */
class PlayerPortraitSourceRegistryTest {
    /** 固定玩家識別碼與非 AI 身分，供 provider resolve 測試共用。 */
    private val context = PlayerPortraitSourceContext(Uuid.parse("00000000-0000-0000-0000-000000000001"), false)

    /** 優先序較高者勝出，同優先序依 provider ID 排序。 */
    @Test
    fun `higher priority wins and equal priority uses provider ID order`() {
        val registry = PlayerPortraitSourceRegistryImpl()
        registry.register(PlayerPortraitSourceDefinition("test:z", 10, provider = { PlayerPortraitSource.TileFace("z") }))
        registry.register(PlayerPortraitSourceDefinition("test:b", 20, provider = { PlayerPortraitSource.TileFace("b") }))
        registry.register(PlayerPortraitSourceDefinition("test:a", 20, provider = { PlayerPortraitSource.TileFace("a") }))

        registry.freeze()

        assertEquals("test:a", registry.resolve(context)?.providerId)
        assertEquals(PlayerPortraitSource.TileFace("a"), registry.resolve(context)?.source)
    }

    /** 不適用的 provider 會略過並繼續查詢後續候選。 */
    @Test
    fun `skips providers that do not apply`() {
        val registry = PlayerPortraitSourceRegistryImpl()
        registry.register(PlayerPortraitSourceDefinition("test:first", 20, provider = { null }))
        registry.register(PlayerPortraitSourceDefinition("test:second", 10, provider = { PlayerPortraitSource.PlayerSkinFace }))

        assertEquals("test:second", registry.resolve(context)?.providerId)
    }

    /** 重複識別碼與 freeze 後註冊都應失敗。 */
    @Test
    fun `rejects duplicate IDs and registration after freeze`() {
        val registry = PlayerPortraitSourceRegistryImpl()
        registry.register(PlayerPortraitSourceDefinition("test:portrait", provider = { PlayerPortraitSource.PlayerSkinFace }))
        assertFailsWith<IllegalArgumentException> {
            registry.register(PlayerPortraitSourceDefinition("test:portrait", provider = { PlayerPortraitSource.PlayerSkinFace }))
        }
        registry.freeze()
        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> {
            registry.register(PlayerPortraitSourceDefinition("test:late", provider = { PlayerPortraitSource.PlayerSkinFace }))
        }
    }

    /** TextureRegion 要求 namespaced 資源 ID 與正規化 UV 範圍。 */
    @Test
    fun `validates namespaced texture and normalized UV`() {
        assertFailsWith<IllegalArgumentException> { PlayerPortraitSource.TextureRegion("texture", 0f, 0f, 1f, 1f) }
        assertFailsWith<IllegalArgumentException> { PlayerPortraitSource.TextureRegion("test:texture", 0f, 0f, 2f, 1f) }
    }
}
