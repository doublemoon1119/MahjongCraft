package com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.planTablePropSync
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
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
 * 使用 Fabric 1.20.1 entity 差量同步規則桌面物件；每個物件由 [FabricTablePropKindRegistry] 裡對應的種類
 * 負責生成與辨識。
 *
 * 描述裡使用了未登記的種類、或種類不認得的變體時，該物件略過不擺並記錄警告，其餘物件照常同步。
 *
 * @property serverHolder 取得目前伺服器世界。
 * @property spawnGateway 透過平台入口生成桌面物件 entity。
 * @property kindRegistry 查詢規則桌面物件種類實作。
 */
@Single(binds = [TablePropPresenter::class])
class FabricTablePropPresenter(
    private val serverHolder: FabricServerHolder,
    private val spawnGateway: FabricEntitySpawnGateway,
    private val kindRegistry: FabricTablePropKindRegistry,
) : TablePropPresenter {
    /** 記錄略過的未知種類與變體。 */
    private val logger = mahjongCraftLogger(FabricTablePropPresenter::class)

    /**
     * 驗證 controller 後保留相符的既有物件；缺少的物件全部生成成功後，才移除多餘的舊物件。
     *
     * @param presentation 桌子與目標桌面物件的呈現資料。
     * @return 呈現結果；桌子無效或生成失敗時回傳對應錯誤。
     */
    override fun present(presentation: TablePropPresentation): TablePropPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return TablePropPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return TablePropPresentationResult.TABLE_NOT_FOUND
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return TablePropPresentationResult.TABLE_NOT_FOUND
        }

        val targets = presentation.targets().filter { target ->
            (kindRegistry.find(target.kind) != null).also { known ->
                if (!known) logger.warn("Skipped table prop of unregistered kind {} for table {}", target.kind, presentation.tableId)
            }
        }
        val plan = planTablePropSync(
            targets = targets,
            existing = findManagedProps(world, presentation.tableId, controllerPos),
            targetOf = ManagedProp::target,
        )
        val spawned = mutableListOf<Entity>()
        plan.missing.forEach { target ->
            val kind = checkNotNull(kindRegistry.find(target.kind))
            val entity = kind.create(world, presentation.tableId, target)
            if (entity == null) {
                logger.warn("Skipped table prop {} with unknown variant {} for table {}", target.kind, target.variant, presentation.tableId)
                return@forEach
            }
            if (!spawnGateway.spawn(world, entity, "table-prop", presentation.tableId)) {
                spawned.forEach(Entity::discard)
                return TablePropPresentationResult.SPAWN_FAILED
            }
            kind.onSpawned(entity)
            spawned += entity
        }
        plan.surplus.forEach { prop -> prop.entity.discard() }
        table.markDirty()
        return TablePropPresentationResult.PRESENTED
    }

    /**
     * 清除指定 controller 周圍、由這張桌子管理的全部規則桌面物件。
     *
     * @param tableId 桌子 UUID。
     * @param tableLocation 桌子位置。
     * @return 被移除的桌面物件數量。
     */
    override fun clear(tableId: Uuid, tableLocation: TableLocation): Int {
        val world = resolveWorld(tableLocation) ?: return 0
        val props = findManagedProps(world, tableId, tableLocation.toBlockPos())
        props.forEach { prop -> prop.entity.discard() }
        return props.size
    }

    /**
     * 查詢每個已登記種類在桌子結構附近、由這張桌子管理的 entity。
     *
     * @param world 桌子所在伺服器世界。
     * @param tableId 桌子 UUID。
     * @param controllerPos controller 方塊位置。
     * @return entity 與負責種類配對的清單。
     */
    private fun findManagedProps(world: ServerWorld, tableId: Uuid, controllerPos: BlockPos): List<ManagedProp> {
        val searchBox = Box(controllerPos).expand(SEARCH_HORIZONTAL, SEARCH_VERTICAL, SEARCH_HORIZONTAL)
        return kindRegistry.kinds.flatMap { kind ->
            kind.findManaged(world, tableId, searchBox).map { entity -> ManagedProp(kind, entity) }
        }
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
     * @param state 該方塊的狀態。
     * @param tableId 預期桌子 UUID。
     * @return 狀態與桌子 UUID 相符時的 block entity；否則為 null。
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
     * 將版本無關 table location 轉回 Fabric 方塊座標。
     *
     * @param location 版本無關桌子位置。
     * @return 對應的 Minecraft 方塊位置。
     */
    private fun TableLocation.toBlockPos(): BlockPos = BlockPos(x, y, z)

    /**
     * 桌上一個既有的規則桌面物件與負責它的種類。
     *
     * @property kind 解讀變體及搜尋該 entity 的桌面物件種類。
     * @property entity 桌上已存在的 Minecraft entity。
     */
    private class ManagedProp(
        val kind: FabricTablePropKind,
        val entity: Entity,
    ) {
        /**
         * 讀出目前的身分與落點；種類認不得變體時為 null。
         *
         * @return entity 對應的桌面物件目標；無法辨識變體時為 null。
         */
        fun target(): TablePropTarget? = kind.variantOf(entity)?.let { variant ->
            TablePropTarget(
                kind = kind.id,
                variant = variant,
                x = entity.x,
                y = entity.y,
                z = entity.z,
                yaw = entity.yaw,
            )
        }
    }

    private companion object {
        /** controller 周圍查詢規則桌面物件的水平半徑。 */
        const val SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢規則桌面物件的垂直半徑。 */
        const val SEARCH_VERTICAL: Double = 2.0
    }
}
