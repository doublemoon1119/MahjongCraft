package com.doublemoon1119.mahjongcraft.platform.fabric.server.stick

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickPresenter
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
 * 使用 Fabric 1.20.1 entity 呈現並替換指定麻將桌的正式積棒——比照
 * `FabricMahjongDiceRollPresenter` 的按需生成模式，不是像牌那樣預生成、之後只搬移既有 entity，理由見
 * [MahjongScoringStickPresenter] KDoc。
 */
@Single(binds = [MahjongScoringStickPresenter::class])
class FabricMahjongScoringStickPresenter(
    private val serverHolder: FabricServerHolder,
) : MahjongScoringStickPresenter {
    /** 驗證 controller 後先建立新積棒；全部成功才移除同桌舊積棒。 */
    override fun present(presentation: MahjongScoringStickPresentation): MahjongScoringStickPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongScoringStickPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return MahjongScoringStickPresentationResult.TABLE_NOT_FOUND
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return MahjongScoringStickPresentationResult.TABLE_NOT_FOUND
        }

        val oldSticks = findManagedSticks(world, presentation.tableId, controllerPos)
        val newSticks = (0 until presentation.stickCount).map { stickIndex ->
            val placement = MahjongTileTableLayout.stickPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                seatIndex = presentation.dealerSeatIndex,
                stickIndex = stickIndex,
            )
            MahjongScoringStickEntity(world = world).apply {
                refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
                assignToTable(presentation.tableId)
            }
        }
        val spawnedSticks = mutableListOf<MahjongScoringStickEntity>()
        newSticks.forEach { stick ->
            if (!world.spawnEntity(stick)) {
                spawnedSticks.forEach(MahjongScoringStickEntity::discard)
                return MahjongScoringStickPresentationResult.SPAWN_FAILED
            }
            spawnedSticks += stick
        }
        oldSticks.forEach(MahjongScoringStickEntity::discard)
        table.markDirty()
        return MahjongScoringStickPresentationResult.PRESENTED
    }

    /** 清除指定 controller 周圍且 table UUID 相符的正式積棒。 */
    override fun clear(tableId: Uuid, tableLocation: TableLocation): Int {
        val world = resolveWorld(tableLocation) ?: return 0
        val sticks = findManagedSticks(world, tableId, tableLocation.toBlockPos())
        sticks.forEach(MahjongScoringStickEntity::discard)
        return sticks.size
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
    private fun findManagedSticks(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
    ): List<MahjongScoringStickEntity> = world.getEntitiesByClass(
        MahjongScoringStickEntity::class.java,
        Box(controllerPos).expand(STICK_SEARCH_HORIZONTAL, STICK_SEARCH_VERTICAL, STICK_SEARCH_HORIZONTAL),
    ) { stick -> stick.managedTableId == tableId }

    /** 將版本無關 table location 轉回 Fabric 方塊座標。 */
    private fun TableLocation.toBlockPos(): BlockPos = BlockPos(x, y, z)

    /** 正式積棒建立與查詢使用的固定參數。 */
    private companion object {
        /** controller 周圍查詢正式積棒的水平半徑。 */
        const val STICK_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢正式積棒的垂直半徑。 */
        const val STICK_SEARCH_VERTICAL: Double = 2.0
    }
}
