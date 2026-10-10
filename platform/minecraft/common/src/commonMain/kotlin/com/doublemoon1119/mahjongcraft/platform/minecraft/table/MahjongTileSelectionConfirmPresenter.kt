package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.uuid.Uuid

/**
 * 將多選選牌確認面板呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 每位正在多選選牌的玩家最多同時存在一個確認面板，找不到既有的才生成新的，比照
 * [MahjongRoundInfoPresenter] 的「找到、更新」慣例。
 */
interface MahjongTileSelectionConfirmPresenter {
    /** 在指定桌面、指定玩家座位上方呈現確認面板；找不到既有 entity 時建立一個。 */
    fun present(presentation: MahjongTileSelectionConfirmPresentation): MahjongTileSelectionConfirmPresentationResult

    /** 清除指定玩家的確認面板（選牌送出或情境失效時使用）；回傳實際移除數量。 */
    fun clearForPlayer(tableId: Uuid, tableLocation: TableLocation, holderId: Uuid): Int

    /** 清除指定桌子（任何持有者）目前的確認面板；桌子被移除時使用，回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
