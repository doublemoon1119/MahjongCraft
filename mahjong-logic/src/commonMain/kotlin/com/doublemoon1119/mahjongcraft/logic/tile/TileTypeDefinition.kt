package com.doublemoon1119.mahjongcraft.logic.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId

/**
 * 宣告 runtime 可辨識的擴充麻將牌種類。
 *
 * 此定義目前只建立 [id] 的註冊邊界。牌山張數、排序、補花行為與平台資源不屬於所有規則共用的
 * 固定屬性，後續應由規則 tile set、規則 policy 與平台 adapter 分別提供。
 *
 * @property id 跨 network 與 persistence 保持穩定的牌種識別碼。
 */
data class TileTypeDefinition(val id: TileTypeId)
