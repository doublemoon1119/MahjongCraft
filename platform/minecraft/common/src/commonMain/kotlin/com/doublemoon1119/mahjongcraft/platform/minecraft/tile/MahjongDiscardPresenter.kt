package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 將權威牌河呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 呼叫端提供依捨牌順序排列的牌 UUID 與側身標記。實作負責依 [MahjongTileTableLayout.discardPlacement]
 * 計算位置，並處理 entity 查找、姿態設定與移動。
 */
interface MahjongDiscardPresenter {
    /**
     * 在指定桌面呈現這位玩家的牌河；[MahjongDiscardPresentation.discardTileIds] 為空時等同只清除舊牌。
     *
     * @param presentation 已由伺服器決定的牌河呈現資料。
     * @return 牌河呈現結果。
     */
    fun present(presentation: MahjongDiscardPresentation): MahjongDiscardPresentationResult

    /**
     * 清除指定桌子目前的正式牌河用牌。
     *
     * @param tableId 麻將桌 UUID。
     * @param tableLocation 麻將桌目前位置。
     * @return 實際移除的 entity 數量。
     */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
