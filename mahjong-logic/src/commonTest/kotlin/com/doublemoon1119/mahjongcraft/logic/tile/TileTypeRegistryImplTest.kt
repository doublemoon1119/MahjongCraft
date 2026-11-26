package com.doublemoon1119.mahjongcraft.logic.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證擴充牌種 registry 的註冊、查詢與凍結契約。 */
class TileTypeRegistryImplTest {
    /** 驗證已註冊定義可由穩定 ID 查詢並建立擴充牌。 */
    @Test
    fun `registered tile type can be resolved by extension tile`() {
        val registry = TileTypeRegistryImpl()
        val definition = TileTypeDefinition(TileTypeId.parse("mahjongcraft:taiwan/spring"))
        val tile = Tile.Extension(definition.id)

        registry.register(definition)

        assertEquals(definition, registry.find(tile.typeId))
        assertEquals(definition, registry.require(tile.typeId))
        assertEquals(listOf(definition), registry.getAll())
    }

    /** 驗證同一完整 ID 不得重複註冊。 */
    @Test
    fun `duplicate tile type id is rejected`() {
        val registry = TileTypeRegistryImpl()
        val definition = TileTypeDefinition(TileTypeId.parse("example:flower/spring"))
        registry.register(definition)

        assertFailsWith<IllegalArgumentException> { registry.register(definition) }
    }

    /** 驗證不同 namespace 可安全使用相同 path。 */
    @Test
    fun `different namespaces can share a path`() {
        val registry = TileTypeRegistryImpl()
        registry.register(TileTypeDefinition(TileTypeId.parse("first:flower/spring")))
        registry.register(TileTypeDefinition(TileTypeId.parse("second:flower/spring")))

        assertEquals(2, registry.getAll().size)
    }

    /** 驗證凍結後保持可查詢，但禁止新增定義。 */
    @Test
    fun `frozen registry remains readable and rejects registration`() {
        val registry = TileTypeRegistryImpl()
        val registeredId = TileTypeId.parse("example:registered")
        registry.register(TileTypeDefinition(registeredId))

        registry.freeze()

        assertTrue(registry.isFrozen)
        assertEquals(registeredId, registry.require(registeredId).id)
        assertFailsWith<IllegalStateException> {
            registry.register(TileTypeDefinition(TileTypeId.parse("example:late")))
        }
    }

    /** 驗證未註冊 ID 的可選與必要查詢具有不同失敗語意。 */
    @Test
    fun `unknown tile type has explicit lookup behavior`() {
        val registry = TileTypeRegistryImpl()
        val unknownId = TileTypeId.parse("example:unknown")

        assertNull(registry.find(unknownId))
        assertFailsWith<IllegalStateException> { registry.require(unknownId) }
    }
}
