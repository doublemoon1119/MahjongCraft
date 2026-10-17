package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.PrivateHandLayout
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
 * 在每幀開始時讀取原生牌張資料，為渲染與命中建立同一份本人布局。
 *
 * @property stateStore 已按本人視角裁切的對局快照。
 */
class ClientHandTileLayoutController(
    private val stateStore: ClientMahjongStateStore,
) {
    /** 不持有原生物件的連續布局計算器。 */
    private val layout = PrivateHandLayout()

    /** 本幀絕對顯示位置；讀取時不再重新計算或改變過渡。 */
    private var frame: Map<Uuid, TileDisplayTransform> = emptyMap()

    /** 最近建立布局的世界實例，避免不同連線共用同一個時間值。 */
    private var world: ClientWorld? = null

    /** 桌局與回合作用域；連莊也必須建立新的暫存。 */
    private var scope: Scope? = null

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
        val playerId = client.player?.uuid?.toKotlinUuid() ?: return clear()
        val tableId = stateStore.findTableWhereSeated(playerId) ?: return clear()
        val snapshot = stateStore.gameSnapshot(tableId) ?: return clear()
        val player = snapshot.players.firstOrNull { it.id == playerId } ?: return clear()
        val nextScope = Scope(
            tableId = tableId,
            playerId = playerId,
            roundNumber = snapshot.roundNumber,
            roundPosition = snapshot.roundPosition,
            comboCount = snapshot.comboCount,
        )
        if (world !== currentWorld || scope != nextScope) {
            clear()
            world = currentWorld
            scope = nextScope
        }
        val standingIds = player.hand.standingTiles.map { it.id }.filterNot { it == player.hand.lastDrawn?.id }
        val inputs = currentWorld.entities.asSequence().filterIsInstance<MahjongTileEntity>()
            .filter { !it.isRemoved && it.managedTableId == tableId }
            .associate { tile ->
                tile.uuid.toKotlinUuid() to TileDisplayInput(
                    target = TileDisplayTransform(
                        position = DiceAnimationVector(tile.x, tile.y, tile.z),
                        yawDegrees = tile.yaw,
                        poseDegrees = tile.tilePose.rotationDegrees,
                    ),
                    motion = tile.takeIf { it.animating && it.animationDurationTicks > 0 }?.let {
                        TileDisplayMotion(
                            startedAt = it.animationStartGameTime,
                            spec = TileMotionAnimationSpec(
                                durationTicks = it.animationDurationTicks,
                                arcHeight = it.animationArcHeight,
                                startPoseRotationDegrees = it.animationStartPoseRotationDegrees,
                                endPoseRotationDegrees = it.animationEndPoseRotationDegrees,
                                easeRotation = it.animationEaseRotation,
                            ),
                            startOffset = it.animationStartOffset,
                            startYawOffset = it.animationStartYawOffsetDegrees,
                        )
                    },
                    visible = !tile.isInvisible,
                )
            }
        val revealedIds = snapshot.revealedHandTiles.mapTo(mutableSetOf()) { it.id }
        val handIds = standingIds + listOfNotNull(player.hand.lastDrawn?.id)
        frame = layout.update(
            time = currentWorld.time.toDouble() + tickDelta,
            standingOrder = standingIds,
            drawnId = player.hand.lastDrawn?.id,
            inputs = inputs,
            releaseHand = playerId in snapshot.finishedPlayerIds || (handIds.isNotEmpty() && handIds.all { it in revealedIds }),
        )
    }

    /**
     * 讀取本幀已計算的顯示結果；沒有接管時由原版共享動畫繪製。
     *
     * @param entity 要繪製或選取的牌張。
     * @return 本幀絕對位置與姿態；非本作用域時為 null。
     */
    fun display(entity: MahjongTileEntity): TileDisplayTransform? = if (entity.world === world && entity.managedTableId == scope?.tableId) {
        frame[entity.uuid.toKotlinUuid()]
    } else {
        null
    }

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

    /** 清除世界、桌局、回合與所有過渡，不保留上一輪私人牌位。 */
    fun clear() {
        layout.clear()
        frame = emptyMap()
        world = null
        scope = null
    }

    /**
     * 本人布局的暫存身分，包含連莊時會變動的本場數。
     *
     * @property tableId 桌局識別碼。
     * @property playerId 本人識別碼。
     * @property roundNumber 回合序號。
     * @property roundPosition 規則的回合位置。
     * @property comboCount 本場數，辨識同局連莊。
     */
    private data class Scope(
        val tableId: Uuid,
        val playerId: Uuid,
        val roundNumber: Int,
        val roundPosition: MatchRoundPosition,
        val comboCount: Int,
    )
}
