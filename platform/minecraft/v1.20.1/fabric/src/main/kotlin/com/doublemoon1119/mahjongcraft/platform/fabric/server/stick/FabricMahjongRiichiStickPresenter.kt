package com.doublemoon1119.mahjongcraft.platform.fabric.server.stick

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickDenomination
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongRiichiStickPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongRiichiStickPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongRiichiStickPresenter
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
 * 使用 Fabric 1.20.1 entity 呈現並替換指定麻將桌的正式立直棒——跟
 * [FabricMahjongScoringStickPresenter] 共用同一個「全部生成成功才丟棄舊的」按需生成模式與同一個
 * [MahjongScoringStickEntity] 類型（同一個 entity 類型同時代表積棒與立直棒，見該類型 KDoc），只是
 * 這裡的呈現對象合併兩種來源——目前立直中的座位（`riichiStickPlacement`）與延續自前局、跟積棒疊在
 * 莊家角落的供託堆（`stickPlacement`，跟 [FabricMahjongScoringStickPresenter] 共用同一個排列函式）——
 * 見 [MahjongRiichiStickPresenter] KDoc。
 */
@Single(binds = [MahjongRiichiStickPresenter::class])
class FabricMahjongRiichiStickPresenter(
    private val serverHolder: FabricServerHolder,
) : MahjongRiichiStickPresenter {
    /** 驗證 controller 後先建立新立直棒；全部成功才移除同桌舊立直棒。 */
    override fun present(presentation: MahjongRiichiStickPresentation): MahjongRiichiStickPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongRiichiStickPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return MahjongRiichiStickPresentationResult.TABLE_NOT_FOUND
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return MahjongRiichiStickPresentationResult.TABLE_NOT_FOUND
        }

        val oldSticks = findManagedSticks(world, presentation.tableId, controllerPos)
        val declaredSticks = presentation.riichiSeatIndices.map { seatIndex ->
            val placement = MahjongTileTableLayout.riichiStickPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                seatIndex = seatIndex,
            )
            MahjongScoringStickEntity(world = world).apply {
                refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
                denomination = MahjongScoringStickDenomination.P1000
                assignToTable(presentation.tableId)
            }
        }
        // 延續自前局、尚未被收下的供託堆——跟積棒同一個莊家角落疊放，stickIndex 從積棒支數之後接續，
        // 視覺上連成同一疊，見 MahjongRiichiStickPresentation KDoc。
        val pooledSticks = (0 until presentation.pooledStickCount).map { i ->
            val placement = MahjongTileTableLayout.stickPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                seatIndex = presentation.dealerSeatIndex,
                stickIndex = presentation.comboStickCount + i,
            )
            MahjongScoringStickEntity(world = world).apply {
                refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
                denomination = MahjongScoringStickDenomination.P1000
                assignToTable(presentation.tableId)
            }
        }
        val newSticks = declaredSticks + pooledSticks
        val spawnedSticks = mutableListOf<MahjongScoringStickEntity>()
        newSticks.forEach { stick ->
            if (!world.spawnEntity(stick)) {
                spawnedSticks.forEach(MahjongScoringStickEntity::discard)
                return MahjongRiichiStickPresentationResult.SPAWN_FAILED
            }
            stick.enqueueDropAnimation()
            spawnedSticks += stick
        }
        oldSticks.forEach(MahjongScoringStickEntity::discard)
        table.markDirty()
        return MahjongRiichiStickPresentationResult.PRESENTED
    }

    /** 清除指定 controller 周圍且 table UUID 相符的正式立直棒。 */
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

    /**
     * 只查詢桌子結構附近並以同步 UUID＋面額精確篩選，避免掃描整個 dimension——用面額額外過濾是因為
     * 積棒與立直棒共用同一個 entity 類型與同一個 [MahjongScoringStickEntity.managedTableId]，兩個 presenter 各自的清除邏輯不能
     * 誤刪對方管理的 entity。
     */
    private fun findManagedSticks(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
    ): List<MahjongScoringStickEntity> = world.getEntitiesByClass(
        MahjongScoringStickEntity::class.java,
        Box(controllerPos).expand(STICK_SEARCH_HORIZONTAL, STICK_SEARCH_VERTICAL, STICK_SEARCH_HORIZONTAL),
    ) { stick -> stick.managedTableId == tableId && stick.denomination == MahjongScoringStickDenomination.P1000 }

    /** 將版本無關 table location 轉回 Fabric 方塊座標。 */
    private fun TableLocation.toBlockPos(): BlockPos = BlockPos(x, y, z)

    /** 正式立直棒建立與查詢使用的固定參數。 */
    private companion object {
        /** controller 周圍查詢正式立直棒的水平半徑。 */
        const val STICK_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢正式立直棒的垂直半徑。 */
        const val STICK_SEARCH_VERTICAL: Double = 2.0
    }
}
