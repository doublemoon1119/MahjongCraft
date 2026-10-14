package com.doublemoon1119.mahjongcraft.platform.fabric.client.model

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearances
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.allTileAssetKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileModelResourceId
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import org.koin.core.context.GlobalContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * 載入麻將牌固定模型與牌組外觀變體。
 *
 * 所有變體都經由同一個 Fabric preparable plugin 登記；資源重載會重新建立快照，
 * 不會與上一輪烘焙結果共用衍生資料。
 */
object MahjongTileModelLoadingPlugin : PreparableModelLoadingPlugin<FixedMahjongTileModelPreparedData> {
    @Volatile
    private var preparedData: FixedMahjongTileModelPreparedData? = null

    /** Fabric 在資源重載前用來準備模型與材質驗證資料的 loader。 */
    val DATA_LOADER: PreparableModelLoadingPlugin.DataLoader<FixedMahjongTileModelPreparedData> =
        PreparableModelLoadingPlugin.DataLoader { resourceManager, executor ->
            load(resourceManager, executor)
        }

    override fun onInitializeModelLoader(
        data: FixedMahjongTileModelPreparedData,
        pluginContext: ModelLoadingPlugin.Context,
    ) {
        preparedData = data
        val registry = GlobalContext.get().get<MinecraftTileAssetRegistry>()
        pluginContext.addModels(
            registry.allTileAssetKeys().map { assetKey ->
                Identifier(tileModelResourceId(assetKey, registry))
            } + data.variants.map { it.modelId },
        )
        pluginContext.resolveModel().register(FixedMahjongTileModelVariantResolver(data.variants))
    }

    /**
     * 取得本次資源重載真正可用的牌組外觀；完整外觀缺少必要資源時回退到內建預設。
     *
     * @param requested 要求使用的牌組外觀。
     * @return 已通過本次資源重載驗證的外觀，或內建預設外觀。
     */
    fun effectiveAppearance(requested: MinecraftTileSetAppearanceDefinition): MinecraftTileSetAppearanceDefinition = preparedData?.effectiveAppearance(requested) ?: BuiltInMinecraftTileAppearances.DEFAULT

    /**
     * 準備本次資源重載的固定牌模型變體。
     *
     * @param resourceManager 本次資源重載使用的資源管理器。
     * @param executor Fabric 提供的非同步準備執行器。
     * @return 已完成資源檢查的模型變體快照。
     */
    private fun load(
        resourceManager: ResourceManager,
        executor: Executor,
    ): CompletableFuture<FixedMahjongTileModelPreparedData> = FixedMahjongTileModelLoadingPlugin.load(resourceManager, executor)
}
