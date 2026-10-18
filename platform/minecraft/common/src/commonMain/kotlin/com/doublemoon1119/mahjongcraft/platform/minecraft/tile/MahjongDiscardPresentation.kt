package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的單一玩家牌河呈現資料。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property seatLayout 這桌玩家席位對應的固定桌面布局。
 * @property seatIndex 牌河所屬玩家在 `TableState.players` 的固定座位 index。
 * @property discardTileIds 這位玩家目前牌河所有紀錄的牌 Uuid，依捨牌順序排列——順序本身決定牌河
 * 排列位置，不需要另外傳遞位置索引；空清單代表這局結束，只需要清除舊牌。
 * @property sidewaysMarkedTileId [discardTileIds] 之中應側身呈現的那張牌 Uuid；`null` 代表沒有任何
 * 一張需要側身。
 * @property newlyDiscardedTileId [discardTileIds] 之中這次呼叫真正新增的那張牌 Uuid——只有它會播放
 * 「牌從手牌位置面向玩家起飛、隱形傳送到牌河位置、傳送同一瞬間切換成面朝上（含側身旋轉，若
 * [sidewaysMarkedTileId] 相符）、解除隱形後落下」的動畫；其餘既有牌（即使位置因為側身標記轉移而
 * 跟著微調）維持定格顯示，不重複播放。`null` 代表這次呼叫沒有新增捨牌，只是既有牌河重新整理
 * （例如吃/碰/槓走某張捨牌後側身標記位移），所有牌都定格顯示。
 */
data class MahjongDiscardPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val seatLayout: MahjongTableSeatLayout,
    val seatIndex: Int,
    val discardTileIds: List<Uuid>,
    val sidewaysMarkedTileId: Uuid?,
    val newlyDiscardedTileId: Uuid? = null,
)
