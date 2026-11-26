package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot

/**
 * 一個移出牌區對目前讀取者的結果，不攜帶完整權威牌張。
 *
 * @property id 區域完整識別碼。
 * @property tiles 依區域內順序排列且已裁切牌面的牌張。
 */
data class PlayerTileAreaSnapshot(
    val id: String,
    val tiles: List<IdentifiedTileSnapshot>,
)
