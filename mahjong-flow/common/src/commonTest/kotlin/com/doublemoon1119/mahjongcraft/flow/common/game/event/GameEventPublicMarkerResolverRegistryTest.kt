package com.doublemoon1119.mahjongcraft.flow.common.game.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證公開事件標記 resolver registry 的註冊、查詢與凍結契約。 */
class GameEventPublicMarkerResolverRegistryTest {
    private val resolver = object : GameEventPublicMarkerResolver {}

    @Test
    fun `registration can be found by rule module id`() {
        val registry = GameEventPublicMarkerResolverRegistry()
        registry.register(GameEventPublicMarkerResolverDefinition("example:pairing", resolver))

        assertEquals(resolver, registry.find("example:pairing"))
        assertEquals(setOf("example:pairing"), registry.registrationKeys)
    }

    @Test
    fun `duplicate and invalid registrations are rejected`() {
        val registry = GameEventPublicMarkerResolverRegistry()
        registry.register(GameEventPublicMarkerResolverDefinition("example:pairing", resolver))

        assertFailsWith<IllegalArgumentException> {
            registry.register(GameEventPublicMarkerResolverDefinition("example:pairing", resolver))
        }
        assertFailsWith<IllegalArgumentException> {
            registry.register(GameEventPublicMarkerResolverDefinition("pairing", resolver))
        }
    }

    @Test
    fun `freeze prevents later registration`() {
        val registry = GameEventPublicMarkerResolverRegistry()
        registry.freeze()

        assertTrue(registry.isFrozen)
        assertNull(registry.find("example:pairing"))
        assertFailsWith<IllegalStateException> {
            registry.register(GameEventPublicMarkerResolverDefinition("example:pairing", resolver))
        }
    }
}
