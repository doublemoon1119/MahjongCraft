package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.minecraft.ExampleMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.model.FixedMahjongTileModelPreparedData
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import java.nio.file.Path
import java.util.Optional
import java.util.function.Predicate
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 以實際範例資源執行遊戲模型準備，避免只用自行補路徑的測試掩蓋錯誤登記。 */
class ExampleTileModelLoadingTest {
    /** 自訂正面與共用牌體在模型準備後仍有效，不回退成內建外觀或未知牌面。 */
    @Test
    fun `resource rule decision keeps the example face and shared cover`() {
        val registry = MinecraftTileAssetRegistryImpl().apply {
            ExampleMinecraftMahjongExtension().registerTileAssets(this)
            freeze()
        }
        val appearance = assertNotNull(registry.findAppearanceForRule(ExamplePairingIds.RULE_MODULE))
        val assetKey = assertNotNull(registry.find(ExamplePairingIds.INVERTED_ONE_MAN))
        val tile = assertNotNull(registry.findResources(assetKey))
        val prepared = FixedMahjongTileModelPreparedData.prepare(registry, ClasspathResources())
        assertEquals(appearance, prepared.effectiveAppearance(appearance))
        val visible = prepared.variants.single {
            it.modelId == FixedMahjongTileModelPreparedData.syntheticModelId(appearance.id, assetKey)
        }
        val unknown = prepared.variants.single {
            it.modelId == FixedMahjongTileModelPreparedData.syntheticUnknownModelId(appearance.id)
        }
        assertEquals(ResourceLocation(tile.textureId), visible.frontTextureId)
        assertEquals(ResourceLocation(tile.modelId), visible.templateModelId)
        assertEquals(ResourceLocation(appearance.coverTextureId), visible.coverTextureId)
        assertEquals(visible.coverTextureId, unknown.coverTextureId)
        assertEquals(ResourceLocation(appearance.unknownFaceTextureId), unknown.frontTextureId)
    }

    /** 從測試執行時實際打包的資源載入指定完整 ID，不製造模型或替貼圖補路徑。 */
    private class ClasspathResources : ResourceManager {
        override fun getResource(id: ResourceLocation): Optional<Resource> {
            val url = javaClass.classLoader.getResource("assets/${id.namespace}/${id.path}") ?: return Optional.empty()
            return Optional.of(Resource(PathPackResources("test", Path.of(""), true)) { url.openStream() })
        }

        override fun getNamespaces(): Set<String> = setOf("mahjongcraft", "mahjongcraft_example")

        override fun getResourceStack(id: ResourceLocation): List<Resource> = getResource(id).map(::listOf).orElse(emptyList())

        override fun listResources(startingPath: String, allowedPathPredicate: Predicate<ResourceLocation>): Map<ResourceLocation, Resource> = emptyMap()

        override fun listResourceStacks(startingPath: String, allowedPathPredicate: Predicate<ResourceLocation>): Map<ResourceLocation, List<Resource>> = emptyMap()

        override fun listPacks(): Stream<PackResources> = Stream.empty()
    }
}
