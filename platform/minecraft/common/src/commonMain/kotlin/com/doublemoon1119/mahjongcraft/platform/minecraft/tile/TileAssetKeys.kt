package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes

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
 * 無法解析的舊牌型或 Extension ID 會回退至 [UNKNOWN_TILE_ASSET_KEY]，讓物品與 entity 使用相同的
 * 佔位外觀；此 fallback 不會改寫權威狀態保存的原始牌種 ID。
 */
fun Tile.toAssetKey(): String = when (this) {
    is Tile.Numeric -> "${suit.assetPrefix}$value"
    Tile.Honor.East -> "east"
    Tile.Honor.South -> "south"
    Tile.Honor.West -> "west"
    Tile.Honor.North -> "north"
    Tile.Honor.Red -> "red_dragon"
    Tile.Honor.Green -> "green_dragon"
    Tile.Honor.White -> "white_dragon"
    is Tile.Flower -> UNKNOWN_TILE_ASSET_KEY
    is Tile.Extension -> when (typeId) {
        RiichiTileTypes.RED_FIVE_CHARACTER -> "m5_red"
        RiichiTileTypes.RED_FIVE_DOT -> "p5_red"
        RiichiTileTypes.RED_FIVE_BAMBOO -> "s5_red"
        TaiwanTileTypes.SPRING -> "flower_spring"
        TaiwanTileTypes.SUMMER -> "flower_summer"
        TaiwanTileTypes.AUTUMN -> "flower_autumn"
        TaiwanTileTypes.WINTER -> "flower_winter"
        TaiwanTileTypes.PLUM -> "flower_plum"
        TaiwanTileTypes.ORCHID -> "flower_orchid"
        TaiwanTileTypes.BAMBOO -> "flower_bamboo"
        TaiwanTileTypes.CHRYSANTHEMUM -> "flower_chrysanthemum"
        else -> UNKNOWN_TILE_ASSET_KEY
    }
}

private val Tile.Suit.assetPrefix: Char
    get() = when (this) {
        Tile.Suit.Character -> 'm'
        Tile.Suit.Dot -> 'p'
        Tile.Suit.Bamboo -> 's'
    }

/**
 * 目前內建牌種使用的全部素材識別字串，順序固定——`platform/minecraft/v1.20.1/fabric` 的
 * `mahjong_tile.json` item model override 清單依這個順序產生，兩者必須保持同步。
 *
 * 包含日麻牌、台灣花牌與結尾的 [UNKNOWN_TILE_ASSET_KEY] 佔位牌。
 */
val ALL_TILE_ASSET_KEYS: List<String> = buildList {
    for (suit in Tile.Suit.entries) {
        for (value in 1..9) {
            add(Tile.Numeric(suit, value).toAssetKey())
            if (value == 5) add(RiichiTileTypes.redFive(suit).toAssetKey())
        }
    }
    add(Tile.Honor.East.toAssetKey())
    add(Tile.Honor.South.toAssetKey())
    add(Tile.Honor.West.toAssetKey())
    add(Tile.Honor.North.toAssetKey())
    add(Tile.Honor.Red.toAssetKey())
    add(Tile.Honor.Green.toAssetKey())
    add(Tile.Honor.White.toAssetKey())
    TaiwanTileTypes.createAll().forEach { add(it.toAssetKey()) }
    add(UNKNOWN_TILE_ASSET_KEY)
}

/** 將外部讀取的素材 key 正規化；不在支援清單中的值一律回退至 [UNKNOWN_TILE_ASSET_KEY]。 */
fun String?.normalizedTileAssetKey(): String = this?.takeIf(ALL_TILE_ASSET_KEYS::contains)
    ?: UNKNOWN_TILE_ASSET_KEY

/**
 * 取得循環順序中的下一個素材 key。
 *
 * 無效或缺失值視為尚未選擇牌面，因此回到第一張 `m1`，而不是從 `unknown` 繼續循環。
 */
fun String?.nextTileAssetKey(): String {
    val currentIndex = this?.let(ALL_TILE_ASSET_KEYS::indexOf) ?: -1
    return ALL_TILE_ASSET_KEYS[(currentIndex + 1) % ALL_TILE_ASSET_KEYS.size]
}
