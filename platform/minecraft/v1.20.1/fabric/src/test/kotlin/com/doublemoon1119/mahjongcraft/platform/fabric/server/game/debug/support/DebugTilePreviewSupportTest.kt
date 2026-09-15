package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_TILE_ASSET_KEYS
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證 `tile` 引數的補全候選來源與過濾規則。 */
class DebugTilePreviewSupportTest {
    /** 沒有輸入前綴時列出全部內建牌面，但排除佔位用的 unknown。 */
    @Test
    fun `lists built-in asset keys without the placeholder`() {
        val suggestions = buildTileAssetKeySuggestions(remaining = "", registeredAssetKeys = emptySet())

        assertEquals(ALL_TILE_ASSET_KEYS.filterNot { it == UNKNOWN_TILE_ASSET_KEY }, suggestions)
        assertFalse(UNKNOWN_TILE_ASSET_KEY in suggestions)
    }

    /** 第三方註冊的 asset key 接在內建牌面之後。 */
    @Test
    fun `appends registered third-party asset keys`() {
        val suggestions = buildTileAssetKeySuggestions(
            remaining = "",
            registeredAssetKeys = setOf("example_cat"),
        )

        assertEquals("example_cat", suggestions.last())
        assertContains(suggestions, "example_cat")
    }

    /** 已輸入的前綴只保留相符候選，且大小寫不敏感。 */
    @Test
    fun `filters candidates by the typed prefix ignoring case`() {
        val suggestions = buildTileAssetKeySuggestions(
            remaining = "EX",
            registeredAssetKeys = setOf("example_cat", "other_key"),
        )

        assertEquals(listOf("example_cat"), suggestions)
    }

    /** 內建牌面與第三方註冊撞名時只留一份。 */
    @Test
    fun `keeps a single candidate for duplicated asset keys`() {
        val builtIn = ALL_TILE_ASSET_KEYS.first { it != UNKNOWN_TILE_ASSET_KEY }

        val suggestions = buildTileAssetKeySuggestions(
            remaining = builtIn,
            registeredAssetKeys = setOf(builtIn),
        )

        assertEquals(1, suggestions.count { it == builtIn })
        assertTrue(suggestions.isNotEmpty())
    }
}
