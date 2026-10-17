package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
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
    /**
     * 在指定桌面呈現整副牌牆；[MahjongTileWallPresentation.finalLayout] 為空時等同只清除舊牌。
     *
     * @param presentation 已由規則決定的牌牆呈現資料。
     * @return 牌牆呈現結果。
     */
    fun present(presentation: MahjongTileWallPresentation): MahjongTileWallPresentationResult

    /**
     * 在既有牌牆上排入開門整理及初始公開牌翻面動畫。
     *
     * @param presentation 開門與初始翻面所需的資料。
     * @return 牌牆呈現結果。
     */
    fun presentOpening(presentation: MahjongTileWallOpeningPresentation): MahjongTileWallPresentationResult

    /**
     * 將權威 transition 原子排入指定桌子的既有牌牆 entity。
     *
     * @param presentation 已由規則解析完成的 transition 資料。
     * @return transition 呈現結果。
     */
    fun presentTransition(presentation: MahjongTileWallTransitionPresentation): MahjongTileWallTransitionResult

    /**
     * 將既有牌牆 entity 同步到目前的權威牌牆布局，不建立或移除任何 entity。
     *
     * @param tableId 麻將桌 UUID。
     * @param tableLocation 麻將桌目前位置。
     * @param context 將抽象牌牆格位投影到此桌世界座標的資料。
     * @param layout 目前權威牌牆中每張牌的格位。
     * @param revealedTileIds 目前應翻面的牌張 UUID；其他牌維持蓋牌。
     * @return 所有布局牌張都存在並完成同步時為 [MahjongTileWallPresentationResult.PRESENTED]；桌子無效或牌張缺失時
     *   回傳對應錯誤，且不做部分同步。
     */
    fun synchronizeLayout(
        tableId: Uuid,
        tableLocation: TableLocation,
        context: MahjongTileWallProjectionContext,
        layout: TileWallPhysicalLayout,
        revealedTileIds: Set<Uuid>,
    ): MahjongTileWallPresentationResult

    /**
     * 將 [revealedTileIds] 對應的既有王牌 entity 翻至正面，不影響其他管理中的王牌。重複呼叫相同 ID
     * 不會改變結果。
     *
     * @param tableId 麻將桌 UUID。
     * @param tableLocation 麻將桌目前位置。
     * @param revealedTileIds 本次新增公開翻面的牌 Uuid 集合；實作只處理這一批，重複 ID 仍須保持冪等。
     * @return 找不到對應 entity 的張數；比照本介面 best-effort 慣例，找不到的牌會被跳過。
     */
    fun revealDeadWallTiles(tableId: Uuid, tableLocation: TableLocation, revealedTileIds: Set<Uuid>): MahjongTileWallPresentationResult

    /**
     * 清除指定桌子目前的正式牌牆用牌。
     *
     * @param tableId 麻將桌 UUID。
     * @param tableLocation 麻將桌目前位置。
     * @return 實際移除的 entity 數量。
     */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
