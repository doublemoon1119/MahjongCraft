package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 一張牌左上角／右上角的標籤內容；任一角落為 `null` 代表該角落不顯示文字。
 *
 * @property topLeft 左上角的標籤；不顯示時為 `null`。
 * @property topRight 右上角的標籤；不顯示時為 `null`。
 * @property forced 是否無視玩家本機的牌面輔助標籤開關、永遠顯示——花牌八張圖案彼此外觀相近（都是同一
 * 個樹形剪影配不同季節配色，見 `mahjong_tile_flower_*.png`），標籤本身兼有「花色種類＋順序」雙重
 * 辨識功能，不只是給非中文圈玩家看的輔助資訊，因此花牌預設 `forced = true`；其餘牌種維持 `false`，
 * 只在玩家自己開啟開關時才顯示。
 */
data class TileLabel(
    val topLeft: TileLabelText?,
    val topRight: TileLabelText?,
    val forced: Boolean = false,
)
