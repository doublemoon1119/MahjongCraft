package com.doublemoon1119.mahjongcraft.platform.fabric.server.dice

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.DiceRollPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDicePoint
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceRollAnimationSpec
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
import kotlin.uuid.Uuid

/**
 * 使用實體呈現擲骰與結果，成功建立後替換指定麻將桌的舊骰子。
 *
 * @property serverHolder 取得目前 server session 與世界。
 * @property spawnGateway 執行並診斷正式 entity 生成。
 */
class FabricMahjongDiceRollPresenter(
    private val serverHolder: FabricServerHolder,
    private val spawnGateway: FabricEntitySpawnGateway,
) : MahjongDiceRollPresenter {
    /**
     * 驗證 controller 後先建立新骰子；全部成功才移除同桌舊骰子。
     *
     * @param presentation 權威骰子結果、桌位與投擲時間資料。
     * @return 已呈現、找不到相符牌桌，或實體生成失敗的結果。
     */
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
        val oldStages = findManagedStages(world, presentation.tableId, controllerPos)
        val revealGameTime = world.time + presentation.extraLeadDelayTicks +
            MahjongDiceTableLayout.maxStartDelayTicks(presentation.dice.size) + DiceRollAnimationSpec.DEFAULT_DURATION_TICKS
        val endGameTime = revealGameTime + DiceRollAnimationSpec.EXTRA_VIEWING_TICKS
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
                    startDelayTicks = presentation.extraLeadDelayTicks + placement.startDelayTicks,
                    startOffset = placement.startOffset,
                    sharedViewingEndGameTime = endGameTime,
                )
            }
        }
        val stage = DiceRollPresentationEntity(world = world).apply {
            refreshPositionAndAngles(
                controllerPos.x + BLOCK_CENTER,
                controllerPos.y + TABLETOP_HEIGHT + DiceRollPresentationEntity.HEIGHT_ABOVE_TABLETOP,
                controllerPos.z + BLOCK_CENTER,
                0.0f,
                0.0f,
            )
            configure(
                tableId = presentation.tableId,
                points = presentation.dice.map { it.point },
                revealGameTime = revealGameTime,
                endGameTime = endGameTime,
            )
        }
        val spawnedDice = mutableListOf<MahjongDiceEntity>()
        newDice.forEach { dice ->
            if (!spawnGateway.spawn(world, dice, "dice-roll-die", presentation.tableId)) {
                spawnedDice.forEach(MahjongDiceEntity::discard)
                return MahjongDiceRollPresentationResult.SPAWN_FAILED
            }
            spawnedDice += dice
        }
        if (!spawnGateway.spawn(world, stage, "dice-roll-stage", presentation.tableId)) {
            spawnedDice.forEach(MahjongDiceEntity::discard)
            return MahjongDiceRollPresentationResult.SPAWN_FAILED
        }
        oldDice.forEach(MahjongDiceEntity::discard)
        oldStages.forEach(DiceRollPresentationEntity::discard)
        table.markDirty()
        return MahjongDiceRollPresentationResult.PRESENTED
    }

    /**
     * 清除指定 controller 周圍且 table UUID 相符的正式骰子。
     *
     * @param tableId 所屬麻將桌 UUID。
     * @param tableLocation 桌子所在 dimension 與方塊位置。
     * @return 移除的骰子與結果舞台 entity 總數。
     */
    override fun clear(tableId: Uuid, tableLocation: TableLocation): Int {
        val world = resolveWorld(tableLocation) ?: return 0
        val dice = findManagedDice(world, tableId, tableLocation.toBlockPos())
        val stages = findManagedStages(world, tableId, tableLocation.toBlockPos())
        dice.forEach(MahjongDiceEntity::discard)
        stages.forEach(DiceRollPresentationEntity::discard)
        return dice.size + stages.size
    }

    /**
     * 依位置中的維度識別碼取得目前伺服器世界。
     *
     * @param location 包含維度識別碼的桌子位置。
     * @return 相符世界；識別碼無效、伺服器不存在或世界未載入時為 null。
     */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
        return serverHolder.current()?.getWorld(worldKey)
    }

    /**
     * 驗證指定位置是身分相符且具水平朝向資料的桌子控制方塊。
     *
     * @param world 桌子所在世界。
     * @param controllerPos 控制方塊座標。
     * @param state 該位置目前的方塊狀態。
     * @param tableId 預期的麻將桌 UUID。
     * @return 相符的桌子方塊實體；結構、朝向欄位或身分不符時為 null。
     */
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
     * 查詢桌子附近且所屬桌子 UUID 相符的骰子，不掃描整個維度。
     *
     * @param world 要查詢的世界。
     * @param tableId 預期的麻將桌 UUID。
     * @param controllerPos 查詢範圍中心的控制方塊座標。
     * @return 範圍內屬於該桌的骰子實體。
     */
    private fun findManagedDice(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
    ): List<MahjongDiceEntity> = world.getEntitiesByClass(
        MahjongDiceEntity::class.java,
        Box(controllerPos).expand(DICE_SEARCH_HORIZONTAL, DICE_SEARCH_VERTICAL, DICE_SEARCH_HORIZONTAL),
    ) { dice -> dice.managedTableId == tableId }

    /**
     * 查詢桌子附近且所屬桌子 UUID 相符的擲骰結果舞台。
     *
     * @param world 要查詢的世界。
     * @param tableId 預期的麻將桌 UUID。
     * @param controllerPos 查詢範圍中心的控制方塊座標。
     * @return 範圍內屬於該桌的結果舞台。
     */
    private fun findManagedStages(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
    ): List<DiceRollPresentationEntity> = world.getEntitiesByClass(
        DiceRollPresentationEntity::class.java,
        Box(controllerPos).expand(DICE_SEARCH_HORIZONTAL, DICE_SEARCH_VERTICAL, DICE_SEARCH_HORIZONTAL),
    ) { stage -> stage.managedTableId == tableId }

    /** @return 桌子位置對應的 Minecraft 方塊座標，不含維度資訊。 */
    private fun TableLocation.toBlockPos(): BlockPos = BlockPos(x, y, z)

    /** 正式骰子建立與查詢使用的固定參數。 */
    private companion object {
        /** 隨機水平角度的完整範圍。 */
        const val FULL_YAW_DEGREES: Float = 360.0f

        /** controller 周圍查詢正式骰子的水平半徑。 */
        const val DICE_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢正式骰子的垂直半徑。 */
        const val DICE_SEARCH_VERTICAL: Double = 2.0

        /** 方塊中心座標偏移。 */
        const val BLOCK_CENTER: Double = 0.5

        /** 麻將桌桌面相對 controller 的高度。 */
        const val TABLETOP_HEIGHT: Double = 1.0
    }
}
