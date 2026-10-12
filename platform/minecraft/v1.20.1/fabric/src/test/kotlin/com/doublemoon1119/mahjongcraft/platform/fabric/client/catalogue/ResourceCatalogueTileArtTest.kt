package com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import net.minecraft.resource.Resource
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourcePack
import net.minecraft.util.Identifier
import java.io.ByteArrayInputStream
import java.util.Optional
import java.util.function.Predicate
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證規則一覽的牌面圖案快取與資源重新載入。 */
class ResourceCatalogueTileArtTest {
    /** 清除快取前沿用第一次的查詢結果；清除後重新查詢，補上的貼圖立即生效。 */
    @Test
    fun `picks up textures added after the cache is cleared`() {
        val resources = ToggleResourceManager()
        val art = ResourceCatalogueTileArt(assets = MinecraftTileAssetRegistryImpl(), resources = resources)
        val tile = Tile.Numeric(Tile.Suit.Character, 1)

        assertEquals(UNKNOWN_TILE_ASSET_KEY, art.resolve(tile).assetKey)
        resources.available = true
        assertEquals(UNKNOWN_TILE_ASSET_KEY, art.resolve(tile).assetKey)

        art.clearCache()

        assertEquals("m1", art.resolve(tile).assetKey)
    }

    /** 所有貼圖同時存在或同時缺少的測試資源。 */
    private class ToggleResourceManager : ResourceManager {
        /** 目前是否提供貼圖。 */
        var available = false

        /** 依 [available] 回傳空資源或空串流的測試資源。 */
        override fun getResource(id: Identifier): Optional<Resource> = if (available) Optional.of(Resource(null as ResourcePack?) { ByteArrayInputStream(ByteArray(0)) }) else Optional.empty()

        /** 測試資源不宣告 namespace。 */
        override fun getAllNamespaces(): Set<String> = emptySet()

        /** 測試資源不列出同一 ID 的多個資源。 */
        override fun getAllResources(id: Identifier): List<Resource> = emptyList()

        /** 測試資源不列出符合路徑的單一資源。 */
        override fun findResources(startingPath: String, allowedPathPredicate: Predicate<Identifier>): Map<Identifier, Resource> = emptyMap()

        /** 測試資源不列出符合路徑的資源集合。 */
        override fun findAllResources(startingPath: String, allowedPathPredicate: Predicate<Identifier>): Map<Identifier, List<Resource>> = emptyMap()

        /** 測試資源沒有可串流的資源包。 */
        override fun streamResourcePacks(): Stream<ResourcePack> = Stream.empty()
    }
}
