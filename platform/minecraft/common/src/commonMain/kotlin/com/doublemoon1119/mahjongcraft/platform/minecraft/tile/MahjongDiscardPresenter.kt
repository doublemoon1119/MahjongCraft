package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 將權威牌河呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 呼叫端提供依捨牌順序排列的牌 Uuid 清單與側身標記；entity 查找、姿態設定、移動位置均由實作處理。
 * 比照 [MahjongPlayerAreaPresenter] 的 best-effort 慣例，牌河是獨立於手牌的呈現區域（多排網格、
 * 躺平姿態、逐張側身判斷），因此獨立成一組 presenter，不塞進 [MahjongPlayerAreaPresenter]。
 *
 * [MahjongTileTableLayout.discardPlacement]
 * 排到第四排以後要不要真的往桌緣方向新增，取決於這位玩家自己那面牆是否還有剩餘牌——這個判斷刻意不
 * 放進 [MahjongDiscardPresentation]（不由呼叫端／domain 層決定），而是由實作自己對世界即時查詢
 * 「這位玩家座位那面牆的世界座標範圍內是否還有管理中的麻將牌」：這個資訊只有平台層有（domain 層從
 * 未持久化牌牆結構跟座位對應面），而且即時查詢天然撐得過伺服器重啟，不需要另外設計持久化或快取。
 */
interface MahjongDiscardPresenter {
    /** 在指定桌面呈現這位玩家的牌河；[MahjongDiscardPresentation.discardTileIds] 為空時等同只清除舊牌。 */
    fun present(presentation: MahjongDiscardPresentation): MahjongDiscardPresentationResult

    /** 清除指定桌子目前的正式牌河用牌；回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
