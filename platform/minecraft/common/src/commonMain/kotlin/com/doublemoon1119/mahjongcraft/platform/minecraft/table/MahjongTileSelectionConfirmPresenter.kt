package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定要生成的多選選牌（`maxCount > 1`）確認面板。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property seatIndex 決策玩家在 `TableState.players` 的固定座位 index，決定面板要擺在哪個座位的手牌
 * 上方。
 * @property holderId 這個面板屬於哪位玩家；只有這位玩家的 client 會實際畫出來，見
 * `MahjongTileSelectionConfirmEntity` KDoc 的取捨說明。
 */
data class MahjongTileSelectionConfirmPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val seatIndex: Int,
    val holderId: Uuid,
)

/** 選牌確認面板呈現請求的處理結果。 */
enum class MahjongTileSelectionConfirmPresentationResult {
    /** 已生成或更新這位玩家的確認面板。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 生成確認面板 entity 失敗（例如世界拒絕 spawn）。 */
    SPAWN_FAILED,
}

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
