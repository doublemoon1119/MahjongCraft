package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.FabricTickMonotonicClock
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresenter
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
import kotlin.uuid.toKotlinUuid

/** 使用 Fabric 1.20.1 entity 呈現並替換指定麻將桌的正式牌牆。 */
@Single(binds = [MahjongTileWallPresenter::class])
class FabricMahjongTileWallPresenter(
    private val serverHolder: FabricServerHolder,
    private val tickClock: FabricTickMonotonicClock,
) : MahjongTileWallPresenter {
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /**
     * 驗證 controller 後先建立新牌；全部成功才移除同桌舊牌。[MahjongTileWallPresentation.structure]
     * 為空（例如對局結束）時，跳過建立步驟直接視為成功，等同只清除舊牌——重用同一段替換邏輯，不需要
     * 另外開一個清除專用的呼叫路徑。
     *
     * 每張新牌 entity 的 UUID 在加入世界前直接設為對應 [com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile.id]，
     * 沿用 [MahjongTileEntity] 既有 KDoc 早已寫下的設計意圖——此時 entity 尚未加入 world 的 UUID
     * 索引，是唯一安全能覆寫 UUID 的時機點；`world.spawnEntity` 之後才變更會與世界既有索引不一致。
     *
     * 王牌區的牌這裡一律跟活牌用同一組（`isDeadWall = false`）座標生成，維持一圈完整無縫的牌牆——
     * 不在生成當下就把王牌拉出開門，那樣會少了「開門」的過程，缺少沉浸感。真正把王牌區拉出的動作
     * 交給 [scheduleDeadWallReveal]，等擲骰動畫播完才執行。
     *
     * 這裡查詢並清除的「舊牌」不限定牌牆自己管理的牌，而是這張桌子目前所有管理中的麻將牌 entity
     * （含已經被 [com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.FabricMahjongHandTilesPresenter]
     * 領走、目前呈現成手牌的那些）——因為每一局重新洗牌都會產生全新的 `IdentifiedTile.id`，跟上一局
     * 完全無關，所以只要牌牆重新生成（代表新的一局開始），上一局不管是牌牆、王牌還是手牌的舊 entity
     * 全部都已經沒有意義，應該整批清空，不需要區分「這張舊牌上一局屬於哪個呈現子系統」再各自分開
     * 清理。這也是本方法是這條開局呈現鏈裡第一個被呼叫的原因：由它負責一次把整張桌子清乾淨，之後
     * 手牌 presenter 只需要專心「領走這局的新牌」，不用再自己額外清理上一局的殘留。
     */
    override fun present(presentation: MahjongTileWallPresentation): MahjongTileWallPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongTileWallPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return MahjongTileWallPresentationResult.TABLE_NOT_FOUND
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return MahjongTileWallPresentationResult.TABLE_NOT_FOUND
        }

        val stacksPerSide = presentation.structure.values
            .filter { position -> position.side == 0 }
            .maxOfOrNull { position -> position.stack + 1 } ?: 0
        val oldTiles = findManagedTiles(world, presentation.tableId, controllerPos)
        val newTiles = presentation.structure.map { (tileId, position) ->
            val placement = MahjongTileTableLayout.wallPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                dealerSeatIndex = presentation.dealerSeatIndex,
                stacksPerSide = stacksPerSide,
                position = position,
            )
            MahjongTileEntity(world = world).apply {
                uuid = tileId.toJavaUuid()
                refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
                tilePose = MahjongTilePose.FACE_DOWN
                assignToTable(presentation.tableId)
            }
        }
        val spawnedTiles = mutableListOf<MahjongTileEntity>()
        newTiles.forEach { tile ->
            if (!world.spawnEntity(tile)) {
                spawnedTiles.forEach(MahjongTileEntity::discard)
                return MahjongTileWallPresentationResult.SPAWN_FAILED
            }
            spawnedTiles += tile
        }
        oldTiles.forEach(MahjongTileEntity::discard)
        table.markDirty()
        scheduleDeadWallReveal(presentation, controllerPos, stacksPerSide)
        return MahjongTileWallPresentationResult.PRESENTED
    }

    /**
     * 排定王牌區延遲移出開門位置：延遲時長跟 [FabricGamePresentationPublisher][com.doublemoon1119.mahjongcraft.platform.fabric.server.event.FabricGamePresentationPublisher]
     * 用來標記桌子忙碌的時長算法完全一致（[MahjongDiceTableLayout.totalAnimationTicks]），確保王牌
     * 移出的時機跟「擲骰動畫播完」同步，不是任意估計的秒數。[MahjongTileWallPresentation.diceCount]
     * 為 `0`（沒有搭配擲骰）或沒有王牌時直接跳過，不排定任何延遲工作。
     *
     * 延遲工作觸發時重新用 UUID 查詢目前世界上的 entity（不沿用 [present] 當下建立的參考）：中途
     * 桌子可能已被拆除或這局已經結束，屆時查不到對應 entity 屬於正常情況，直接放棄，不拋例外。
     */
    private fun scheduleDeadWallReveal(
        presentation: MahjongTileWallPresentation,
        controllerPos: BlockPos,
        stacksPerSide: Int,
    ) {
        if (presentation.diceCount <= 0 || presentation.deadWallTileIds.isEmpty()) return
        val delayTicks = MahjongDiceTableLayout.totalAnimationTicks(presentation.diceCount)
        tickClock.scheduleAfter(delayTicks * MILLIS_PER_TICK) {
            moveDeadWallToOpenPosition(presentation, controllerPos, stacksPerSide)
        }
    }

    /** 把王牌區的牌從無縫牌牆位置移到跟活牌保持距離的開門位置，逐張 `refreshPositionAndAngles`。 */
    private fun moveDeadWallToOpenPosition(
        presentation: MahjongTileWallPresentation,
        controllerPos: BlockPos,
        stacksPerSide: Int,
    ) {
        val world = resolveWorld(presentation.tableLocation) ?: return
        val deadWallTiles = findManagedTiles(world, presentation.tableId, controllerPos)
            .filter { tile -> tile.uuid.toKotlinUuid() in presentation.deadWallTileIds }
        deadWallTiles.forEach { tile ->
            val position = presentation.structure[tile.uuid.toKotlinUuid()] ?: return@forEach
            val placement = MahjongTileTableLayout.wallPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                dealerSeatIndex = presentation.dealerSeatIndex,
                stacksPerSide = stacksPerSide,
                position = position,
                isDeadWall = true,
            )
            tile.refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
            tile.isDeadWallTile = true
        }
        logger.debug("moveDeadWallToOpenPosition tableId={} movedTileCount={}", presentation.tableId, deadWallTiles.size)
    }

    /** 清除指定 controller 周圍且 table UUID 相符的所有正式管理中麻將牌（含手牌），理由同 [present] KDoc。 */
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
     * 只查詢桌子結構附近並以同步 UUID 精確篩選，避免掃描整個 dimension。不篩選角色——同一張桌子
     * 目前所有管理中的麻將牌（不管是牌牆、王牌還是已經被領走的手牌）都在查詢範圍內，理由見 [present]
     * KDoc。
     */
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

    /** 正式牌牆建立與查詢使用的固定參數。 */
    private companion object {
        /** controller 周圍查詢正式牌牆用牌的水平半徑。 */
        const val TILE_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢正式牌牆用牌的垂直半徑。 */
        const val TILE_SEARCH_VERTICAL: Double = 2.0

        /** Minecraft 正常運行時每個 tick 對應的毫秒數（20 TPS），換算 [FabricTickMonotonicClock.scheduleAfter] 的延遲毫秒數用。 */
        const val MILLIS_PER_TICK: Long = 50L
    }
}
