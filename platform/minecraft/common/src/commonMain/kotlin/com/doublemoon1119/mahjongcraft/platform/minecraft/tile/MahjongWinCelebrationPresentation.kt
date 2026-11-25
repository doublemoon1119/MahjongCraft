package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的胡牌慶祝演出呈現資料。
 *
 * - 只涵蓋贏家自己這側需要重排或倒牌的牌，包含自摸的胡牌張。
 * - 不含角落物件 entity 的生成與清除，角落物件依桌況另外擺放。
 * - 自摸的胡牌張雖然列在 [organizedStandingTileIds] 裡，呈現時仍放在手牌右邊的摸牌位，不插進整理後的立牌。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property seatLayout 這桌玩家席位對應的固定桌面布局。
 * @property seatIndex 贏家在 `TableState.players` 的固定座位 index。
 * @property organizedStandingTileIds 贏家目前立牌依規則模組牌序整理後的目標順序。
 *   - 不論玩家是否啟用自動整理手牌，胡牌慶祝演出一律整理一次。
 *   - 依加入時間軸排列，由實作端反過來對應左右座標。
 *   - 自摸時 [winningTileId] 也在這份清單裡，呈現時另外放在摸牌位。
 *   - 榮和／搶槓時 [winningTileId] 不在清單裡，那張牌仍在放銃者的牌河或副露區。
 * @property handShift 贏家立牌與摸牌位整排往玩家左手邊平移的距離（負值往右手邊），由
 * [MahjongPlayerAreaLayout.handShift] 依副露、移出的牌、桌角物件與牌牆算好。這個方法不重新呈現副露本身。
 * @property winningTileId 胡的那張牌 Uuid。
 *   - 自摸時是原本的摸牌位那張，也列在 [organizedStandingTileIds] 裡。
 *   - 榮和／搶槓時是放銃者打出的捨牌，或被搶槓的加槓／暗槓牌，仍在放銃者或宣告者的區域。
 * @property isTsumo 是否自摸。
 *   - `true`：[winningTileId] 先單獨倒下，再等其餘立牌一起倒下。
 *   - `false`（榮和／搶槓）：[winningTileId] 已經面朝上，省略單獨倒下，直接理牌、等待，再讓立牌一起倒下。
 * @property revealsHand 是否理牌並倒下贏家手牌。為 false 時手牌與 [winningTileId] 都留在原處不動，例如湊齊花牌成立、
 * 與手牌組成無關的和牌。
 * @property earliestStartGameTime 整條演出最早可以開始的絕對 game time，實際起點為 `max(現在, 這個值)`。
 *   - 用於把演出接在某條呈現時間軸的結束之後，讓同一條時間軸上的連續胡牌演出依序播放而不重疊。
 *   - 預設 `0` 代表立刻開始。
 */
data class MahjongWinCelebrationPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val seatLayout: MahjongTableSeatLayout,
    val seatIndex: Int,
    val organizedStandingTileIds: List<Uuid>,
    val handShift: Double,
    val winningTileId: Uuid,
    val isTsumo: Boolean,
    val revealsHand: Boolean,
    val earliestStartGameTime: Long = 0L,
)
