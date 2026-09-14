package com.doublemoon1119.mahjongcraft.platform.fabric.server.stick

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickDenomination
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongStickPotPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongStickPotPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongStickPotPresenter
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
import kotlin.math.abs
import kotlin.uuid.Uuid

/**
 * 使用 Fabric 1.20.1 entity 差量同步指定麻將桌的正式供託棒。已位於相同目標槽位的 entity 原樣保留，
 * 只有新增槽位會生成新 entity 並播放落下動畫；不再因另一位玩家宣告立直而讓既有立直棒全部重播。
 * 同一個 [MahjongScoringStickEntity] 類型同時代表積棒與供託棒，兩者由面額與所屬桌資訊區分。
 */
@Single(binds = [MahjongStickPotPresenter::class])
class FabricMahjongStickPotPresenter(
    private val serverHolder: FabricServerHolder,
    private val spawnGateway: FabricEntitySpawnGateway,
) : MahjongStickPotPresenter {
    /** 驗證 controller 後差量保留既有槽位；全部缺少的棒都生成成功後，才移除多餘舊棒。 */
    override fun present(presentation: MahjongStickPotPresentation): MahjongStickPotPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongStickPotPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return MahjongStickPotPresentationResult.TABLE_NOT_FOUND
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return MahjongStickPotPresentationResult.TABLE_NOT_FOUND
        }

        val oldSticks = findManagedSticks(world, presentation.tableId, controllerPos)
        val declaredTargets = presentation.declaredSeatIndices.map { seatIndex ->
            val placement = MahjongTileTableLayout.riichiStickPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                seatIndex = seatIndex,
            )
            StickTarget(placement.x, placement.y, placement.z, placement.yaw)
        }
        // 延續自前局、尚未被收下的供託堆——跟積棒同一個莊家角落疊放，stickIndex 從積棒支數之後接續，
        // 視覺上連成同一疊，見 MahjongStickPotPresentation KDoc。
        val pooledTargets = (0 until presentation.pooledStickCount).map { i ->
            val placement = MahjongTileTableLayout.stickPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                seatIndex = presentation.dealerSeatIndex,
                stickIndex = presentation.comboStickCount + i,
            )
            StickTarget(placement.x, placement.y, placement.z, placement.yaw)
        }
        val unmatchedOldSticks = oldSticks.toMutableList()
        val missingTargets = (declaredTargets + pooledTargets).filter { target ->
            val existingIndex = unmatchedOldSticks.indexOfFirst { stick -> target.matches(stick) }
            if (existingIndex >= 0) {
                unmatchedOldSticks.removeAt(existingIndex)
                false
            } else {
                true
            }
        }
        val spawnedSticks = mutableListOf<MahjongScoringStickEntity>()
        missingTargets.forEach { target ->
            val stick = MahjongScoringStickEntity(world = world).apply {
                refreshPositionAndAngles(target.x, target.y, target.z, target.yaw, 0.0f)
                denomination = MahjongScoringStickDenomination.P1000
                assignToTable(presentation.tableId)
            }
            if (!spawnGateway.spawn(world, stick, "stick-pot", presentation.tableId)) {
                spawnedSticks.forEach(MahjongScoringStickEntity::discard)
                return MahjongStickPotPresentationResult.SPAWN_FAILED
            }
            stick.enqueueDropAnimation()
            spawnedSticks += stick
        }
        unmatchedOldSticks.forEach(MahjongScoringStickEntity::discard)
        table.markDirty()
        return MahjongStickPotPresentationResult.PRESENTED
    }

    /** 清除指定 controller 周圍且 table UUID 相符的正式供託棒。 */
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
     * 積棒與供託棒共用同一個 entity 類型與同一個 [MahjongScoringStickEntity.managedTableId]，兩個 presenter 各自的清除邏輯不能
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

    /** 一根供託棒的權威落點；entity 真實座標固定在落點，動畫只加入 render offset。 */
    private data class StickTarget(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
    ) {
        /** 以足以吸收 float 往返誤差、但不會混淆相鄰槽位的容差比對既有 entity。 */
        fun matches(stick: MahjongScoringStickEntity): Boolean = abs(stick.x - x) <= POSITION_EPSILON &&
            abs(stick.y - y) <= POSITION_EPSILON &&
            abs(stick.z - z) <= POSITION_EPSILON &&
            yawDistance(stick.yaw, yaw) <= YAW_EPSILON

        /** 比較環狀角度，讓 `180` 與 `-180` 視為同一朝向。 */
        private fun yawDistance(first: Float, second: Float): Float {
            val normalizedDifference = abs((first - second) % FULL_ROTATION_DEGREES)
            return minOf(normalizedDifference, FULL_ROTATION_DEGREES - normalizedDifference)
        }
    }

    /** 正式供託棒建立與查詢使用的固定參數。 */
    private companion object {
        /** controller 周圍查詢正式供託棒的水平半徑。 */
        const val STICK_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢正式供託棒的垂直半徑。 */
        const val STICK_SEARCH_VERTICAL: Double = 2.0

        /** 世界座標比對容差。 */
        const val POSITION_EPSILON: Double = 1.0e-4

        /** 水平朝向比對容差（度）。 */
        const val YAW_EPSILON: Float = 1.0e-3f

        /** 一圈角度。 */
        const val FULL_ROTATION_DEGREES: Float = 360.0f
    }
}
