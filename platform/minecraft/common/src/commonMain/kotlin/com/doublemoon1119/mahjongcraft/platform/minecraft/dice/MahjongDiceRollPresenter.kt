package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 將權威擲骰結果呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 呼叫端提供點數、動畫 seed、投入側與序號；entity、開始時間、舊骰替換及版本 API 均由實作處理。
 */
interface MahjongDiceRollPresenter {
    /**
     * 在指定桌面呈現兩顆或三顆正式骰子。
     *
     * @param presentation 擲骰者、位置與伺服器決定的骰子資料。
     * @return 呈現成功或失敗的結果。
     */
    fun present(presentation: MahjongDiceRollPresentation): MahjongDiceRollPresentationResult

    /**
     * 清除指定桌子目前的正式桌面骰子及聚合結果舞台；回傳實際移除的 entity 數量。
     *
     * @param tableId 桌子的穩定 UUID。
     * @param tableLocation 桌子 controller 的世界位置。
     * @return 實際移除的 entity 數量。
     */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
