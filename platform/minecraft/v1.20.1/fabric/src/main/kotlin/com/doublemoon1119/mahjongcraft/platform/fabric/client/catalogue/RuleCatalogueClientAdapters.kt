package com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileTextureAssetPath
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import net.minecraft.client.font.TextRenderer
import net.minecraft.resource.ResourceManager
import net.minecraft.text.Style
import net.minecraft.util.Identifier
import net.minecraft.util.Language

/**
 * 以 Minecraft 字型測量目錄文字。
 *
 * @property font 目前的字型。
 */
internal class TextRendererCatalogueMetrics(private val font: TextRenderer) : RuleCatalogueTextMetrics {
    override fun width(text: String): Int = font.getWidth(text)

    override fun wrap(text: String, maxWidth: Int): List<String> = font.textHandler
        .wrapLines(text, maxWidth.coerceAtLeast(1), Style.EMPTY)
        .map { it.string }
        .ifEmpty { listOf("") }

    override fun trim(text: String, maxWidth: Int): String = font.trimToWidth(text, maxWidth.coerceAtLeast(0))
}

/**
 * 從已載入的資源包找出範例牌面圖案；同一牌種在 [clearCache] 之前只查一次。
 *
 * @property assets 牌種與素材鍵的對應。
 * @property resources 目前的資源管理器。
 */
internal class ResourceCatalogueTileArt(
    private val assets: MinecraftTileAssetRegistry,
    private val resources: ResourceManager,
) : RuleCatalogueTileArt {
    /** 已解析的牌面圖案。 */
    private val resolved = mutableMapOf<Tile, CatalogueTileArt>()

    override fun resolve(tile: Tile): CatalogueTileArt = resolved.getOrPut(tile) {
        val assetKey = tile.toAssetKey(assets)
        val name = if (tile is Tile.Extension) "${tile.typeId.namespace}:${tile.typeId.path}" else assetKey
        val texture = Identifier(MinecraftModMetadata.MOD_ID, tileTextureAssetPath(assetKey))
        if (assetKey == UNKNOWN_TILE_ASSET_KEY || resources.getResource(texture).isEmpty) {
            CatalogueTileArt(assetKey = UNKNOWN_TILE_ASSET_KEY, missingName = name)
        } else {
            CatalogueTileArt(assetKey = assetKey)
        }
    }

    override fun clearCache() = resolved.clear()
}

/**
 * 取得目前語言的翻譯。
 *
 * @param key 翻譯鍵。
 * @return 翻譯文字；目前語言沒有這個鍵時為 null。
 */
internal fun currentLanguageTranslation(key: String): String? = Language.getInstance().takeIf { it.hasTranslation(key) }?.get(key)
