package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresenter
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
import kotlin.uuid.toJavaUuid

/** 使用 Fabric 1.20.1 entity 呈現並替換指定麻將桌的正式牌牆。 */
@Single(binds = [MahjongTileWallPresenter::class])
class FabricMahjongTileWallPresenter(
    private val serverHolder: FabricServerHolder,
) : MahjongTileWallPresenter {
    /**
     * 驗證 controller 後先建立新牌；全部成功才移除同桌舊牌。[MahjongTileWallPresentation.structure]
     * 為空（例如對局結束）時，跳過建立步驟直接視為成功，等同只清除舊牌——重用同一段替換邏輯，不需要
     * 另外開一個清除專用的呼叫路徑。
     *
     * 每張新牌 entity 的 UUID 在加入世界前直接設為對應 [com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile.id]，
     * 沿用 [MahjongTileEntity] 既有 KDoc 早已寫下的設計意圖——此時 entity 尚未加入 world 的 UUID
     * 索引，是唯一安全能覆寫 UUID 的時機點；`world.spawnEntity` 之後才變更會與世界既有索引不一致。
     */
    override fun present(presentation: MahjongTileWallPresentation): MahjongTileWallPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongTileWallPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return MahjongTileWallPresentationResult.TABLE_NOT_FOUND
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return MahjongTileWallPresentationResult.TABLE_NOT_FOUND
        }

        val stacksPerSide = presentation.structure.values
            .filter { position -> position.side == 0 }
            .maxOfOrNull { position -> position.stack + 1 } ?: 0
        val oldTiles = findManagedTiles(world, presentation.tableId, controllerPos)
        val newTiles = presentation.structure.map { (tileId, position) ->
            val placement = MahjongTileTableLayout.wallPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                dealerSeatIndex = presentation.dealerSeatIndex,
                stacksPerSide = stacksPerSide,
                position = position,
            )
            MahjongTileEntity(world = world).apply {
                uuid = tileId.toJavaUuid()
                refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
                tilePose = MahjongTilePose.FACE_DOWN
                assignToTable(presentation.tableId)
            }
        }
        val spawnedTiles = mutableListOf<MahjongTileEntity>()
        newTiles.forEach { tile ->
            if (!world.spawnEntity(tile)) {
                spawnedTiles.forEach(MahjongTileEntity::discard)
                return MahjongTileWallPresentationResult.SPAWN_FAILED
            }
            spawnedTiles += tile
        }
        oldTiles.forEach(MahjongTileEntity::discard)
        table.markDirty()
        return MahjongTileWallPresentationResult.PRESENTED
    }

    /** 清除指定 controller 周圍且 table UUID 相符的正式牌牆用牌。 */
    override fun clear(tableId: Uuid, tableLocation: TableLocation): Int {
        val world = resolveWorld(tableLocation) ?: return 0
        val tiles = findManagedTiles(world, tableId, tableLocation.toBlockPos())
        tiles.forEach(MahjongTileEntity::discard)
        return tiles.size
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

    /** 只查詢桌子結構附近並以同步 UUID 精確篩選，避免掃描整個 dimension。 */
    private fun findManagedTiles(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
    ): List<MahjongTileEntity> = world.getEntitiesByClass(
        MahjongTileEntity::class.java,
        Box(controllerPos).expand(TILE_SEARCH_HORIZONTAL, TILE_SEARCH_VERTICAL, TILE_SEARCH_HORIZONTAL),
    ) { tile -> tile.managedTableId == tableId }

    /** 將版本無關 table location 轉回 Fabric 方塊座標。 */
    private fun TableLocation.toBlockPos(): BlockPos = BlockPos(x, y, z)

    /** 正式牌牆建立與查詢使用的固定參數。 */
    private companion object {
        /** controller 周圍查詢正式牌牆用牌的水平半徑。 */
        const val TILE_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢正式牌牆用牌的垂直半徑。 */
        const val TILE_SEARCH_VERTICAL: Double = 2.0
    }
}
