package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class RoomMemberAppearanceSourceRegistryTest {
    private val context = RoomMemberAppearanceContext(Uuid.parse("00000000-0000-0000-0000-000000000001"), true)

    @Test
    fun `higher priority wins and equal priority uses provider ID order`() {
        val registry = RoomMemberAppearanceSourceRegistryImpl()
        registry.register("test:z", 10) { RoomMemberAppearanceSource.Portrait }
        registry.register("test:b", 20) { RoomMemberAppearanceSource.ActorPreview("test:actor_b") }
        registry.register("test:a", 20) { RoomMemberAppearanceSource.ActorPreview("test:actor_a") }

        assertEquals(RoomMemberAppearanceSource.ActorPreview("test:actor_a"), registry.resolve(context))
    }

    @Test
    fun `built-in fallback distinguishes humans and AI`() {
        val registry = RoomMemberAppearanceSourceRegistryImpl()

        assertEquals(RoomMemberAppearanceSource.Portrait, registry.resolve(context))
        assertEquals(RoomMemberAppearanceSource.PlayerModel, registry.resolve(context.copy(isAi = false)))
    }

    @Test
    fun `duplicate providers and registration after freeze fail`() {
        val registry = RoomMemberAppearanceSourceRegistryImpl()
        registry.register("test:provider", 1) { null }
        assertFailsWith<IllegalArgumentException> { registry.register("test:provider", 2) { null } }
        registry.freeze()
        assertFailsWith<IllegalStateException> { registry.register("test:late", 1) { null } }
    }
}
