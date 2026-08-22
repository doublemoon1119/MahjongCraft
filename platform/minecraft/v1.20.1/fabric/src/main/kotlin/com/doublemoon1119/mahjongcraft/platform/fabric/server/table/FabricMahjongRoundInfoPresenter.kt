package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresenter
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
 * 使用 Fabric 1.20.1 entity 呈現桌面中央局況顯示——比照
 * [com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.FabricMahjongTileWallPresenter] 的
 * 「找到既有的就更新，找不到才生成」模式，不是每次都整批換新（見 [MahjongRoundInfoPresenter] KDoc）。
 */
@Single(binds = [MahjongRoundInfoPresenter::class])
class FabricMahjongRoundInfoPresenter(
    private val serverHolder: FabricServerHolder,
) : MahjongRoundInfoPresenter {
    override fun present(presentation: MahjongRoundInfoPresentation): MahjongRoundInfoPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongRoundInfoPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return MahjongRoundInfoPresentationResult.TABLE_NOT_FOUND
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return MahjongRoundInfoPresentationResult.TABLE_NOT_FOUND
        }

        val placement = MahjongTileTableLayout.roundInfoDisplayPlacement(controllerPos.x, controllerPos.y, controllerPos.z)

        val existing = findManagedDisplay(world, presentation.tableId, controllerPos)
        if (existing != null) {
            existing.applyPresentation(presentation)
            existing.refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
        } else {
            val display = MahjongRoundInfoEntity(world = world).apply {
                applyPresentation(presentation)
                refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
                assignToTable(presentation.tableId)
            }
            if (!world.spawnEntity(display)) {
                return MahjongRoundInfoPresentationResult.SPAWN_FAILED
            }
        }
        table.markDirty()
        return MahjongRoundInfoPresentationResult.PRESENTED
    }

    override fun clear(tableId: Uuid, tableLocation: TableLocation): Int {
        val world = resolveWorld(tableLocation) ?: return 0
        val controllerPos = tableLocation.toBlockPos()
        val display = findManagedDisplay(world, tableId, controllerPos) ?: return 0
        display.discard()
        return 1
    }

    /**
     * 把呈現資料的原始數值套到 entity 上——不在這裡格式化成文字，翻譯要交給 client 端 renderer 依各自
     * 語系解析，見 [MahjongRoundInfoEntity] KDoc。
     */
    private fun MahjongRoundInfoEntity.applyPresentation(presentation: MahjongRoundInfoPresentation) {
        lines = presentation.lines
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
    private fun findManagedDisplay(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
    ): MahjongRoundInfoEntity? = world.getEntitiesByClass(
        MahjongRoundInfoEntity::class.java,
        Box(controllerPos).expand(DISPLAY_SEARCH_HORIZONTAL, DISPLAY_SEARCH_VERTICAL, DISPLAY_SEARCH_HORIZONTAL),
    ) { display -> display.managedTableId == tableId }.firstOrNull()

    /** 將版本無關 table location 轉回 Fabric 方塊座標。 */
    private fun TableLocation.toBlockPos(): BlockPos = BlockPos(x, y, z)

    private companion object {
        /** controller 周圍查詢局況顯示 entity 的水平半徑。 */
        const val DISPLAY_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢局況顯示 entity 的垂直半徑。 */
        const val DISPLAY_SEARCH_VERTICAL: Double = 2.0
    }
}
