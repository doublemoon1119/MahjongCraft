package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 將權威牌牆結構呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 呼叫端提供結構座標與莊家座位；entity、UUID 對齊、舊牌替換及版本 API 均由實作處理。
 * 比照 [MahjongDiceRollPresenter] 的 best-effort 慣例。
 */
interface MahjongTileWallPresenter {
    /** 在指定桌面呈現整副牌牆；[MahjongTileWallPresentation.finalLayout] 為空時等同只清除舊牌。 */
    fun present(presentation: MahjongTileWallPresentation): MahjongTileWallPresentationResult

    /** 在既有牌牆上排入開門整理及初始公開牌翻面動畫。 */
    fun presentOpening(presentation: MahjongTileWallOpeningPresentation): MahjongTileWallPresentationResult

    /** 將權威 transition 原子排入指定桌子的既有牌牆 entity。 */
    fun presentTransition(presentation: MahjongTileWallTransitionPresentation): MahjongTileWallTransitionResult

    /**
     * 把 [revealedTileIds] 對應的既有王牌 entity 姿態改成正面朝上，其餘管理中的王牌不受影響——用於
     * 牌牆建立之後才追加公開的牌（例如日麻槓牌後翻開新的寶牌指示牌），跟 [present] 開局那次的初始
     * 公開（[MahjongTileWallPresentation.revealedTileIds]）是兩條獨立的時機，理由見
     * `GamePresentationPublisher.publishWallTilesRevealed` KDoc。冪等：重複呼叫同一批 id 沒有
     * 副作用。
     *
     * @param revealedTileIds 本次新增公開翻面的牌 Uuid 集合；實作只處理這一批，重複 ID 仍須保持冪等。
     * @return 找不到對應 entity 的張數；比照本介面 best-effort 慣例，找不到的牌會被跳過。
     */
    fun revealDeadWallTiles(tableId: Uuid, tableLocation: TableLocation, revealedTileIds: Set<Uuid>): MahjongTileWallPresentationResult

    /** 清除指定桌子目前的正式牌牆用牌；回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
