package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的胡牌慶祝演出呈現資料——只涵蓋贏家自己這側需要重排／倒牌的立牌，不含摸牌位（贏家
 * 手牌到此已經沒有懸而未決的摸牌位：自摸的胡牌張已經併入 [organizedStandingTileIds]，榮和／搶槓則
 * 從頭就沒有摸牌位），也不含角落物件 entity 本身生成／清除（理由同 [MahjongPlayerAreaPresentation]）。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property seatLayout 這桌玩家席位對應的固定桌面布局。
 * @property seatIndex 贏家在 `TableState.players` 的固定座位 index。
 * @property organizedStandingTileIds 贏家目前立牌，已依規則模組的牌序整理過的目標順序（不論玩家原本
 * 是否啟用自動整理手牌，胡牌慶祝演出一律強制整理一次）——跟 [MahjongPlayerAreaPresentation.standingTileIds]
 * 同一套「加入時間軸、由實作端反過來對應左右座標」的慣例；自摸時 [winningTileId] 已經併入這份清單
 * （原本的摸牌位併入立牌），榮和／搶槓時 [winningTileId] 不在這份清單裡（那張牌仍在放銃者的牌河或
 * 副露區，不屬於贏家手牌）。
 * @property melds 贏家目前所有副露，排列規則同 [MahjongPlayerAreaPresentation.melds]，只用來正確計算
 * 立牌讓開副露的偏移，這個方法不重新呈現副露本身（副露的位置/姿態不受胡牌影響）。
 * @property cornerWidth 贏家副露角落被佔用的寬度，只用來換算讓開偏移，理由同 [MahjongPlayerAreaPresentation.cornerWidth]。
 * @property setAsideTileCount 贏家移出手牌、接在副露之後擺放的牌張數（例如三人日麻拔出的北），只用來換算讓開偏移。
 * @property winningTileId 胡的那張牌 Uuid——自摸時是原本的摸牌位那張，此時已併入 [organizedStandingTileIds]；
 * 榮和／搶槓時是放銃者打出的那張捨牌，或搶槓來源的那張加槓/暗槓牌，仍在放銃者／宣告者的區域。
 * @property isTsumo `true` 代表自摸（[winningTileId] 需要先單獨倒下、再等其餘立牌一起倒下）；`false`
 * 代表榮和／搶槓（[winningTileId] 早已是面朝上姿態，省略單獨倒下這一步，直接進入「強制理牌 → 等待 →
 * 立牌一起倒下」）。
 * @property earliestStartGameTime 整條演出最早可以開始的絕對 game time；實際起點為
 * `max(現在, 這個值)`。用於把演出接在某條呈現時間軸的結束時間之後，讓同一條時間軸上的連續胡牌演出
 * 依序播放而不重疊。預設 `0` 等同「立刻開始」，維持既有呼叫端的行為不變。
 */
data class MahjongWinCelebrationPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val seatLayout: MahjongTableSeatLayout,
    val seatIndex: Int,
    val organizedStandingTileIds: List<Uuid>,
    val melds: List<MahjongMeldTileGroup>,
    val cornerWidth: Double,
    val setAsideTileCount: Int,
    val winningTileId: Uuid,
    val isTsumo: Boolean,
    val earliestStartGameTime: Long = 0L,
)
