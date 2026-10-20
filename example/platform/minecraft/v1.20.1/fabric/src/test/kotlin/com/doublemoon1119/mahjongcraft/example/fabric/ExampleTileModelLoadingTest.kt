package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.minecraft.ExampleMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.platform.fabric.client.model.FixedMahjongTileModelPreparedData
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import net.minecraft.resource.Resource
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourcePack
import net.minecraft.util.Identifier
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
    fun `resource preparation keeps the example face and shared cover`() {
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
        assertEquals(Identifier(tile.textureId), visible.frontTextureId)
        assertEquals(Identifier(tile.modelId), visible.templateModelId)
        assertEquals(Identifier(appearance.coverTextureId), visible.coverTextureId)
        assertEquals(visible.coverTextureId, unknown.coverTextureId)
        assertEquals(Identifier(appearance.unknownFaceTextureId), unknown.frontTextureId)
    }

    /** 從測試執行時實際打包的資源載入指定完整 ID，不製造模型或替貼圖補路徑。 */
    private class ClasspathResources : ResourceManager {
        override fun getResource(id: Identifier): Optional<Resource> {
            val url = javaClass.classLoader.getResource("assets/${id.namespace}/${id.path}") ?: return Optional.empty()
            return Optional.of(Resource(null as ResourcePack?) { url.openStream() })
        }

        override fun getAllNamespaces(): Set<String> = setOf("mahjongcraft", "mahjongcraft_example")

        override fun getAllResources(id: Identifier): List<Resource> = getResource(id).map(::listOf).orElse(emptyList())

        override fun findResources(startingPath: String, allowedPathPredicate: Predicate<Identifier>): Map<Identifier, Resource> = emptyMap()

        override fun findAllResources(startingPath: String, allowedPathPredicate: Predicate<Identifier>): Map<Identifier, List<Resource>> = emptyMap()

        override fun streamResourcePacks(): Stream<ResourcePack> = Stream.empty()
    }
}
