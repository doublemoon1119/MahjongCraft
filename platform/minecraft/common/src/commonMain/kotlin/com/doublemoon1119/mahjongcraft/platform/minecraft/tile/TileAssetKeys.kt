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
 * @throws UnsupportedOperationException 若為花牌——目前沒有對應素材。
 */
fun Tile.toAssetKey(): String = when (this) {
    is Tile.Numeric -> buildString {
        append(suit.assetPrefix)
        append(value)
        if (isRed) append("_red")
    }
    Tile.Honor.East -> "east"
    Tile.Honor.South -> "south"
    Tile.Honor.West -> "west"
    Tile.Honor.North -> "north"
    Tile.Honor.Red -> "red_dragon"
    Tile.Honor.Green -> "green_dragon"
    Tile.Honor.White -> "white_dragon"
    is Tile.Flower -> throw UnsupportedOperationException(
        "Flower tiles have no Minecraft asset key yet: $this",
    )
}

private val Tile.Suit.assetPrefix: Char
    get() = when (this) {
        Tile.Suit.Character -> 'm'
        Tile.Suit.Dot -> 'p'
        Tile.Suit.Bamboo -> 's'
    }

/**
 * 日麻會用到的全部素材識別字串，順序固定——`platform/minecraft/v1.20.1/fabric` 的
 * `mahjong_tile.json` item model override 清單依這個順序產生，兩者必須保持同步。
 *
 * 不含花牌（見 [toAssetKey]），含結尾的 [UNKNOWN_TILE_ASSET_KEY] 佔位牌。
 */
val ALL_RIICHI_TILE_ASSET_KEYS: List<String> = buildList {
    for (suit in Tile.Suit.entries) {
        for (value in 1..9) {
            add(Tile.Numeric(suit, value).toAssetKey())
            if (value == 5) add(Tile.Numeric(suit, value, isRed = true).toAssetKey())
        }
    }
    add(Tile.Honor.East.toAssetKey())
    add(Tile.Honor.South.toAssetKey())
    add(Tile.Honor.West.toAssetKey())
    add(Tile.Honor.North.toAssetKey())
    add(Tile.Honor.Red.toAssetKey())
    add(Tile.Honor.Green.toAssetKey())
    add(Tile.Honor.White.toAssetKey())
    add(UNKNOWN_TILE_ASSET_KEY)
}
