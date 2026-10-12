package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresenter
import kotlin.uuid.Uuid

/**
 * 將桌面中央局況顯示呈現於 Minecraft 世界的版本 adapter 邊界——實際顯示什麼內容由 Minecraft 規則
 * 呈現 provider 決定，這裡不假設任何固定欄位。
 *
 * 每張桌子固定只有一個常駐 entity，找不到既有的才生成新的（不是每次都清除重建，比照
 * [MahjongTileWallPresenter] 的「找到、更新」慣例——局況顯示沒有「整批換新」的需求，通常只是文字內容
 * 更新）。
 */
interface MahjongRoundInfoPresenter {
    /**
     * 在指定桌面呈現目前局況；找不到既有 entity 時建立一個。
     *
     * @param presentation 已由伺服器決定的桌面局況資料。
     * @return 局況顯示呈現結果。
     */
    fun present(presentation: MahjongRoundInfoPresentation): MahjongRoundInfoPresentationResult

    /**
     * 清除指定桌子目前的局況顯示。
     *
     * @param tableId 麻將桌 UUID。
     * @param tableLocation 麻將桌目前位置。
     * @return 實際移除的 entity 數量。
     */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
