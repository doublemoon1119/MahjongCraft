package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證 Minecraft asset key registry 的註冊、查詢與凍結契約。 */
class MinecraftTileAssetRegistryImplTest {

    /** 驗證已註冊的 asset key 可由穩定 ID 查詢。 */
    @Test
    fun `registered asset key can be resolved by type id`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val typeId = TileTypeId.parse("mahjongcraft:taiwan/spring")

        registry.register(typeId, "flower_spring")

        assertEquals("flower_spring", registry.find(typeId))
    }

    /** 驗證同一穩定 ID 不得重複註冊。 */
    @Test
    fun `duplicate type id is rejected`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val typeId = TileTypeId.parse("example:flower/spring")
        registry.register(typeId, "flower_spring")

        assertFailsWith<IllegalArgumentException> { registry.register(typeId, "another_key") }
    }

    /** 驗證凍結後保持可查詢，但禁止新增映射。 */
    @Test
    fun `frozen registry remains readable and rejects registration`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val registeredId = TileTypeId.parse("example:registered")
        registry.register(registeredId, "registered_key")

        registry.freeze()

        assertTrue(registry.isFrozen)
        assertEquals("registered_key", registry.find(registeredId))
        assertFailsWith<IllegalStateException> {
            registry.register(TileTypeId.parse("example:late"), "late_key")
        }
    }

    /** 驗證未註冊 ID 查詢時回傳 null，而不是拋出例外。 */
    @Test
    fun `unknown type id resolves to null`() {
        val registry = MinecraftTileAssetRegistryImpl()

        assertNull(registry.find(TileTypeId.parse("example:unknown")))
    }
}
