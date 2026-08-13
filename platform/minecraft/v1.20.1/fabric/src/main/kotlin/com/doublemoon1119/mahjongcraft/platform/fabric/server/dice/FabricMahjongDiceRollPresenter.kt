package com.doublemoon1119.mahjongcraft.platform.fabric.server.dice

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDicePoint
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
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

/** 使用 Fabric 1.20.1 entity 呈現並替換指定麻將桌的正式骰子。 */
@Single(binds = [MahjongDiceRollPresenter::class])
class FabricMahjongDiceRollPresenter(
    private val serverHolder: FabricServerHolder,
) : MahjongDiceRollPresenter {
    /** 驗證 controller 後先建立新骰子；全部成功才移除同桌舊骰子。 */
    override fun present(presentation: MahjongDiceRollPresentation): MahjongDiceRollPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongDiceRollPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return MahjongDiceRollPresentationResult.TABLE_NOT_FOUND
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return MahjongDiceRollPresentationResult.TABLE_NOT_FOUND
        }
        val placements = MahjongDiceTableLayout.placements(
            controllerX = controllerPos.x,
            controllerY = controllerPos.y,
            controllerZ = controllerPos.z,
            tableId = presentation.tableId,
            tableFacing = presentation.tableFacing,
            throwSide = presentation.throwSide,
            rollSequence = presentation.rollSequence,
            diceCount = presentation.dice.size,
        )
        val oldDice = findManagedDice(world, presentation.tableId, controllerPos)
        val newDice = placements.zip(presentation.dice).map { (placement, dicePresentation) ->
            MahjongDiceEntity(world = world).apply {
                refreshPositionAndAngles(
                    placement.finalPosition.x,
                    placement.finalPosition.y,
                    placement.finalPosition.z,
                    world.random.nextFloat() * FULL_YAW_DEGREES,
                    0.0f,
                )
                assignToTable(presentation.tableId)
                startRoll(
                    finalPoint = MahjongDicePoint.fromValueOrDefault(dicePresentation.point),
                    seed = dicePresentation.animationSeed,
                    startGameTime = world.time + placement.startDelayTicks,
                    startOffset = placement.startOffset,
                )
            }
        }
        val spawnedDice = mutableListOf<MahjongDiceEntity>()
        newDice.forEach { dice ->
            if (!world.spawnEntity(dice)) {
                spawnedDice.forEach(MahjongDiceEntity::discard)
                return MahjongDiceRollPresentationResult.SPAWN_FAILED
            }
            spawnedDice += dice
        }
        oldDice.forEach(MahjongDiceEntity::discard)
        table.markDirty()
        return MahjongDiceRollPresentationResult.PRESENTED
    }

    /** 清除指定 controller 周圍且 table UUID 相符的正式骰子。 */
    override fun clear(tableId: Uuid, tableLocation: TableLocation): Int {
        val world = resolveWorld(tableLocation) ?: return 0
        val dice = findManagedDice(world, tableId, tableLocation.toBlockPos())
        dice.forEach(MahjongDiceEntity::discard)
        return dice.size
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
    private fun findManagedDice(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
    ): List<MahjongDiceEntity> = world.getEntitiesByClass(
        MahjongDiceEntity::class.java,
        Box(controllerPos).expand(DICE_SEARCH_HORIZONTAL, DICE_SEARCH_VERTICAL, DICE_SEARCH_HORIZONTAL),
    ) { dice -> dice.managedTableId == tableId }

    /** 將版本無關 table location 轉回 Fabric 方塊座標。 */
    private fun TableLocation.toBlockPos(): BlockPos = BlockPos(x, y, z)

    /** 正式骰子建立與查詢使用的固定參數。 */
    private companion object {
        /** 隨機水平角度的完整範圍。 */
        const val FULL_YAW_DEGREES: Float = 360.0f

        /** controller 周圍查詢正式骰子的水平半徑。 */
        const val DICE_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢正式骰子的垂直半徑。 */
        const val DICE_SEARCH_VERTICAL: Double = 2.0
    }
}
