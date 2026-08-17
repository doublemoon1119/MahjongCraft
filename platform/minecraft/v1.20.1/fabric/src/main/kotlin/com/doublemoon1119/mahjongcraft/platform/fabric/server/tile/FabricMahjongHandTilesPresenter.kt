package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDrawnTilePresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDrawnTilePresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongHandTilesPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongHandTilesPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongHandTilesPresenter
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
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/** 使用 Fabric 1.20.1 entity 呈現正式手牌：把牌牆管理中的既有 entity 領走並移動，不重新生成。 */
@Single(binds = [MahjongHandTilesPresenter::class])
class FabricMahjongHandTilesPresenter(
    private val serverHolder: FabricServerHolder,
) : MahjongHandTilesPresenter {
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /**
     * 手牌裡的每一張牌，UUID 都跟牌牆結構座標傳過來的那張牌完全同一個——這副牌本來就是從牌牆摸出來
     * 分給玩家的，不是另外複製出一批新牌。[com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.FabricMahjongTileWallPresenter]
     * 已經在牌牆生成時，用同樣的 UUID 把這些 entity 建立在世界上了，這裡只需要用 `World.getEntity`
     * 依 UUID 找到那個既有 entity，直接改標記、改姿態（[MahjongTilePose.STANDING]）、移動到手牌
     * 位置——絕對不能另外呼叫 `world.spawnEntity` 建立相同 UUID 的第二個 entity，那樣一定會撞號失敗
     * （Minecraft 世界不允許兩個相同 UUID 的 entity 同時存在）。
     *
     * 每位玩家的手牌各自對稱置中於自己座位的局部側面，直接用座位 index（不經過莊家相對旋轉），理由見
     * [MahjongTileTableLayout.handPlacement] KDoc。找不到對應 UUID 的既有 entity（例如遊戲狀態跟
     * 世界狀態不一致）時該筆直接跳過並記一則警告 log，不中斷其餘牌的領取——比照本介面 best-effort
     * 的既有慣例。
     *
     * 不在這裡另外清除「上一局遺留的手牌」——牌牆 presenter 每次重新生成牌牆時，已經會把這張桌子
     * 目前所有管理中的麻將牌（不分牌牆／手牌）整批清空一次（見
     * [com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.FabricMahjongTileWallPresenter.present]
     * KDoc），而牌牆一定比這裡先執行；輪到這裡領牌時，上一局的舊 entity 早就不存在了，不需要重複
     * 清理。
     */
    override fun present(presentation: MahjongHandTilesPresentation): MahjongHandTilesPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongHandTilesPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return MahjongHandTilesPresentationResult.TABLE_NOT_FOUND
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return MahjongHandTilesPresentationResult.TABLE_NOT_FOUND
        }

        var missingTileCount = 0
        presentation.handsBySeatIndex.forEach { (seatIndex, tileIds) ->
            tileIds.forEachIndexed { tileIndex, tileId ->
                val tile = world.getEntity(tileId.toJavaUuid()) as? MahjongTileEntity
                if (tile == null) {
                    missingTileCount++
                    logger.warn("publishHandTiles tableId={} tileId={} skipped: no existing wall entity found to claim", presentation.tableId, tileId)
                    return@forEachIndexed
                }
                val placement = MahjongTileTableLayout.handPlacement(
                    controllerX = controllerPos.x,
                    controllerY = controllerPos.y,
                    controllerZ = controllerPos.z,
                    tableFacing = presentation.tableFacing,
                    seatIndex = seatIndex,
                    handSize = tileIds.size,
                    tileIndex = tileIndex,
                )
                tile.assignToTable(presentation.tableId)
                tile.tilePose = MahjongTilePose.STANDING
                tile.refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
            }
        }
        table.markDirty()
        if (missingTileCount > 0) {
            logger.warn("publishHandTiles tableId={} presented with {} missing tile(s) out of {}", presentation.tableId, missingTileCount, presentation.handsBySeatIndex.values.sumOf { it.size })
            return MahjongHandTilesPresentationResult.SPAWN_FAILED
        }
        return MahjongHandTilesPresentationResult.PRESENTED
    }

    /**
     * 摸到的牌是同一批 wall-spawned entity 之一，跟 [present] 同一套「找 UUID、改標記、移動」邏輯，
     * 只是移動目的地換成 [MahjongTileTableLayout.drawnTilePlacement]。
     * [MahjongDrawnTilePresentation.drawnTileId] 為 `null` 時直接視為成功、不做任何事——理由見本
     * 方法所屬介面的 KDoc。
     */
    override fun presentDrawnTile(presentation: MahjongDrawnTilePresentation): MahjongDrawnTilePresentationResult {
        val drawnTileId = presentation.drawnTileId ?: return MahjongDrawnTilePresentationResult.PRESENTED
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongDrawnTilePresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return MahjongDrawnTilePresentationResult.TABLE_NOT_FOUND
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return MahjongDrawnTilePresentationResult.TABLE_NOT_FOUND
        }

        val tile = world.getEntity(drawnTileId.toJavaUuid()) as? MahjongTileEntity
        if (tile == null) {
            logger.warn("publishTileDrawn tableId={} tileId={} skipped: no existing wall entity found to claim", presentation.tableId, drawnTileId)
            return MahjongDrawnTilePresentationResult.SPAWN_FAILED
        }
        val placement = MahjongTileTableLayout.drawnTilePlacement(
            controllerX = controllerPos.x,
            controllerY = controllerPos.y,
            controllerZ = controllerPos.z,
            tableFacing = presentation.tableFacing,
            seatIndex = presentation.seatIndex,
            standingTileCount = presentation.standingTileCount,
        )
        tile.assignToTable(presentation.tableId)
        tile.tilePose = MahjongTilePose.STANDING
        tile.refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
        table.markDirty()
        return MahjongDrawnTilePresentationResult.PRESENTED
    }

    /**
     * 清除指定 controller 周圍且 table UUID 相符的所有正式管理中麻將牌——跟
     * [com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.FabricMahjongTileWallPresenter.clear]
     * 效果相同（都是清這張桌子的全部管理中麻將牌，不分子系統），保留成獨立方法只是維持介面對稱，
     * 呼叫端仍可能只想觸發手牌這條路徑的清除。
     */
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

    /** 正式手牌建立與查詢使用的固定參數。 */
    private companion object {
        /** controller 周圍查詢正式手牌的水平半徑。 */
        const val TILE_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢正式手牌的垂直半徑。 */
        const val TILE_SEARCH_VERTICAL: Double = 2.0
    }
}
