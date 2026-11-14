package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView

/**
 * 未知/佔位牌（正面朝下、無法辨識）在 Minecraft 資源裡使用的素材識別字串。
 */
const val UNKNOWN_TILE_ASSET_KEY = "unknown"

/**
 * 將 [Tile] 轉換成 Minecraft 牌面資源映射使用的素材識別字串。
 *
 * 數牌格式為 `{花色字母}{數值}`；字牌則各自對應到固定的英文名稱（例如 `east`、`white_dragon`）。
 * 外觀樣式（例如赤五）不在這裡處理，改用 [TileFaceView.toAssetKey]。
 *
 * @param registry 保存 [Tile.Extension] 對應 asset key 的 runtime registry；內建與第三方牌種共用
 * 同一份查詢流程。無法解析的 Extension ID 會回退至 [UNKNOWN_TILE_ASSET_KEY]，讓物品與 entity 使用
 * 相同的佔位外觀；此 fallback 不會改寫權威狀態保存的原始牌種 ID。
 * @return 對應的 Minecraft 素材 key。
 */
fun Tile.toAssetKey(registry: MinecraftTileAssetRegistry): String = when (this) {
    is Tile.Numeric -> standardAssetKey()
    is Tile.Honor -> standardAssetKey()
    is Tile.Extension -> registry.find(typeId) ?: UNKNOWN_TILE_ASSET_KEY
}

/**
 * 數牌固定使用的素材 key，格式為 `{花色字母}{數值}`；不需要 registry，可在登記期間使用。
 *
 * @return 對應的 Minecraft 素材 key。
 */
fun Tile.Numeric.standardAssetKey(): String = "${suit.assetPrefix}$value"

/**
 * 字牌固定使用的素材 key，例如 `east`、`white_dragon`；不需要 registry，可在登記期間使用。
 *
 * @return 對應的 Minecraft 素材 key。
 */
fun Tile.Honor.standardAssetKey(): String = when (this) {
    Tile.Honor.East -> "east"
    Tile.Honor.South -> "south"
    Tile.Honor.West -> "west"
    Tile.Honor.North -> "north"
    Tile.Honor.Red -> "red_dragon"
    Tile.Honor.Green -> "green_dragon"
    Tile.Honor.White -> "white_dragon"
}

/**
 * 將已知牌面轉成素材 key：外觀樣式有登記時使用該樣式的素材，否則使用基本牌面的素材。
 *
 * @param registry 牌面與外觀樣式的素材 registry。
 * @return 對應的 Minecraft 素材 key。
 */
fun TileFaceView.toAssetKey(registry: MinecraftTileAssetRegistry): String = appearanceId
    ?.let { registry.findStyle(it, tile) }
    ?: tile.toAssetKey(registry)

/**
 * 數牌花色對應的單字母素材前綴。
 *
 * @return 萬、餅、條分別對應的素材前綴。
 */
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
 * 所有應有牌面資源映射的素材識別字串，順序固定：[STANDARD_TILE_ASSET_KEYS]、這個 registry 依登記順序的
 * 擴充牌種，最後是 [UNKNOWN_TILE_ASSET_KEY] 佔位牌。各 loader adapter 依此清單註冊模型、建立渲染用物品，
 * 並作為循環換牌的順序；擴充牌種可在登記描述中提供自訂資源，省略時沿用內建資源路徑。
 *
 * @throws IllegalStateException registry 尚未凍結時拋出；擴充牌種要等所有 extension 登記完成才確定。
 * @receiver 已凍結的牌資產 registry。
 * @return 內建、已登記擴充與未知牌 key 的固定順序清單。
 */
fun MinecraftTileAssetRegistry.allTileAssetKeys(): List<String> {
    check(isFrozen) { "Tile asset keys are only complete after the tile asset registry is frozen" }
    return buildList {
        addAll(STANDARD_TILE_ASSET_KEYS)
        addAll(registeredAssetKeys)
        add(UNKNOWN_TILE_ASSET_KEY)
    }
}

/**
 * 判斷素材 key 是否有牌面資源：基本牌 key（[STANDARD_TILE_ASSET_KEYS]）、[UNKNOWN_TILE_ASSET_KEY] 佔位牌，
 * 或這個 registry 已登記的擴充牌種 key。
 *
 * @param assetKey 要確認的素材 key。
 * @return 認得這個 key 時為 true。
 */
fun MinecraftTileAssetRegistry.isKnownTileAssetKey(assetKey: String): Boolean = assetKey in STANDARD_TILE_ASSET_KEYS || assetKey == UNKNOWN_TILE_ASSET_KEY || isRegisteredAssetKey(assetKey)

/**
 * 將外部讀取的素材 key 正規化；[MinecraftTileAssetRegistry.isKnownTileAssetKey] 認得的 key 保持不變，
 * 其餘一律回退至 [UNKNOWN_TILE_ASSET_KEY]。
 *
 * @param registry 用來確認擴充 key 是否已登記的 registry。
 * @return 可識別的原 key，或未知牌 key。
 */
fun String?.normalizedTileAssetKey(registry: MinecraftTileAssetRegistry): String = this
    ?.takeIf { registry.isKnownTileAssetKey(it) }
    ?: UNKNOWN_TILE_ASSET_KEY

/**
 * 取得 [MinecraftTileAssetRegistry.allTileAssetKeys] 循環順序中的下一個素材 key。
 *
 * 無效或缺失值視為尚未選擇牌面，因此回到第一張 `m1`，而不是從 `unknown` 繼續循環。
 *
 * @param registry 已凍結的 asset key registry。
 * @return 循環順序中的下一個素材 key。
 */
fun String?.nextTileAssetKey(registry: MinecraftTileAssetRegistry): String {
    val keys = registry.allTileAssetKeys()
    val currentIndex = this?.let(keys::indexOf) ?: -1
    return keys[(currentIndex + 1) % keys.size]
}
