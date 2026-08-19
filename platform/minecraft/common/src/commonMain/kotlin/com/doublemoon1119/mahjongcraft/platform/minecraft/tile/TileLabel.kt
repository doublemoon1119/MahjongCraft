package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 牌面角落標籤文字顏色，只保留渲染層需要對應到實際色碼的兩種語意值。 */
enum class TileLabelColor {
    BLACK,
    RED,
}

/** 單一角落顯示的標籤文字與顏色。 */
data class TileLabelText(val text: String, val color: TileLabelColor)

/**
 * 一張牌左上角／右上角的標籤內容；任一角落為 `null` 代表該角落不顯示文字。
 *
 * @property forced 是否無視玩家本機的牌面輔助標籤開關、永遠顯示——花牌八張圖案彼此外觀相近（都是同一
 * 個樹形剪影配不同季節配色，見 `mahjong_tile_flower_*.png`），標籤本身兼有「花色種類＋順序」雙重
 * 辨識功能，不只是給非中文圈玩家看的輔助資訊，因此花牌預設 `forced = true`；其餘牌種維持 `false`，
 * 只在玩家自己開啟開關時才顯示。
 */
data class TileLabel(val topLeft: TileLabelText?, val topRight: TileLabelText?, val forced: Boolean = false)
