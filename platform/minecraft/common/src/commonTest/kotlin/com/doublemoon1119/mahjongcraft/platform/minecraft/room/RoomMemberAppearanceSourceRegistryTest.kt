package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 驗證房間成員外觀來源的優先序、內建 fallback 與凍結行為。 */
class RoomMemberAppearanceSourceRegistryTest {
    /** 固定 AI 成員情境，供解析測試共用。 */
    private val context = RoomMemberAppearanceContext(Uuid.parse("00000000-0000-0000-0000-000000000001"), true)

    /** 優先序較高者勝出，同優先序依 provider ID 排序。 */
    @Test
    fun `higher priority wins and equal priority uses provider ID order`() {
        val registry = RoomMemberAppearanceSourceRegistryImpl()
        registry.register(RoomMemberAppearanceSourceDefinition("test:z", 10, provider = { RoomMemberAppearanceSource.Portrait }))
        registry.register(RoomMemberAppearanceSourceDefinition("test:b", 20, provider = { RoomMemberAppearanceSource.ActorPreview("test:actor_b") }))
        registry.register(RoomMemberAppearanceSourceDefinition("test:a", 20, provider = { RoomMemberAppearanceSource.ActorPreview("test:actor_a") }))

        assertEquals(RoomMemberAppearanceSource.ActorPreview("test:actor_a"), registry.resolve(context))
    }

    /** 沒有自訂來源時依 AI 身分選擇內建外觀。 */
    @Test
    fun `built-in fallback distinguishes humans and AI`() {
        val registry = RoomMemberAppearanceSourceRegistryImpl()

        assertEquals(RoomMemberAppearanceSource.Portrait, registry.resolve(context))
        assertEquals(RoomMemberAppearanceSource.PlayerModel, registry.resolve(context.copy(isAi = false)))
    }

    /** 重複 provider 與凍結後註冊都應失敗。 */
    @Test
    fun `duplicate providers and registration after freeze fail`() {
        val registry = RoomMemberAppearanceSourceRegistryImpl()
        registry.register(RoomMemberAppearanceSourceDefinition("test:provider", 1, provider = { null }))
        assertFailsWith<IllegalArgumentException> { registry.register(RoomMemberAppearanceSourceDefinition("test:provider", 2, provider = { null })) }
        registry.freeze()
        assertFailsWith<IllegalStateException> { registry.register(RoomMemberAppearanceSourceDefinition("test:late", 1, provider = { null })) }
    }
}
