package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.LocalHandRelease
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.PrivateHandLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.PrivateHandOrder
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.PrivateHandOrderEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.PrivateHandOrderPayloadDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.PrivateHandOrderTimeline
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.SpectatorHandOrder
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayInput
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayMotion
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayTransform
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileMotionAnimationSpec
import net.minecraft.client.MinecraftClient
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.Box
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 在每幀開始時讀取原生牌張資料，為渲染與命中建立同一份本機手牌布局。
 *
 * 本人所坐的桌子只排本人的手牌，排列取自伺服器送來的時間軸，與共享呈現的牌張動作同時生效；快照的手牌是交易結束時的結果，
 * 只在還沒收到時間軸時使用。旁觀的桌子則替每一家看得到牌面的手牌依規則牌序排列，見 [SpectatorHandOrder]。
 *
 * @property stateStore 已按本人視角裁切的對局快照。
 * @property moduleRegistry 取得各桌規則牌序。
 */
class ClientHandTileLayoutController(
    private val stateStore: ClientMahjongStateStore,
    private val moduleRegistry: MahjongModuleRegistry,
) {
    /** 每一家由本機排列的手牌各自的連續布局，以桌子與玩家索引。 */
    private val slots = mutableMapOf<SlotKey, Slot>()

    /** 本幀絕對顯示位置；讀取時不再重新計算或改變過渡。 */
    private var frame: Map<Uuid, TileDisplayTransform> = emptyMap()

    /** 最近建立布局的世界實例，避免不同連線共用同一個時間值。 */
    private var world: ClientWorld? = null

    /** 各桌的本人手牌排列時間軸；跨回合保留，由伺服器在發牌開始時送出空排列來結束上一局。 */
    private val timelines = mutableMapOf<Uuid, PrivateHandOrderTimeline>()

    /** 最近一次解析牌序的規則設定與結果，同一桌的設定在對局中不變。 */
    private val tileOrders = mutableMapOf<MahjongRuleConfig, Comparator<Tile>?>()

    /** 本幀是否有需要替換原版位置的牌張。 */
    val hasOverrides: Boolean get() = frame.isNotEmpty()

    /**
     * 由 GameRenderer 的幀起點呼叫一次，之後所有呈現讀取相同結果。
     *
     * @param client 目前客戶端。
     * @param tickDelta 本幀部分 tick。
     */
    fun beginFrame(client: MinecraftClient, tickDelta: Float) {
        val currentWorld = client.world ?: return clear()
        val localId = client.player?.uuid?.toKotlinUuid() ?: return clear()
        if (world !== currentWorld) {
            clear()
            world = currentWorld
        }
        val time = currentWorld.time.toDouble() + tickDelta
        val seatedTableId = stateStore.findTableWhereSeated(localId)
        val tilesByTable = currentWorld.entities.asSequence().filterIsInstance<MahjongTileEntity>()
            .filter { !it.isRemoved }
            .groupBy { it.managedTableId }
        val active = mutableSetOf<SlotKey>()
        val next = mutableMapOf<Uuid, TileDisplayTransform>()
        stateStore.gameTableIds().forEach { tableId ->
            val snapshot = stateStore.gameSnapshot(tableId) ?: return@forEach
            val inputs = tilesByTable[tableId].orEmpty().associate { tile -> tile.uuid.toKotlinUuid() to tile.displayInput() }
            val faceUpIds = snapshot.tilePoseStates.filter { it.pose == TilePose.FACE_UP }.mapTo(mutableSetOf()) { it.tileId }
            val arranged = if (tableId == seatedTableId) {
                listOfNotNull(snapshot.players.firstOrNull { it.id == localId }?.let { it to seatedOrder(tableId, it, time) })
            } else {
                val tileOrder = tileOrder(snapshot.config) ?: return@forEach
                val targets = inputs.mapValues { it.value.target }
                snapshot.players.mapNotNull { player -> SpectatorHandOrder.arrange(player.hand, tileOrder, targets)?.let { player to it } }
            }
            arranged.forEach { (player, order) ->
                val key = SlotKey(tableId, player.id)
                active += key
                next += slot(key, snapshot).layout.update(
                    time = time,
                    standingOrder = order.standing,
                    drawnId = order.drawnId,
                    inputs = inputs,
                    releaseHand = LocalHandRelease.isReleased(
                        ownerId = player.id,
                        handIds = (player.hand.standingTiles.map { it.id } + listOfNotNull(player.hand.lastDrawn?.id)).toSet(),
                        finishedPlayerIds = snapshot.finishedPlayerIds,
                        faceUpIds = faceUpIds,
                        publicEvidenceTileIds = snapshot.publicEvidenceTileIds,
                    ),
                    releaseTileIds = faceUpIds,
                    unsortedIds = order.unsortedIds,
                )
            }
        }
        slots.keys.retainAll(active)
        frame = next
    }

    /**
     * 本人手牌的排列：依時間軸生效；還沒收到任何項目（例如剛重新連線）時才用快照的權威手牌。
     *
     * @param tableId 本人所坐的桌子。
     * @param player 本人的玩家快照。
     * @param time 本幀世界時間。
     * @return 本人手牌排列。
     */
    private fun seatedOrder(tableId: Uuid, player: MahjongPlayerSnapshot, time: Double): PrivateHandOrder {
        val timeline = timelines[tableId]
        return if (timeline == null || timeline.isEmpty) {
            PrivateHandOrder(
                standing = player.hand.standingTiles.map { it.id }.filterNot { it == player.hand.lastDrawn?.id },
                drawnId = player.hand.lastDrawn?.id,
                unsortedIds = emptyList(),
            )
        } else {
            timeline.orderAt(time) ?: PrivateHandOrder(standing = emptyList(), drawnId = null, unsortedIds = emptyList())
        }
    }

    /**
     * 取得這一家的布局；換回合（包含連莊）時清除上一輪的暫存。
     *
     * @param key 桌子與玩家。
     * @param snapshot 桌況快照。
     * @return 這一家的布局。
     */
    private fun slot(key: SlotKey, snapshot: TableStateSnapshot): Slot {
        val round = Round(snapshot.roundNumber, snapshot.roundPosition, snapshot.comboCount)
        val existing = slots[key]
        if (existing != null && existing.round == round) return existing
        existing?.layout?.clear()
        return Slot(round, PrivateHandLayout()).also { slots[key] = it }
    }

    /**
     * 解析該桌規則的牌序；未註冊的規則不排列。
     *
     * @param config 該桌規則設定。
     * @return 規則牌序，或無法解析時為 null。
     */
    private fun tileOrder(config: MahjongRuleConfig): Comparator<Tile>? = tileOrders.getOrPut(config) {
        runCatching { moduleRegistry.getModule(config).tileOrder }.getOrNull()
    }

    /**
     * 讀取原生牌張的共享位置與動作。
     *
     * @receiver 原生牌張。
     * @return 布局使用的牌張資料。
     */
    private fun MahjongTileEntity.displayInput(): TileDisplayInput = TileDisplayInput(
        target = TileDisplayTransform(
            position = DiceAnimationVector(x, y, z),
            yawDegrees = yaw,
            poseDegrees = tilePose.rotationDegrees,
        ),
        motion = takeIf { it.animating && it.animationDurationTicks > 0 }?.let {
            TileDisplayMotion(
                startedAt = it.animationStartGameTime,
                spec = TileMotionAnimationSpec(
                    durationTicks = it.animationDurationTicks,
                    arcHeight = it.animationArcHeight,
                    startPoseRotationDegrees = it.animationStartPoseRotationDegrees,
                    endPoseRotationDegrees = it.animationEndPoseRotationDegrees,
                    easeRotation = it.animationEaseRotation,
                    liftCarryLower = it.animationLiftCarryLower,
                    leanDegrees = it.animationLeanDegrees,
                ),
                startOffset = it.animationStartOffset,
                startYawOffset = it.animationStartYawOffsetDegrees,
            )
        },
        visible = !isInvisible,
    )

    /**
     * 讀取本幀已計算的顯示結果；沒有接管時由原版共享動畫繪製。
     *
     * @param entity 要繪製或選取的牌張。
     * @return 本幀絕對位置與姿態；不是本機排列的牌時為 null。
     */
    fun display(entity: MahjongTileEntity): TileDisplayTransform? = if (entity.world === world) frame[entity.uuid.toKotlinUuid()] else null

    /**
     * 依本人看見的位置與翻轉角建立包圍盒；未接管的牌保留原生結果。
     *
     * @param entity 要進行視線選取或裁切的牌張。
     * @return 本幀顯示位置的保守包圍盒。
     */
    fun adjustedBox(entity: MahjongTileEntity): Box {
        val transform = display(entity) ?: return entity.boundingBox
        val angle = Math.toRadians(transform.poseDegrees.toDouble())
        val height = abs(cos(angle)) * MahjongTileDimensions.TILE_HEIGHT + abs(sin(angle)) * MahjongTileDimensions.TILE_DEPTH
        // 沿用原生的正方形底面包圍方式，旋轉時仍包住整張牌。
        val width = max(
            MahjongTileDimensions.TILE_WIDTH,
            abs(sin(angle)) * MahjongTileDimensions.TILE_HEIGHT + abs(cos(angle)) * MahjongTileDimensions.TILE_DEPTH,
        )
        val position = transform.position
        return Box(
            position.x - width / 2,
            position.y,
            position.z - width / 2,
            position.x + width / 2,
            position.y + height,
            position.z + width / 2,
        )
    }

    /**
     * 收下伺服器送來的本人手牌排列項目。
     *
     * @param payload 線路資料。
     */
    fun receive(payload: PrivateHandOrderPayloadDto) {
        timelines.getOrPut(Uuid.parse(payload.tableId)) { PrivateHandOrderTimeline() }.add(
            PrivateHandOrderEntry(
                effectiveAt = payload.effectiveGameTime,
                order = PrivateHandOrder(
                    standing = payload.standingTileIds.map { id -> id?.let(Uuid::parse) },
                    drawnId = payload.drawnTileId?.let(Uuid::parse),
                    unsortedIds = payload.unsortedTileIds.map(Uuid::parse),
                ),
            ),
        )
    }

    /**
     * 桌子已移除時丟掉它的時間軸。
     *
     * @param tableId 已移除的桌子。
     */
    fun removeTable(tableId: Uuid) {
        timelines.remove(tableId)
    }

    /** 連線開始或結束時清除所有桌子的時間軸與目前布局。 */
    fun clearSession() {
        timelines.clear()
        tileOrders.clear()
        clear()
    }

    /** 清除世界與所有布局的過渡，不保留上一輪的牌位；時間軸保留，本人排列仍依它生效。 */
    fun clear() {
        slots.values.forEach { it.layout.clear() }
        slots.clear()
        frame = emptyMap()
        world = null
    }

    /**
     * 一家手牌布局的身分。
     *
     * @property tableId 桌子。
     * @property playerId 手牌的主人。
     */
    private data class SlotKey(val tableId: Uuid, val playerId: Uuid)

    /**
     * 一家手牌的布局與它所屬的回合。
     *
     * @property round 建立布局時的回合。
     * @property layout 連續布局計算器。
     */
    private class Slot(val round: Round, val layout: PrivateHandLayout)

    /**
     * 回合身分，包含連莊時會變動的本場數。
     *
     * @property roundNumber 回合序號。
     * @property roundPosition 規則的回合位置。
     * @property comboCount 本場數，辨識同局連莊。
     */
    private data class Round(
        val roundNumber: Int,
        val roundPosition: MatchRoundPosition,
        val comboCount: Int,
    )
}
