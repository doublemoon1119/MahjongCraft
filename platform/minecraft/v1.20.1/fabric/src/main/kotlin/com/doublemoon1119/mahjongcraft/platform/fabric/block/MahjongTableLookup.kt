package com.doublemoon1119.mahjongcraft.platform.fabric.block

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableController
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableControllerState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import net.minecraft.block.entity.BlockEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView
import kotlin.uuid.Uuid

/**
 * 已確認為麻將桌控制點的方塊實體。
 *
 * @property blockEntity 控制方塊的方塊實體。
 * @property controller 同一個方塊實體的麻將桌控制點介面。
 */
internal class LocatedMahjongTable(
    val blockEntity: BlockEntity,
    val controller: MahjongTableController,
) {
    /** 桌子的穩定識別碼。 */
    val tableId: Uuid
        get() = controller.mahjongTableState.tableId

    /** MahjongCraft 保存在方塊實體上的桌子狀態。 */
    val state: MahjongTableControllerState
        get() = controller.mahjongTableState

    /** 控制方塊位置，也是桌子布局的原點。 */
    val pos: BlockPos
        get() = blockEntity.pos

    /** 桌子朝向。 */
    val facing: MahjongTableFacing
        get() = controller.mahjongTableFacing
}

/** 依控制點介面辨識麻將桌，不依賴具體方塊類別。 */
internal object MahjongTableLookup {
    /**
     * 查詢指定位置的麻將桌控制點。
     *
     * @param world 查詢的世界。
     * @param controllerPos 控制方塊位置。
     * @param expectedTableId 預期的桌子 ID；null 時不比對。
     * @return 位置上的方塊實體是控制點且 ID 相符時回傳桌子，否則為 null。
     */
    fun find(
        world: BlockView,
        controllerPos: BlockPos,
        expectedTableId: Uuid? = null,
    ): LocatedMahjongTable? = world.getBlockEntity(controllerPos)?.let { of(it, expectedTableId) }

    /**
     * 將方塊實體辨識為麻將桌控制點。
     *
     * @param blockEntity 要辨識的方塊實體。
     * @param expectedTableId 預期的桌子 ID；null 時不比對。
     * @return 方塊實體是控制點且 ID 相符時回傳桌子，否則為 null。
     */
    fun of(
        blockEntity: BlockEntity,
        expectedTableId: Uuid? = null,
    ): LocatedMahjongTable? {
        val controller = blockEntity as? MahjongTableController ?: return null
        if (expectedTableId != null && controller.mahjongTableState.tableId != expectedTableId) return null
        return LocatedMahjongTable(blockEntity, controller)
    }
}
