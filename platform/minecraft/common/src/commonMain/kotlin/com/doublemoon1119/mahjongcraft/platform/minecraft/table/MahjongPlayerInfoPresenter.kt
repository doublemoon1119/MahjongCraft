package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import kotlin.uuid.Uuid

/** Minecraft loader 實作的一桌一 entity 玩家資訊邊界。 */
interface MahjongPlayerInfoPresenter {
    /**
     * 建立或更新桌面的玩家公開資訊 entity。
     *
     * @param presentation 玩家資訊快照。
     * @param tableLocation 麻將桌 controller 位置。
     * @param tableFacing 麻將桌世界朝向。
     * @return entity 呈現結果。
     */
    fun present(
        presentation: MahjongPlayerInfoPresentation,
        tableLocation: TableLocation,
        tableFacing: MahjongTableFacing,
    ): MahjongPlayerInfoPresentationResult

    /**
     * 延長玩家公開資訊的隱藏期限；找不到 entity 時安全忽略。
     *
     * @param tableId 麻將桌 UUID。
     * @param tableLocation 麻將桌目前位置。
     * @param gameTime 隱藏期限的絕對遊戲時間。
     */
    fun hideUntil(tableId: Uuid, tableLocation: TableLocation, gameTime: Long)

    /**
     * 清除指定桌子的玩家公開資訊。
     *
     * @param tableId 麻將桌 UUID。
     * @param tableLocation 麻將桌目前位置。
     * @return 實際移除的 entity 數量。
     */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
