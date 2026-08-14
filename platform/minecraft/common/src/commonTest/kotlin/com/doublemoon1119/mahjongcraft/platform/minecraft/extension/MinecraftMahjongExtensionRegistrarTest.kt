package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證第三方 Minecraft extension 的統一註冊順序、錯誤診斷與 registry freeze。 */
class MinecraftMahjongExtensionRegistrarTest {

    /** 驗證內建映射先完成註冊，第三方映射接續登記，完成後禁止新增映射。 */
    @Test
    fun `extension registers tile assets after built-in mappings and before freeze`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val thirdPartyId = TileTypeId.parse("example:animal/cat")
        val extension = object : MinecraftMahjongExtension {
            override val id: String = "example:recording"

            override fun registerTileAssets(registry: MinecraftTileAssetRegistry) {
                registry.register(thirdPartyId, "animal_cat")
            }
        }

        MinecraftMahjongExtensionRegistrar.registerAndFreeze(listOf(extension), registry)

        assertEquals("m5_red", registry.find(RiichiTileTypes.RED_FIVE_CHARACTER))
        assertEquals("animal_cat", registry.find(thirdPartyId))
        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> {
            registry.register(TileTypeId.parse("example:late"), "late_key")
        }
    }

    /** 驗證未實作 [MinecraftMahjongExtension] 的清單一樣能完成內建映射與凍結。 */
    @Test
    fun `no third-party extensions still registers built-ins and freezes`() {
        val registry = MinecraftTileAssetRegistryImpl()

        MinecraftMahjongExtensionRegistrar.registerAndFreeze(emptyList(), registry)

        assertEquals("flower_spring", registry.find(TaiwanTileTypes.SPRING))
        assertTrue(registry.isFrozen)
        assertNull(registry.find(TileTypeId.parse("example:unregistered")))
    }

    /** 驗證註冊失敗時的例外會指出第三方 extension ID。 */
    @Test
    fun `registration failure identifies extension`() {
        val extension = object : MinecraftMahjongExtension {
            override val id: String = "example:broken"

            override fun registerTileAssets(registry: MinecraftTileAssetRegistry) {
                error("broken")
            }
        }

        val error = assertFailsWith<MinecraftMahjongExtensionRegistrationException> {
            MinecraftMahjongExtensionRegistrar.registerAndFreeze(listOf(extension), MinecraftTileAssetRegistryImpl())
        }

        assertTrue(error.message.orEmpty().contains("example:broken"))
    }

    /** 驗證相同 extension ID 不會形成無法判斷來源的部分註冊結果。 */
    @Test
    fun `duplicate extension id fails registration`() {
        val extension = object : MinecraftMahjongExtension {
            override val id: String = "example:duplicate"
        }

        val error = assertFailsWith<MinecraftMahjongExtensionRegistrationException> {
            MinecraftMahjongExtensionRegistrar.registerAndFreeze(
                listOf(extension, extension),
                MinecraftTileAssetRegistryImpl(),
            )
        }

        assertTrue(error.message.orEmpty().contains(extension.id))
        assertTrue(error.cause?.message.orEmpty().contains("Duplicate"))
    }
}
