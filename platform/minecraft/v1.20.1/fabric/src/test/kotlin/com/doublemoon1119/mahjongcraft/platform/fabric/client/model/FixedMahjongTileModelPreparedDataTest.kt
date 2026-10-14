package com.doublemoon1119.mahjongcraft.platform.fabric.client.model

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearances
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition
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

/** 驗證資源重載時固定牌模型變體的缺失資源 fallback。 */
class FixedMahjongTileModelPreparedDataTest {
    /** 正面貼圖缺失時，變體仍保留合格牌面模型幾何並使用同牌組隱藏正面。 */
    @Test
    fun `missing front texture uses hidden texture with canonical geometry`() {
        val resources = TestResourceManager(missingTextures = setOf("m1"))
        val prepared = prepare(resources)
        val variant = prepared.variants.single {
            it.modelId == FixedMahjongTileModelPreparedData.syntheticModelId(
                BuiltInMinecraftTileAppearances.DEFAULT.id,
                "m1",
            )
        }

        assertEquals(Identifier(BuiltInMinecraftTileAppearances.DEFAULT.unknownFaceTextureId), variant.frontTextureId)
        assertEquals(Identifier("mahjongcraft", "item/mahjong_tile/mahjong_tile_m1"), variant.templateModelId)
    }

    /** 未知牌變體固定使用 canonical 幾何與該牌組共用 cover。 */
    @Test
    fun `unknown variant uses canonical geometry and appearance cover`() {
        val prepared = prepare(TestResourceManager())
        val variant = prepared.variants.single {
            it.modelId == FixedMahjongTileModelPreparedData.syntheticUnknownModelId(
                BuiltInMinecraftTileAppearances.DEFAULT.id,
            )
        }

        assertEquals(Identifier("mahjongcraft", "item/mahjong_tile_base"), variant.templateModelId)
        assertEquals(Identifier(BuiltInMinecraftTileAppearances.DEFAULT.coverTextureId), variant.coverTextureId)
        assertEquals(Identifier(BuiltInMinecraftTileAppearances.DEFAULT.unknownFaceTextureId), variant.frontTextureId)
    }

    /** 牌面模型缺失時，變體仍使用該牌面貼圖與 canonical 固定幾何。 */
    @Test
    fun `missing tile model uses canonical geometry with front texture`() {
        val resources = TestResourceManager(missingModels = setOf("m1"))
        val prepared = prepare(resources)
        val variant = prepared.variants.single {
            it.modelId == FixedMahjongTileModelPreparedData.syntheticModelId(
                BuiltInMinecraftTileAppearances.DEFAULT.id,
                "m1",
            )
        }

        assertEquals(Identifier("mahjongcraft", "textures/item/mahjong_tile/mahjong_tile_m1.png"), variant.frontTextureId)
        assertEquals(Identifier("mahjongcraft", "item/mahjong_tile_base"), variant.templateModelId)
    }

    /** 模型 JSON 的 parent 型別錯誤時，不會讓資源重載失敗，並回退固定幾何。 */
    @Test
    fun `malformed parent falls back to canonical geometry`() {
        val resources = TestResourceManager(malformedModels = setOf("m1"))
        val prepared = prepare(resources)
        val variant = prepared.variants.single {
            it.modelId == FixedMahjongTileModelPreparedData.syntheticModelId(
                BuiltInMinecraftTileAppearances.DEFAULT.id,
                "m1",
            )
        }

        assertEquals(Identifier("mahjongcraft", "item/mahjong_tile_base"), variant.templateModelId)
    }

    /** 外觀缺少必要 cover 時，effective appearance 會回退內建預設牌組。 */
    @Test
    fun `incomplete appearance falls back to default`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val custom = MinecraftTileSetAppearanceDefinition(
            id = "example:missing_cover",
            coverTextureId = "example:textures/missing.png",
            unknownFaceTextureId = BuiltInMinecraftTileAppearances.DEFAULT.unknownFaceTextureId,
        )
        registry.register(custom)
        registry.freeze()

        val prepared = FixedMahjongTileModelPreparedData.prepare(
            registry,
            TestResourceManager(missingTextures = setOf("missing")),
        )

        assertEquals(BuiltInMinecraftTileAppearances.DEFAULT, prepared.effectiveAppearance(custom))
    }

    /** 外觀缺少 unknown face texture 時，也會回退內建預設牌組。 */
    @Test
    fun `appearance without unknown face falls back to default`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val custom = MinecraftTileSetAppearanceDefinition(
            id = "example:missing_unknown",
            coverTextureId = BuiltInMinecraftTileAppearances.DEFAULT.coverTextureId,
            unknownFaceTextureId = "example:textures/missing_unknown.png",
        )
        registry.register(custom)
        registry.freeze()

        val prepared = FixedMahjongTileModelPreparedData.prepare(
            registry,
            TestResourceManager(missingTextures = setOf("missing_unknown")),
        )

        assertEquals(BuiltInMinecraftTileAppearances.DEFAULT, prepared.effectiveAppearance(custom))
    }

    private fun prepare(resources: TestResourceManager): FixedMahjongTileModelPreparedData {
        val registry = MinecraftTileAssetRegistryImpl().also { it.freeze() }
        return FixedMahjongTileModelPreparedData.prepare(registry, resources)
    }

    /**
     * 依測試設定提供固定牌模型 JSON 與貼圖資源的最小 ResourceManager。
     *
     * @property missingTextures 應模擬缺少的牌面 key。
     * @property missingModels 應模擬缺少的牌面 model key。
     * @property malformedModels 應提供錯誤 parent JSON 的牌面 model key。
     */
    private class TestResourceManager(
        private val missingTextures: Set<String> = emptySet(),
        private val missingModels: Set<String> = emptySet(),
        private val malformedModels: Set<String> = emptySet(),
    ) : ResourceManager {
        override fun getResource(id: Identifier): Optional<Resource> {
            val path = id.path
            val assetKey = path.substringAfterLast('/').removePrefix("mahjong_tile_").substringBeforeLast('.')
            if (path.startsWith("textures/") && missingTextures.contains(assetKey)) return Optional.empty()
            if (path.startsWith("models/") && missingModels.contains(assetKey)) return Optional.empty()
            val bytes = if (path.startsWith("models/")) {
                if (malformedModels.contains(assetKey)) {
                    "{\"parent\": []}".toByteArray()
                } else if (path.endsWith("mahjong_tile_base.json")) {
                    "{\"elements\": [], \"display\": {}}".toByteArray()
                } else {
                    "{\"parent\": \"mahjongcraft:item/mahjong_tile_base\"}".toByteArray()
                }
            } else {
                byteArrayOf(0)
            }
            return Optional.of(Resource(null as ResourcePack?) { ByteArrayInputStream(bytes) })
        }

        override fun getAllNamespaces(): Set<String> = emptySet()
        override fun getAllResources(id: Identifier): List<Resource> = emptyList()
        override fun findResources(startingPath: String, allowedPathPredicate: Predicate<Identifier>): Map<Identifier, Resource> = emptyMap()
        override fun findAllResources(startingPath: String, allowedPathPredicate: Predicate<Identifier>): Map<Identifier, List<Resource>> = emptyMap()
        override fun streamResourcePacks(): Stream<ResourcePack> = Stream.empty()
    }
}
