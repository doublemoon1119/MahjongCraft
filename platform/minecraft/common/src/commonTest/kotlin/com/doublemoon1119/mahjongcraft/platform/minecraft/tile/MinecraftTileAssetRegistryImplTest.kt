package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

    /** 驗證不同穩定 ID 不得共用同一個 asset key，避免正規化與 Tab 補全出現無法區分的撞名。 */
    @Test
    fun `duplicate asset key across different type ids is rejected`() {
        val registry = MinecraftTileAssetRegistryImpl()
        registry.register(TileTypeId.parse("example:animal/cat"), "shared_key")

        assertFailsWith<IllegalArgumentException> {
            registry.register(TileTypeId.parse("example:animal/dog"), "shared_key")
        }
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

    /** 驗證 [MinecraftTileAssetRegistry.isRegisteredAssetKey] 只認得實際註冊過的 asset key 值。 */
    @Test
    fun `isRegisteredAssetKey only recognizes values that were actually registered`() {
        val registry = MinecraftTileAssetRegistryImpl()
        registry.register(TileTypeId.parse("example:animal/cat"), "animal_cat")

        assertTrue(registry.isRegisteredAssetKey("animal_cat"))
        assertFalse(registry.isRegisteredAssetKey("animal_dog"))
    }
}
