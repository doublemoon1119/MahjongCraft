package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定要生成的多選選牌（`maxCount > 1`）確認面板。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property seatLayout 這桌玩家席位對應的固定桌面布局。
 * @property seatIndex 決策玩家在 `TableState.players` 的固定座位 index，決定面板要擺在哪個座位的手牌
 * 上方。
 * @property holderId 這個面板屬於哪位玩家；只有這位玩家的 client 會實際畫出來，見
 * `MahjongTileSelectionConfirmEntity` KDoc 的取捨說明。
 */
data class MahjongTileSelectionConfirmPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val seatLayout: MahjongTableSeatLayout,
    val seatIndex: Int,
    val holderId: Uuid,
)
