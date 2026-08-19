package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 註冊 MahjongCraft 內建 asset key（見 [ALL_TILE_ASSET_KEYS]）對應的牌面角落標籤，供非中文圈玩家
 * 開啟輔助標籤時使用。
 *
 * 內建映射與第三方映射共用 [TileLabelRegistry.register]，不具有覆寫或繞過重複 key 驗證的特權；
 * [UNKNOWN_TILE_ASSET_KEY] 與尚未提供標籤的第三方牌種一律不註冊，呈現端 [TileLabelRegistry.find]
 * 回傳 `null` 時不顯示任何標籤。
 *
 * 顏色規則（使用者指定）：
 * - 非花牌中，赤牌（赤五餅／赤五條／赤五萬、紅中——牌面本身印刷成紅色）的角落文字用黑色，其餘非花牌
 *   一律用紅色；非花牌只有右上角標籤，左上角固定不顯示。
 * - 花牌成對出現：春夏秋冬右上角是紅色中文字、左上角是黑色數字（依春夏秋冬排序，1～4）；梅蘭菊竹
 *   右上角是紅色數字（依梅蘭菊竹排序，1～4，對應 [TaiwanTileTypes.ALL] 修正後的順序）、左上角是黑色
 *   中文字。
 *
 * 文字內容規則：數牌只顯示點數數字，不額外標示花色字母（花色本身已由牌面材質的餅／條／萬圖案表達）；
 * 字牌用單一或兩個字母縮寫（東南西北＝E/S/W/N，紅中＝R，發＝G，白＝Wh——避免跟西風的 `W` 混淆特地用
 * 兩個字母）。
 *
 * 花牌額外標記 [TileLabel.forced]＝`true`：八張花牌都是同一個樹形剪影配不同季節配色（見
 * `mahjong_tile_flower_*.png`），彼此外觀相近、單靠材質不容易一眼分辨花色與順序，標籤本身兼有辨識
 * 功能，不只是給非中文圈玩家看的輔助資訊，因此無視玩家本機開關、永遠顯示；其餘牌種維持預設的
 * `forced = false`。
 */
fun TileLabelRegistry.registerBuiltInTileLabels() {
    // 萬子
    register("m1", redText("1"))
    register("m2", redText("2"))
    register("m3", redText("3"))
    register("m4", redText("4"))
    register("m5", redText("5"))
    register("m6", redText("6"))
    register("m7", redText("7"))
    register("m8", redText("8"))
    register("m9", redText("9"))
    register("m5_red", blackTextOnRedTile("5"))

    // 條子
    register("s1", redText("1"))
    register("s2", redText("2"))
    register("s3", redText("3"))
    register("s4", redText("4"))
    register("s5", redText("5"))
    register("s6", redText("6"))
    register("s7", redText("7"))
    register("s8", redText("8"))
    register("s9", redText("9"))
    register("s5_red", blackTextOnRedTile("5"))

    // 餅子
    register("p1", redText("1"))
    register("p2", redText("2"))
    register("p3", redText("3"))
    register("p4", redText("4"))
    register("p5", redText("5"))
    register("p6", redText("6"))
    register("p7", redText("7"))
    register("p8", redText("8"))
    register("p9", redText("9"))
    register("p5_red", blackTextOnRedTile("5"))

    // 風牌
    register("east", redText("E"))
    register("south", redText("S"))
    register("west", redText("W"))
    register("north", redText("N"))

    // 三元牌：紅中牌面本身印刷成紅色，跟赤牌歸為同一種黑字規則
    register("red_dragon", blackTextOnRedTile("R"))
    register("green_dragon", redText("G"))
    register("white_dragon", redText("Wh"))

    // 四季花牌：右上紅色中文字、左上黑色數字，依春夏秋冬排序
    register("flower_spring", seasonalFlower(chinese = "春", order = "1"))
    register("flower_summer", seasonalFlower(chinese = "夏", order = "2"))
    register("flower_autumn", seasonalFlower(chinese = "秋", order = "3"))
    register("flower_winter", seasonalFlower(chinese = "冬", order = "4"))

    // 四君子花牌：右上紅色數字、左上黑色中文字，依梅蘭菊竹排序
    register("flower_plum", plantFlower(chinese = "梅", order = "1"))
    register("flower_orchid", plantFlower(chinese = "蘭", order = "2"))
    register("flower_chrysanthemum", plantFlower(chinese = "菊", order = "3"))
    register("flower_bamboo", plantFlower(chinese = "竹", order = "4"))
}

/** 非花牌、非赤牌：只有右上角紅色文字。 */
private fun redText(text: String): TileLabel = TileLabel(
    topLeft = null,
    topRight = TileLabelText(text, TileLabelColor.RED),
)

/** 非花牌的赤牌（赤五、紅中）：牌面本身印刷成紅色，右上角文字改用黑色維持對比。 */
private fun blackTextOnRedTile(text: String): TileLabel = TileLabel(
    topLeft = null,
    topRight = TileLabelText(text, TileLabelColor.BLACK),
)

/** 四季花牌：右上紅色中文字、左上黑色排序數字，永遠顯示（理由見類別 KDoc）。 */
private fun seasonalFlower(chinese: String, order: String): TileLabel = TileLabel(
    topLeft = TileLabelText(order, TileLabelColor.BLACK),
    topRight = TileLabelText(chinese, TileLabelColor.RED),
    forced = true,
)

/** 四君子花牌：右上紅色排序數字、左上黑色中文字，永遠顯示（理由見類別 KDoc）。 */
private fun plantFlower(chinese: String, order: String): TileLabel = TileLabel(
    topLeft = TileLabelText(chinese, TileLabelColor.BLACK),
    topRight = TileLabelText(order, TileLabelColor.RED),
    forced = true,
)
