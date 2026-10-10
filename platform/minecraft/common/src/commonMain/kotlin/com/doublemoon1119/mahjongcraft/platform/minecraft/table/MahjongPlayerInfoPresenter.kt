package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import kotlin.uuid.Uuid

/** Minecraft loader 實作的一桌一 entity 玩家資訊邊界。 */
interface MahjongPlayerInfoPresenter {
    fun present(
        presentation: MahjongPlayerInfoPresentation,
        tableLocation: TableLocation,
        tableFacing: MahjongTableFacing,
    ): MahjongPlayerInfoPresentationResult

    /** 隱藏期限只能延長；找不到 entity 時安全忽略。 */
    fun hideUntil(tableId: Uuid, tableLocation: TableLocation, gameTime: Long)

    /** 清除指定桌子的玩家公開資訊，回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
