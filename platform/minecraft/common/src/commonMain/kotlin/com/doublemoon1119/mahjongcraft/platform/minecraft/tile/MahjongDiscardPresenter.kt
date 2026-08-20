package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的單一玩家牌河呈現資料。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
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
    val seatIndex: Int,
    val discardTileIds: List<Uuid>,
    val sidewaysMarkedTileId: Uuid?,
    val newlyDiscardedTileId: Uuid? = null,
)

/** 正式牌河呈現請求的處理結果。 */
enum class MahjongDiscardPresentationResult {
    /** 已把這位玩家的牌河更新為本次要呈現的牌（或 [MahjongDiscardPresentation.discardTileIds] 為空、只清除舊牌）。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /**
     * 其中一張以上的牌找不到對應的既有 entity 可以領走——牌河的牌應該早在牌牆生成時就已經存在
     * （同一個 UUID），這裡不重新建立新 entity，只做「找到、改標記、移動」；找不到通常代表遊戲
     * 狀態跟世界狀態已經不一致。找不到的牌會被跳過，其餘牌仍照常呈現，不是整批回滾。
     */
    SPAWN_FAILED,
}

/**
 * 將權威牌河呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 呼叫端提供依捨牌順序排列的牌 Uuid 清單與側身標記；entity 查找、姿態設定、移動位置均由實作處理。
 * 比照 [MahjongHandTilesPresenter] 的 best-effort 慣例，牌河是獨立於手牌的呈現區域（多排網格、
 * 躺平姿態、逐張側身判斷），因此獨立成一組 presenter，不塞進 [MahjongHandTilesPresenter]。
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
