package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import kotlin.uuid.Uuid

/**
 * 一張桌子此刻應有的全部規則桌面物件。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property seatLayout 這桌玩家席位對應的固定桌面布局。
 * @property placements 規則描述的完整物件清單；空清單代表桌上不應有任何規則桌面物件。
 */
data class TablePropPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val seatLayout: MahjongTableSeatLayout,
    val placements: List<TablePropPlacement>,
) {
    /**
     * 依桌子位置與朝向，把每個座位本地描述換算成世界落點，順序與 [placements] 相同。
     *
     * @return 對應每筆描述的世界物件目標。
     */
    fun targets(): List<TablePropTarget> = placements.map { placement ->
        val world = MahjongTileTableLayout.seatAnchorPlacement(
            controllerX = tableLocation.x,
            controllerY = tableLocation.y,
            controllerZ = tableLocation.z,
            tableFacing = tableFacing,
            seatLayout = seatLayout,
            seatIndex = placement.seatIndex,
            anchor = placement.anchor,
            offset = placement.offset,
            yawOffset = placement.yawOffset,
        )
        TablePropTarget(
            kind = placement.kind,
            variant = placement.variant,
            x = world.x,
            y = world.y,
            z = world.z,
            yaw = world.yaw,
        )
    }
}
