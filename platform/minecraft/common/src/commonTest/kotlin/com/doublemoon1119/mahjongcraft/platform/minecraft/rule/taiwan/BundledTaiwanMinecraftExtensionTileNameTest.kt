package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證台灣麻將的花牌以文字列出時有牌名。 */
class BundledTaiwanMinecraftExtensionTileNameTest {
    /** 八張花牌各有不同的牌名翻譯 key。 */
    @Test
    fun `every flower has its own display name`() {
        val registry = TileDisplayNameRegistryImpl().apply { BundledTaiwanMinecraftExtension.registerTileDisplayNames(this) }

        val keys = TaiwanTileTypes.ALL.map { registry.find(it) }
        assertEquals(TaiwanTileTypes.ALL.size, keys.filterNotNull().distinct().size)
    }
}
