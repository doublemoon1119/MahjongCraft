package com.doublemoon1119.mahjongcraft.platform.fabric.client.model

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import net.minecraft.resource.ResourceManager
import org.koin.core.context.GlobalContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * 在每次資源重載時準備並註冊固定牌模型的材質變體。
 *
 * 資源存在性與模板相容性在 model loader 開始前驗證；resolver 只交付已驗證的變體，
 * 因此不會在每幀或烘焙期間讀取資源檔案。
 */
object FixedMahjongTileModelLoadingPlugin {
    /**
     * 載入牌組外觀與牌種模型，產生固定模板的可見與未知牌變體。
     *
     * @param resourceManager 本次資源重載使用的資源管理器。
     * @param executor Fabric 提供的非同步準備執行器。
     * @return 在 model loader 開始前完成準備的資料。
     */
    fun load(resourceManager: ResourceManager, executor: Executor): CompletableFuture<FixedMahjongTileModelPreparedData> = CompletableFuture.supplyAsync(
        {
            val registry = GlobalContext.get().get<MinecraftTileAssetRegistry>()
            FixedMahjongTileModelPreparedData.prepare(registry, resourceManager)
        },
        executor,
    )
}
