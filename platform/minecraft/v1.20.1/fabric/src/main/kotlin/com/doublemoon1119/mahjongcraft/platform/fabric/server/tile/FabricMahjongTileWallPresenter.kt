package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileMotionAnimationSpec
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
) : MahjongTileWallPresenter {
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /**
     * 驗證 controller 後先建立新牌；全部成功才移除同桌舊牌。[MahjongTileWallPresentation.structure]
     * 為空（例如對局結束）時，跳過建立步驟直接視為成功，等同只清除舊牌——重用同一段替換邏輯，不需要
     * 另外開一個清除專用的呼叫路徑。
     *
     * 每張新牌 entity 的 UUID 在加入世界前直接設為對應 [IdentifiedTile.id]，
     * 沿用 [MahjongTileEntity] 既有 KDoc 早已寫下的設計意圖——此時 entity 尚未加入 world 的 UUID
     * 索引，是唯一安全能覆寫 UUID 的時機點；`world.spawnEntity` 之後才變更會與世界既有索引不一致。
     *
     * 王牌區的牌這裡一律跟活牌用同一組（`isDeadWall = false`）座標生成，維持一圈完整無縫的牌牆——
     * 不在生成當下就把王牌拉出開門，那樣會少了「開門」的過程，缺少沉浸感。真正把王牌區拉出的動作
     * 交給 [scheduleDeadWallReveal]，等擲骰動畫播完才執行。
     *
     * 這裡查詢並清除的「舊牌」不限定牌牆自己管理的牌，而是這張桌子目前所有管理中的麻將牌 entity
     * （含已經被 [FabricMahjongHandTilesPresenter]領走、目前呈現成手牌的那些）——因為每一局重新洗牌都會產生全新的 `IdentifiedTile.id`，
     * 跟上一局完全無關，所以只要牌牆重新生成（代表新的一局開始），上一局不管是牌牆、王牌還是手牌的舊 entity
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
            position to MahjongTileEntity(world = world).apply {
                uuid = tileId.toJavaUuid()
                refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
                tilePose = MahjongTilePose.FACE_DOWN
                assignToTable(presentation.tableId)
            }
        }
        val spawnedTiles = mutableListOf<MahjongTileEntity>()
        newTiles.forEach { (_, tile) ->
            if (!world.spawnEntity(tile)) {
                spawnedTiles.forEach(MahjongTileEntity::discard)
                return MahjongTileWallPresentationResult.SPAWN_FAILED
            }
            spawnedTiles += tile
        }
        oldTiles.forEach(MahjongTileEntity::discard)
        table.markDirty()
        startWallDropAnimations(world, newTiles, presentation, controllerPos, stacksPerSide)
        return MahjongTileWallPresentationResult.PRESENTED
    }

    /**
     * 每面牌牆同時由玩家右側（[TileWallPosition.stack] 為 `0`，見 [MahjongTileTableLayout.wallPlacement]
     * KDoc）至左側依序半空生成掉落，形成波浪感——`stack` 越大延遲越久開始掉落，四面牌牆各自獨立套用
     * 同一套延遲公式（[MahjongTileTableLayout.WAVE_STEP_TICKS]）、同時開始，天然同步成波浪；`layer`
     * 不影響延遲，同一墩兩層一起落下。牌張本身已經 `refreshPositionAndAngles` 到掉落終點，這裡只需要
     * 疊加半空起點的動畫，起訖姿態相同（[MahjongTilePose.FACE_DOWN]），只有位置在動。「延遲到期前先
     * 隱形」用 [AnimationStep.SetInvisible] 表達，不是像過去那樣提前把動畫起點設在未來、靠 renderer
     * 端另外判斷「還沒到期」來隱藏——理由見 [AnimatedMahjongEntity] KDoc。
     *
     * 王牌區的牌（[MahjongTileWallPresentation.deadWallTileIds]）額外在掉落動畫播完後接續排定移出
     * 開門位置：所有王牌共用同一個算好的絕對揭示時刻（[AnimationStep.WaitUntil]，等待時長是牌牆掉落
     * 動畫總時長加上擲骰動畫時長的總和——擲骰動畫會等牌牆完全落地才開始播放，見
     * `FabricGamePresentationPublisher.publishDiceRoll`，王牌移出開門的時機要跟著往後移，不能只算
     * 擲骰動畫本身的時長），不是每張王牌各自用減法反推剩餘等待時間去湊同一個目標——理由見
     * [AnimationStep.WaitUntil] KDoc。[MahjongTileWallPresentation.diceCount] 為 `0`（沒有搭配擲骰）
     * 或沒有王牌時這張牌的佇列到掉落動畫播完就結束，不會多排這段。同一時機點順便把
     * [MahjongTileWallPresentation.revealedTileIds] 對應的牌翻成正面朝上——開局第一張寶牌指示牌本該
     * 在王牌分離、開門完成的這一刻公開，不需要另外排一個時機點。
     */
    private fun startWallDropAnimations(
        world: ServerWorld,
        tilesWithPosition: List<Pair<TileWallPosition, MahjongTileEntity>>,
        presentation: MahjongTileWallPresentation,
        controllerPos: BlockPos,
        stacksPerSide: Int,
    ) {
        val revealAbsoluteGameTime = if (presentation.diceCount > 0 && presentation.deadWallTileIds.isNotEmpty()) {
            world.time + MahjongTileTableLayout.wallDropAnimationTicks(stacksPerSide) +
                MahjongDiceTableLayout.totalAnimationTicks(presentation.diceCount)
        } else {
            null
        }
        tilesWithPosition.forEach { (position, tile) ->
            val startDelayTicks = MahjongTileTableLayout.wallDropStartDelayTicks(position.stack)
            val steps = mutableListOf<AnimationStep<MahjongTilePose>>(
                AnimationStep.SetInvisible(true),
                AnimationStep.WaitUntil(world.time + startDelayTicks),
                AnimationStep.SetInvisible(false),
                AnimationStep.PlayMotion(
                    durationTicks = TileMotionAnimationSpec.DEFAULT_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = WALL_DROP_HEIGHT,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                ),
            )
            val tileId = tile.uuid.toKotlinUuid()
            if (revealAbsoluteGameTime != null && tileId in presentation.deadWallTileIds) {
                val openPlacement = MahjongTileTableLayout.wallPlacement(
                    controllerX = controllerPos.x,
                    controllerY = controllerPos.y,
                    controllerZ = controllerPos.z,
                    tableFacing = presentation.tableFacing,
                    dealerSeatIndex = presentation.dealerSeatIndex,
                    stacksPerSide = stacksPerSide,
                    position = position,
                    isDeadWall = true,
                )
                steps += AnimationStep.WaitUntil(revealAbsoluteGameTime)
                steps += AnimationStep.Teleport(openPlacement.x, openPlacement.y, openPlacement.z, openPlacement.yaw)
                if (tileId in presentation.revealedTileIds) steps += AnimationStep.Custom(MahjongTilePose.FACE_UP)
            }
            tile.enqueueAll(steps)
        }
    }

    /**
     * 把 [revealedTileIds] 對應的既有管理中牌姿態改成正面朝上；找不到對應 entity（例如桌子已被拆掉、
     * 或這局已經結束）的牌直接跳過，回傳結果只用來讓呼叫端記 log，不影響已成功翻面的牌。
     */
    override fun revealDeadWallTiles(tableId: Uuid, tableLocation: TableLocation, revealedTileIds: Set<Uuid>): MahjongTileWallPresentationResult {
        val world = resolveWorld(tableLocation) ?: return MahjongTileWallPresentationResult.TABLE_NOT_FOUND
        val controllerPos = tableLocation.toBlockPos()
        val managedTiles = findManagedTiles(world, tableId, controllerPos)
            .filter { tile -> tile.uuid.toKotlinUuid() in revealedTileIds }
            .associateBy { tile -> tile.uuid.toKotlinUuid() }
        managedTiles.values.forEach { tile -> tile.tilePose = MahjongTilePose.FACE_UP }
        val missingTileCount = revealedTileIds.size - managedTiles.size
        if (missingTileCount > 0) {
            logger.warn(
                "revealDeadWallTiles tableId={} skipped {} tile(s): no existing managed entity found",
                tableId,
                missingTileCount,
            )
        }
        return MahjongTileWallPresentationResult.PRESENTED
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

        /** 牌牆生成掉落動畫的半空起點高度，相對掉落終點的世界 Y 偏移；遊戲內驗證後從 1.5 調低。 */
        const val WALL_DROP_HEIGHT: Double = 0.8
    }
}
