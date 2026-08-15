package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 將 [Tile.toAssetKey] 產生的素材識別字串對照到對應的 Unicode「Mahjong Tiles」區塊字元（U+1F000
 * ~U+1F02F）。這組字元由 `assets/minecraft/font/default.json`（見
 * `platform/minecraft/common/src/jvmMain/resources`）註冊成自訂 bitmap 字型，讓文字內容直接顯示成
 * 牌面貼圖；這裡只是純字串對照表，不依賴任何 Minecraft API，供任何組 `Text` 的 loader 模組共用。
 *
 * 只涵蓋 [ALL_TILE_ASSET_KEYS] 收錄的 key；未登記的第三方 asset key 回傳 `null`，呼叫端應該直接顯示
 * 純文字，不強行湊一個不存在的圖案。
 */
private val TILE_ASSET_KEY_EMOJIS: Map<String, String> = buildMap {
    put("east", "🀀")
    put("south", "🀁")
    put("west", "🀂")
    put("north", "🀃")
    put("red_dragon", "🀄")
    put("green_dragon", "🀅")
    put("white_dragon", "🀆")
    put("m1", "🀇")
    put("m2", "🀈")
    put("m3", "🀉")
    put("m4", "🀊")
    put("m5", "🀋")
    put("m6", "🀌")
    put("m7", "🀍")
    put("m8", "🀎")
    put("m9", "🀏")
    put("s1", "🀐")
    put("s2", "🀑")
    put("s3", "🀒")
    put("s4", "🀓")
    put("s5", "🀔")
    put("s6", "🀕")
    put("s7", "🀖")
    put("s8", "🀗")
    put("s9", "🀘")
    put("p1", "🀙")
    put("p2", "🀚")
    put("p3", "🀛")
    put("p4", "🀜")
    put("p5", "🀝")
    put("p6", "🀞")
    put("p7", "🀟")
    put("p8", "🀠")
    put("p9", "🀡")
    put("flower_plum", "🀣")
    put("flower_orchid", "🀤")
    put("flower_bamboo", "🀥")
    put("flower_chrysanthemum", "🀦")
    put("flower_spring", "🀧")
    put("flower_summer", "🀨")
    put("flower_autumn", "🀩")
    put("flower_winter", "🀪")
    put("m5_red", "🀬")
    put("s5_red", "🀭")
    put("p5_red", "🀮")
    put(UNKNOWN_TILE_ASSET_KEY, "🀯")
}

/** 查詢 [assetKey] 對應的牌面 emoji 字元；未登記時回傳 `null`。 */
fun assetKeyToEmoji(assetKey: String): String? = TILE_ASSET_KEY_EMOJIS[assetKey]
