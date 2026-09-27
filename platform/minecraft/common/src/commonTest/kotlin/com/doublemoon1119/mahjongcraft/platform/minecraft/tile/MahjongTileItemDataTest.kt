package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.test.Test
import kotlin.test.assertEquals

/** 麻將牌物品資料的預設與正規化測試。 */
class MahjongTileItemDataTest {
    /** 測試用的內建牌面 registry。 */
    private val registry: MinecraftTileAssetRegistry = MinecraftTileAssetRegistryImpl().apply {
        registerBuiltInTileAssets()
        freeze()
    }

    /** 缺失牌面使用配方預設牌，錯誤名稱使用未知牌面。 */
    @Test
    fun `missing and invalid item tile keys use distinct fallbacks`() {
        assertEquals(ALL_TILE_ASSET_KEYS.first(), MahjongTileItemData.read(null, registry))
        assertEquals(UNKNOWN_TILE_ASSET_KEY, MahjongTileItemData.read("invalid", registry))
    }

    /** 已註冊牌面寫入後可保持相同名稱。 */
    @Test
    fun `registered tile key survives item data normalization`() {
        assertEquals("m5_red", MahjongTileItemData.write("m5_red", registry))
        assertEquals("m5_red", MahjongTileItemData.read("m5_red", registry))
    }
}
