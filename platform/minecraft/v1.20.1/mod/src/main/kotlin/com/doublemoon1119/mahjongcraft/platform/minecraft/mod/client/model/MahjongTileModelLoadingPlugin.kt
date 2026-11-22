package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.model

import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.model.FixedMahjongTileModelLoadingPlugin
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.model.FixedMahjongTileModelPreparedData
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModelLoading
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearances
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.allTileAssetKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileModelResourceId
import net.minecraft.client.resources.model.UnbakedModel
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import org.koin.core.context.GlobalContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * 載入麻將牌與內建角色模型，並準備牌組外觀變體。
 *
 * 每次資源重載重新準備變體快照，不與上一輪烘焙結果共用衍生資料；沒有模型檔的變體由 [resolveModel] 以 synthetic ID 提供。
 */
object MahjongTileModelLoadingPlugin : ModelLoading<FixedMahjongTileModelPreparedData> {
    /** 最近一次模型載入使用的變體快照。 */
    @Volatile
    private var preparedData: FixedMahjongTileModelPreparedData? = null

    override fun prepare(
        resourceManager: ResourceManager,
        executor: Executor,
    ): CompletableFuture<FixedMahjongTileModelPreparedData> = FixedMahjongTileModelLoadingPlugin.load(resourceManager, executor)

    override fun additionalModels(data: FixedMahjongTileModelPreparedData): Collection<ResourceLocation> {
        preparedData = data
        val registry = GlobalContext.get().get<MinecraftTileAssetRegistry>()
        return buildList {
            registry.allTileAssetKeys().mapTo(this) { assetKey ->
                ResourceLocation(tileModelResourceId(assetKey, registry))
            }
            data.variants.mapTo(this) { it.modelId }
            add(ResourceLocation(BuiltInAiCharacters.TILE_MASCOT_LIMB_MODEL_ID))
        }
    }

    /** 只接管本次準備的 synthetic ID；其他模型交回原本的載入流程。 */
    override fun resolveModel(data: FixedMahjongTileModelPreparedData, id: ResourceLocation): UnbakedModel? = data.variant(id)?.toUnbakedModel()

    /**
     * 取得本次資源重載真正可用的牌組外觀；完整外觀缺少必要資源時回退到內建預設。
     *
     * @param requested 要求使用的牌組外觀。
     * @return 已通過本次資源重載驗證的外觀，或內建預設外觀。
     */
    fun effectiveAppearance(requested: MinecraftTileSetAppearanceDefinition): MinecraftTileSetAppearanceDefinition = preparedData?.effectiveAppearance(requested) ?: BuiltInMinecraftTileAppearances.DEFAULT
}
