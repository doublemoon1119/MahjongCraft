package com.doublemoon1119.mahjongcraft.platform.fabric.client.model

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_TILE_ASSET_KEYS
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileModelAssetPath
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.minecraft.util.Identifier

/**
 * 讓麻將牌內建子模型不再只透過 `mahjong_tile.json` 的 predicate override 才會被載入烘焙。
 *
 * 主 item model 已改為 `builtin/entity` marker，不再參照這些子模型；改由此 plugin 呼叫
 * [ModelLoadingPlugin.Context.addModels] 顯式登記，讓 [MahjongTileItemRenderer] 能透過
 * `FabricBakedModelManager.getModel` 以固定 [Identifier] 直接查詢已烘焙模型，重用既有幾何與 UV，
 * 不需重寫。
 */
object MahjongTileModelLoadingPlugin : ModelLoadingPlugin {
    override fun onInitializeModelLoader(pluginContext: ModelLoadingPlugin.Context) {
        pluginContext.addModels(
            ALL_TILE_ASSET_KEYS.map { assetKey ->
                Identifier(MinecraftModMetadata.MOD_ID, tileModelAssetPath(assetKey))
            },
        )
    }
}
