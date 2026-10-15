package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.FabricMahjongTileWallPresenter
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
import kotlin.uuid.Uuid

/**
 * 使用 Fabric 1.20.1 entity 呈現桌面中央局況顯示——比照
 * [FabricMahjongTileWallPresenter] 的
 * 「找到既有的就更新，找不到才生成」模式，不是每次都整批換新（見 [MahjongRoundInfoPresenter] KDoc）。
 *
 * @property serverHolder 取得目前伺服器世界。
 * @property spawnGateway 透過平台入口生成局況顯示 entity。
 */
class FabricMahjongRoundInfoPresenter(
    private val serverHolder: FabricServerHolder,
    private val spawnGateway: FabricEntitySpawnGateway,
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
            if (!spawnGateway.spawn(world, display, "round-info", presentation.tableId)) {
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
     *
     * @param presentation 局況顯示的原始呈現資料。
     */
    private fun MahjongRoundInfoEntity.applyPresentation(presentation: MahjongRoundInfoPresentation) {
        lines = presentation.lines
    }

    /**
     * 由版本無關 dimension ID 取得目前 server session 的世界。
     *
     * @param location 桌子位置。
     * @return 對應的已載入伺服器世界；無法解析時為 null。
     */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
        return serverHolder.current()?.getWorld(worldKey)
    }

    /**
     * 驗證指定位置確實是 UUID 與朝向資料可用的 controller。
     *
     * @param world 桌子所在世界。
     * @param controllerPos controller 方塊位置。
     * @param state 該方塊目前狀態。
     * @param tableId 預期桌子 UUID。
     * @return 狀態完整且 UUID 相符的桌子 block entity；否則為 null。
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
     * 只查詢桌子結構附近並以同步 UUID 精確篩選，避免掃描整個 dimension。
     *
     * @param world 要搜尋的伺服器世界。
     * @param tableId 桌子 UUID。
     * @param controllerPos controller 方塊位置。
     * @return 第一個符合桌子 UUID 的局況顯示 entity；不存在時為 null。
     */
    private fun findManagedDisplay(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
    ): MahjongRoundInfoEntity? = world.getEntitiesByClass(
        MahjongRoundInfoEntity::class.java,
        Box(controllerPos).expand(
            TableOverlayEntitySearch.HORIZONTAL_RADIUS,
            TableOverlayEntitySearch.VERTICAL_RADIUS,
            TableOverlayEntitySearch.HORIZONTAL_RADIUS,
        ),
    ) { display -> display.managedTableId == tableId }.firstOrNull()

    /**
     * 將版本無關 table location 轉回 Fabric 方塊座標。
     *
     * @param location 版本無關桌子位置。
     * @return 對應的 Minecraft 方塊位置。
     */
    private fun TableLocation.toBlockPos(): BlockPos = BlockPos(x, y, z)
}
