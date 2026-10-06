package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.allTileAssetKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 驗證 `tile` 引數的補全候選來源與過濾規則。 */
class DebugTilePreviewSupportTest {
    /** 預覽牌面依完整清單的順序列出所有牌面，包含第三方登記的牌種，但排除佔位用的 unknown。 */
    @Test
    fun `preview asset keys list every tile except the placeholder`() {
        val registry = MinecraftTileAssetRegistryImpl().apply {
            BundledMinecraftMahjongExtensions.all.forEach { it.registerTileAssets(this) }
            register(TileTypeId.parse("example:cat"), "example_cat")
            freeze()
        }

        val previewKeys = DebugTilePreviewSupport(registry).previewAssetKeys

        assertEquals(registry.allTileAssetKeys().filterNot { it == UNKNOWN_TILE_ASSET_KEY }, previewKeys)
        assertEquals("example_cat", previewKeys.last())
        assertFalse(UNKNOWN_TILE_ASSET_KEY in previewKeys)
    }

    /** 沒有輸入前綴時依原順序列出全部候選。 */
    @Test
    fun `lists every candidate in order without a prefix`() {
        assertEquals(listOf("m1", "example_cat"), buildTileAssetKeySuggestions(remaining = "", assetKeys = listOf("m1", "example_cat")))
    }

    /** 已輸入的前綴只保留相符候選，且大小寫不敏感。 */
    @Test
    fun `filters candidates by the typed prefix ignoring case`() {
        val suggestions = buildTileAssetKeySuggestions(
            remaining = "EX",
            assetKeys = listOf("example_cat", "other_key"),
        )

        assertEquals(listOf("example_cat"), suggestions)
    }
}
