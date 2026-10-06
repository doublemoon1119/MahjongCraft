package com.doublemoon1119.mahjongcraft.platform.minecraft.showcase

import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import kotlin.test.Test
import kotlin.test.assertNotNull

/** 驗證內建展示定義引用的資源檔都存在。 */
class WinCelebrationShowcaseResourceFilesTest {
    /** 每個內建展示的標題圖片都能從資源中載入。 */
    @Test
    fun `built in showcase title images exist`() {
        val registry = WinCelebrationShowcaseRegistryImpl().apply { BundledMinecraftMahjongExtensions.all.forEach { it.registerWinCelebrationShowcases(this) } }

        registry.cueKeys.forEach { cueKey ->
            val (namespace, path) = assertNotNull(registry.find(cueKey)).titleImageResourceId.split(':', limit = 2)
            val resourcePath = "/assets/$namespace/$path"
            assertNotNull(javaClass.getResource(resourcePath), "Showcase title image not found for $cueKey: $resourcePath")
        }
    }
}
