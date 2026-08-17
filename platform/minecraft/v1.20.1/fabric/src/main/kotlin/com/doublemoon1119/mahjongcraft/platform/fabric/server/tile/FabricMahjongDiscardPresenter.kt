package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresenter
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

/** 使用 Fabric 1.20.1 entity 呈現正式牌河：把牌牆管理中的既有 entity 領走並移動，不重新生成。 */
@Single(binds = [MahjongDiscardPresenter::class])
class FabricMahjongDiscardPresenter(
    private val serverHolder: FabricServerHolder,
) : MahjongDiscardPresenter {
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /**
     * 牌河裡的每一張牌，UUID 都跟牌牆結構座標傳過來的那張牌完全同一個——理由跟
     * [FabricMahjongHandTilesPresenter.present] 完全一致，這裡只用 `World.getEntity` 依 UUID 找到
     * 既有 entity，直接改標記、改姿態（[MahjongTilePose.FACE_UP]，牌河牌面永遠朝上可見）、移動到牌河
     * 位置——絕對不能另外 `spawnEntity`。
     *
     * [MahjongDiscardPresentation.discardTileIds] 的順序本身就是捨牌順序，直接依索引呼叫
     * [MahjongTileTableLayout.discardPlacement]；[MahjongDiscardPresentation.sidewaysMarkedTileId]
     * 相符的那張額外標記側身。找不到對應 UUID 的既有 entity 時該筆直接跳過並記警告 log，不中斷其餘
     * 牌的呈現，比照本介面 best-effort 的既有慣例。
     *
     * 這次呼叫不做「清除上一局遺留牌河」的整批清除——牌牆 presenter 每次重新生成牌牆時已經整批清空
     * 這張桌子所有管理中的麻將牌（見 [FabricMahjongTileWallPresenter.present] KDoc），牌牆一定比
     * 摸牌/丟牌先執行，上一局的舊 entity 早就不存在了。
     */
    override fun present(presentation: MahjongDiscardPresentation): MahjongDiscardPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongDiscardPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return MahjongDiscardPresentationResult.TABLE_NOT_FOUND
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return MahjongDiscardPresentationResult.TABLE_NOT_FOUND
        }

        // TODO: 暫時停用 sideStillHasWall 即時查詢，固定延伸第三排；換回真正的判斷時把下面這行換成
        //  sideStillHasWall(world, presentation.tableId, controllerPos, presentation.tableFacing, presentation.seatIndex)。
        val wallRemaining = true

        var missingTileCount = 0
        presentation.discardTileIds.forEachIndexed { discardIndex, tileId ->
            val tile = world.getEntity(tileId.toJavaUuid()) as? MahjongTileEntity
            if (tile == null) {
                missingTileCount++
                logger.warn("publishDiscardPileUpdated tableId={} tileId={} skipped: no existing wall entity found to claim", presentation.tableId, tileId)
                return@forEachIndexed
            }
            val placement = MahjongTileTableLayout.discardPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                seatIndex = presentation.seatIndex,
                discardIndex = discardIndex,
                isSidewaysMarked = tileId == presentation.sidewaysMarkedTileId,
                wallRemaining = wallRemaining,
            )
            tile.assignToTable(presentation.tableId)
            tile.tilePose = MahjongTilePose.FACE_UP
            tile.refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
        }
        table.markDirty()
        if (missingTileCount > 0) {
            logger.warn("publishDiscardPileUpdated tableId={} presented with {} missing tile(s) out of {}", presentation.tableId, missingTileCount, presentation.discardTileIds.size)
            return MahjongDiscardPresentationResult.SPAWN_FAILED
        }
        return MahjongDiscardPresentationResult.PRESENTED
    }

    /**
     * 清除指定 controller 周圍且 table UUID 相符的所有正式管理中麻將牌——跟
     * [FabricMahjongTileWallPresenter.clear] 效果相同（都是清這張桌子的全部管理中麻將牌，不分子
     * 系統），保留成獨立方法只是維持介面對稱，呼叫端仍可能只想觸發牌河這條路徑的清除。
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

    /**
     * **尚未定型，目前未被呼叫（見 [present] 內的 TODO）**：`isDeadWallTile` 排除法只解決了王牌區殘留
     * 造成的誤判，還沒有實際遊玩驗證過整體判斷是否可靠；之後可能會整個換成完全不同的實作方式（例如
     * 不再即時掃描世界範圍內的 entity，改用其他判斷依據），不是只在目前這個「查詢範圍內是否有實體」的
     * 設計上小修小補。改動或整個換掉這個函式前不需要顧慮相容性。
     *
     * 這位玩家自己那面牆目前是否還有管理中的麻將牌 entity——每次都對世界即時查詢，不快取任何東西：
     * 快取撐不過伺服器重啟（世界重新載入後快取的計數就對不上了），世界本身的 entity 位置才是持久化、
     * 每次重啟都會正確還原的權威資料，直接查它即可。
     *
     * 牌牆某一面的實際物理方位，推導出來剛好等於「這位玩家自己座位的物理方位」——不管誰是莊家：
     * [MahjongTileTableLayout.wallPlacement] 的旋轉合成是 `advance(seatIndexToTableSide(dealerSeatIndex),
     * position.side)`，`position.side = 0`（該面所屬玩家自己面前）時 `advance` 不推進，直接等於
     * `seatIndexToTableSide(dealerSeatIndex)`；而某位玩家自己那面牆的 domain side 剛好是
     * `(該玩家座位 index - dealerSeatIndex) mod 4`，代入後两个旋轉相消，結果就是
     * `seatIndexToTableSide(該玩家座位 index)`。因此不需要另外知道莊家是誰，直接用
     * `wallPlacement(dealerSeatIndex = seatIndex, position.side = 0, ...)` 這個技巧就能算出這位玩家
     * 自己那面牆的世界座標範圍。
     *
     * 用該面 `stack = 0` 跟 `stack = ASSUMED_STACKS_PER_SIDE - 1`（假設標準日麻 17 墩／面，理由同
     * [MahjongTileTableLayout] 內部换算 `discardPlacement` 安全距離時的同一個假設）兩個角落的世界
     * 座標框出一個涵蓋整面牆的 [Box]，外擴一點誤差空間，查詢範圍內是否還有這張桌子管理中、且不屬於
     * 王牌區（[MahjongTileEntity.isDeadWallTile]）的麻將牌——王牌區的牌一旦移出開門位置就會一直留在
     * 原地直到這局結束，不能算進「這一面牆是否還有活牌」：開門缺口剛好落在某位玩家自己面前那面牆時，
     * 王牌區的牌仍會落在這個查詢框內，如果不排除會讓查詢永遠回傳 true，即使活牌早就摸光了（遊戲內
     * 驗證過的現象：玩家自己那面牆明明已經空了，牌河卻沒有换到第四排，一直沿第三排延伸）。
     */
    private fun sideStillHasWall(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
        tableFacing: MahjongTableFacing,
        seatIndex: Int,
    ): Boolean {
        val nearCorner = MahjongTileTableLayout.wallPlacement(
            controllerX = controllerPos.x,
            controllerY = controllerPos.y,
            controllerZ = controllerPos.z,
            tableFacing = tableFacing,
            dealerSeatIndex = seatIndex,
            stacksPerSide = ASSUMED_STACKS_PER_SIDE,
            position = TileWallPosition(side = 0, stack = 0, layer = 0),
        )
        val farCorner = MahjongTileTableLayout.wallPlacement(
            controllerX = controllerPos.x,
            controllerY = controllerPos.y,
            controllerZ = controllerPos.z,
            tableFacing = tableFacing,
            dealerSeatIndex = seatIndex,
            stacksPerSide = ASSUMED_STACKS_PER_SIDE,
            position = TileWallPosition(side = 0, stack = ASSUMED_STACKS_PER_SIDE - 1, layer = 1),
        )
        val box = Box(nearCorner.x, nearCorner.y, nearCorner.z, farCorner.x, farCorner.y, farCorner.z)
            .expand(WALL_SIDE_QUERY_PADDING)
        return world.getEntitiesByClass(MahjongTileEntity::class.java, box) { tile ->
            tile.managedTableId == tableId && !tile.isDeadWallTile
        }.isNotEmpty()
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

    /** 正式牌河建立與查詢使用的固定參數。 */
    private companion object {
        /** controller 周圍查詢正式牌河的水平半徑。 */
        const val TILE_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢正式牌河的垂直半徑。 */
        const val TILE_SEARCH_VERTICAL: Double = 2.0

        /** [sideStillHasWall] 假設的標準日麻每面墩數，理由同 [MahjongTileTableLayout] 內部同名假設。 */
        const val ASSUMED_STACKS_PER_SIDE: Int = 17

        /** [sideStillHasWall] 用兩個角落算出來的框再外擴的容許誤差，確保牌本身的寬度也算進查詢範圍。 */
        const val WALL_SIDE_QUERY_PADDING: Double = 1.0
    }
}
