package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileSelectionConfirmEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTileSelectionConfirmPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTileSelectionConfirmPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTileSelectionConfirmPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import net.minecraft.block.BlockState
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 使用 Fabric 1.20.1 entity 呈現多選選牌確認面板。跟 [FabricMahjongRoundInfoPresenter] 不同，這裡
 * 一次要生成／清除一整組 [MahjongTileTableLayout.TILE_SELECTION_CONFIRM_SEGMENT_COUNT] 個 entity
 * （見 `MahjongTileSelectionConfirmEntity` 類別 KDoc「多個 instance 拼湊」的設計），所以每次 [present]
 * 一律先清掉這位玩家既有的整組、再重新生成一組全新的，不採用「找到既有的就更新」模式——這個決策目前
 * 只在多選選牌開始時觸發一次，不會頻繁呼叫，重新生成的成本可以忽略。
 */
@Single(binds = [MahjongTileSelectionConfirmPresenter::class])
class FabricMahjongTileSelectionConfirmPresenter(
    private val serverHolder: FabricServerHolder,
) : MahjongTileSelectionConfirmPresenter {
    override fun present(presentation: MahjongTileSelectionConfirmPresentation): MahjongTileSelectionConfirmPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongTileSelectionConfirmPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return MahjongTileSelectionConfirmPresentationResult.TABLE_NOT_FOUND
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return MahjongTileSelectionConfirmPresentationResult.TABLE_NOT_FOUND
        }

        findManagedPanelsForHolder(world, presentation.tableId, controllerPos, presentation.holderId).forEach { it.discard() }

        val primarySegmentIndex = (MahjongTileTableLayout.TILE_SELECTION_CONFIRM_SEGMENT_COUNT - 1) / 2
        val spawned = mutableListOf<MahjongTileSelectionConfirmEntity>()
        repeat(MahjongTileTableLayout.TILE_SELECTION_CONFIRM_SEGMENT_COUNT) { segmentIndex ->
            val placement = MahjongTileTableLayout.tileSelectionConfirmPlacement(
                controllerPos.x,
                controllerPos.y,
                controllerPos.z,
                presentation.tableFacing,
                presentation.seatIndex,
                segmentIndex,
            )
            val panel = MahjongTileSelectionConfirmEntity(world = world).apply {
                assignToHolder(
                    tableId = presentation.tableId,
                    playerId = presentation.holderId,
                    segmentIndex = segmentIndex,
                    isPrimary = segmentIndex == primarySegmentIndex,
                )
                refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
            }
            if (!world.spawnEntity(panel)) {
                spawned.forEach { it.discard() }
                return MahjongTileSelectionConfirmPresentationResult.SPAWN_FAILED
            }
            spawned += panel
        }
        return MahjongTileSelectionConfirmPresentationResult.PRESENTED
    }

    override fun clearForPlayer(tableId: Uuid, tableLocation: TableLocation, holderId: Uuid): Int {
        val world = resolveWorld(tableLocation) ?: return 0
        val controllerPos = tableLocation.toBlockPos()
        val panels = findManagedPanelsForHolder(world, tableId, controllerPos, holderId)
        panels.forEach { it.discard() }
        return panels.size
    }

    override fun clear(tableId: Uuid, tableLocation: TableLocation): Int {
        val world = resolveWorld(tableLocation) ?: return 0
        val controllerPos = tableLocation.toBlockPos()
        val panels = findManagedPanels(world, tableId, controllerPos)
        panels.forEach { it.discard() }
        return panels.size
    }

    /** 由版本無關 dimension ID 取得目前 server session 的世界。 */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
        return serverHolder.current()?.getWorld(worldKey)
    }

    /** 驗證指定位置確實是 UUID 與朝向資料可用的 controller。 */
    private fun resolveTable(
        world: ServerWorld,
        controllerPos: BlockPos,
        state: BlockState,
        tableId: Uuid,
    ): MahjongTableBlockEntity? {
        if (state.block !is MahjongTableBlock || state.get(MahjongTableBlock.PART) != MahjongTablePart.BOTTOM_CENTER) {
            return null
        }
        if (!state.contains(Properties.HORIZONTAL_FACING)) return null
        return (world.getBlockEntity(controllerPos) as? MahjongTableBlockEntity)?.takeIf { table ->
            table.tableId == tableId
        }
    }

    /**
     * 只查詢桌子結構附近並以同步 UUID／持有玩家精確篩選，避免掃描整個 dimension。回傳這位玩家目前
     * 那一組拼湊面板的所有 instance（見 `MahjongTileSelectionConfirmEntity` 類別 KDoc）。
     */
    private fun findManagedPanelsForHolder(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
        holderId: Uuid,
    ): List<MahjongTileSelectionConfirmEntity> = world.getEntitiesByClass(
        MahjongTileSelectionConfirmEntity::class.java,
        searchBox(controllerPos),
    ) { panel -> panel.managedTableId == tableId && panel.holderId == holderId }

    /** 只查詢桌子結構附近並以同步 UUID 精確篩選（任何持有者），避免掃描整個 dimension。 */
    private fun findManagedPanels(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
    ): List<MahjongTileSelectionConfirmEntity> = world.getEntitiesByClass(
        MahjongTileSelectionConfirmEntity::class.java,
        searchBox(controllerPos),
    ) { panel -> panel.managedTableId == tableId }

    private fun searchBox(pos: BlockPos) = Box(pos).expand(
        TableOverlayEntitySearch.HORIZONTAL_RADIUS,
        TableOverlayEntitySearch.VERTICAL_RADIUS,
        TableOverlayEntitySearch.HORIZONTAL_RADIUS,
    )

    /** 將版本無關 table location 轉回 Fabric 方塊座標。 */
    private fun TableLocation.toBlockPos(): BlockPos = BlockPos(x, y, z)
}
