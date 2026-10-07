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

        override fun getResource(id: Identifier): Optional<Resource> = if (available) Optional.of(Resource(null as ResourcePack?) { ByteArrayInputStream(ByteArray(0)) }) else Optional.empty()

        override fun getAllNamespaces(): Set<String> = emptySet()

        override fun getAllResources(id: Identifier): List<Resource> = emptyList()

        override fun findResources(startingPath: String, allowedPathPredicate: Predicate<Identifier>): Map<Identifier, Resource> = emptyMap()

        override fun findAllResources(startingPath: String, allowedPathPredicate: Predicate<Identifier>): Map<Identifier, List<Resource>> = emptyMap()

        override fun streamResourcePacks(): Stream<ResourcePack> = Stream.empty()
    }
}
