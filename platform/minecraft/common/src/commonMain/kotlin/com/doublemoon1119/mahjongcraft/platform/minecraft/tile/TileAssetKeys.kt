package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 未知/佔位牌（正面朝下、無法辨識）在 Minecraft 資源裡使用的素材識別字串。
 */
const val UNKNOWN_TILE_ASSET_KEY = "unknown"

/**
 * 將 [Tile] 轉換成 Minecraft 資源檔（貼圖/模型檔名、item NBT 儲存值）使用的素材識別字串。
 *
 * 數牌格式為 `{花色字母}{數值}`，紅五額外加上 `_red` 後綴（例如 `m5_red`）；字牌則各自對應到固定的
 * 英文名稱（例如 `east`、`white_dragon`）。
 *
 * @param registry 保存 [Tile.Extension] 對應 asset key 的 runtime registry；內建與第三方牌種共用
 * 同一份查詢流程。無法解析的 Extension ID 會回退至 [UNKNOWN_TILE_ASSET_KEY]，讓物品與 entity 使用
 * 相同的佔位外觀；此 fallback 不會改寫權威狀態保存的原始牌種 ID。
 */
fun Tile.toAssetKey(registry: MinecraftTileAssetRegistry): String = when (this) {
    is Tile.Numeric -> "${suit.assetPrefix}$value"
    Tile.Honor.East -> "east"
    Tile.Honor.South -> "south"
    Tile.Honor.West -> "west"
    Tile.Honor.North -> "north"
    Tile.Honor.Red -> "red_dragon"
    Tile.Honor.Green -> "green_dragon"
    Tile.Honor.White -> "white_dragon"
    is Tile.Extension -> registry.find(typeId) ?: UNKNOWN_TILE_ASSET_KEY
}

private val Tile.Suit.assetPrefix: Char
    get() = when (this) {
        Tile.Suit.Character -> 'm'
        Tile.Suit.Dot -> 'p'
        Tile.Suit.Bamboo -> 's'
    }

/**
 * 34 種基本牌的素材識別字串，依萬子、餅子、條子的 1～9 與東南西北、中發白排列；不屬於任何規則。
 */
val STANDARD_TILE_ASSET_KEYS: List<String> = buildList {
    for (suit in Tile.Suit.entries) {
        for (value in 1..9) add("${suit.assetPrefix}$value")
    }
    addAll(listOf("east", "south", "west", "north", "red_dragon", "green_dragon", "white_dragon"))
}

/**
 * 所有應有對應材質與模型的素材識別字串，順序固定：[STANDARD_TILE_ASSET_KEYS]、這個 registry 依登記順序的
 * 擴充牌種，最後是 [UNKNOWN_TILE_ASSET_KEY] 佔位牌。各 loader adapter 依此清單註冊模型、建立渲染用物品，
 * 並作為循環換牌的順序；登記擴充牌種的 extension 須提供對應的材質與模型。
 *
 * @throws IllegalStateException registry 尚未凍結時拋出；擴充牌種要等所有 extension 登記完成才確定。
 */
fun MinecraftTileAssetRegistry.allTileAssetKeys(): List<String> {
    check(isFrozen) { "Tile asset keys are only complete after the tile asset registry is frozen" }
    return STANDARD_TILE_ASSET_KEYS + registeredAssetKeys + UNKNOWN_TILE_ASSET_KEY
}

/**
 * 將外部讀取的素材 key 正規化；基本牌 key（[STANDARD_TILE_ASSET_KEYS]）或 [registry] 已註冊的擴充牌種 key
 * 保持不變，其餘一律回退至 [UNKNOWN_TILE_ASSET_KEY]。
 */
fun String?.normalizedTileAssetKey(registry: MinecraftTileAssetRegistry): String = this
    ?.takeIf { it in STANDARD_TILE_ASSET_KEYS || registry.isRegisteredAssetKey(it) }
    ?: UNKNOWN_TILE_ASSET_KEY

/**
 * 取得 [MinecraftTileAssetRegistry.allTileAssetKeys] 循環順序中的下一個素材 key。
 *
 * 無效或缺失值視為尚未選擇牌面，因此回到第一張 `m1`，而不是從 `unknown` 繼續循環。
 *
 * @param registry 已凍結的 asset key registry。
 */
fun String?.nextTileAssetKey(registry: MinecraftTileAssetRegistry): String {
    val keys = registry.allTileAssetKeys()
    val currentIndex = this?.let(keys::indexOf) ?: -1
    return keys[(currentIndex + 1) % keys.size]
}
