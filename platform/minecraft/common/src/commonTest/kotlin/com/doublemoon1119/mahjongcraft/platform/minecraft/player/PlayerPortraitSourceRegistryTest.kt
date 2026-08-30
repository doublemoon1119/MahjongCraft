package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 portrait provider 的穩定排序、驗證與凍結契約。 */
class PlayerPortraitSourceRegistryTest {
    private val context = PlayerPortraitSourceContext(Uuid.parse("00000000-0000-0000-0000-000000000001"), false)

    @Test
    fun `higher priority wins and equal priority uses provider ID order`() {
        val registry = PlayerPortraitSourceRegistryImpl()
        registry.register("test:z", 10) { PlayerPortraitSource.TileFace("z") }
        registry.register("test:b", 20) { PlayerPortraitSource.TileFace("b") }
        registry.register("test:a", 20) { PlayerPortraitSource.TileFace("a") }

        registry.freeze()

        assertEquals("test:a", registry.resolve(context)?.providerId)
        assertEquals(PlayerPortraitSource.TileFace("a"), registry.resolve(context)?.source)
    }

    @Test
    fun `skips providers that do not apply`() {
        val registry = PlayerPortraitSourceRegistryImpl()
        registry.register("test:first", 20) { null }
        registry.register("test:second", 10) { PlayerPortraitSource.PlayerSkinFace }

        assertEquals("test:second", registry.resolve(context)?.providerId)
    }

    @Test
    fun `rejects duplicate IDs and registration after freeze`() {
        val registry = PlayerPortraitSourceRegistryImpl()
        registry.register("test:portrait") { PlayerPortraitSource.PlayerSkinFace }
        assertFailsWith<IllegalArgumentException> {
            registry.register("test:portrait") { PlayerPortraitSource.PlayerSkinFace }
        }
        registry.freeze()
        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> {
            registry.register("test:late") { PlayerPortraitSource.PlayerSkinFace }
        }
    }

    @Test
    fun `validates namespaced texture and normalized UV`() {
        assertFailsWith<IllegalArgumentException> { PlayerPortraitSource.TextureRegion("texture", 0f, 0f, 1f, 1f) }
        assertFailsWith<IllegalArgumentException> { PlayerPortraitSource.TextureRegion("test:texture", 0f, 0f, 2f, 1f) }
    }
}
