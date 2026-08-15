package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 註冊 MahjongCraft 內建 asset key（見 [ALL_TILE_ASSET_KEYS]）對應的 Unicode「Mahjong Tiles」區塊
 * 字元（U+1F000~U+1F02F）。這組字元由 `assets/minecraft/font/default.json`（見
 * `platform/minecraft/common/src/jvmMain/resources`）註冊成自訂 bitmap 字型，讓文字內容直接顯示成
 * 牌面貼圖。
 *
 * 內建映射與第三方映射共用 [TileEmojiRegistry.register]，不具有覆寫或繞過重複 key 驗證的特權。
 */
fun TileEmojiRegistry.registerBuiltInTileEmojis() {
    register("east", "🀀")
    register("south", "🀁")
    register("west", "🀂")
    register("north", "🀃")
    register("red_dragon", "🀄")
    register("green_dragon", "🀅")
    register("white_dragon", "🀆")
    register("m1", "🀇")
    register("m2", "🀈")
    register("m3", "🀉")
    register("m4", "🀊")
    register("m5", "🀋")
    register("m6", "🀌")
    register("m7", "🀍")
    register("m8", "🀎")
    register("m9", "🀏")
    register("s1", "🀐")
    register("s2", "🀑")
    register("s3", "🀒")
    register("s4", "🀓")
    register("s5", "🀔")
    register("s6", "🀕")
    register("s7", "🀖")
    register("s8", "🀗")
    register("s9", "🀘")
    register("p1", "🀙")
    register("p2", "🀚")
    register("p3", "🀛")
    register("p4", "🀜")
    register("p5", "🀝")
    register("p6", "🀞")
    register("p7", "🀟")
    register("p8", "🀠")
    register("p9", "🀡")
    register("flower_plum", "🀣")
    register("flower_orchid", "🀤")
    register("flower_bamboo", "🀥")
    register("flower_chrysanthemum", "🀦")
    register("flower_spring", "🀧")
    register("flower_summer", "🀨")
    register("flower_autumn", "🀩")
    register("flower_winter", "🀪")
    register("m5_red", "🀬")
    register("s5_red", "🀭")
    register("p5_red", "🀮")
    register(UNKNOWN_TILE_ASSET_KEY, "🀯")
}
