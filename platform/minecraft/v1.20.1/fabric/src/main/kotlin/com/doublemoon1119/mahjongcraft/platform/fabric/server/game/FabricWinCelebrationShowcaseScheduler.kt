package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ShowcaseCardSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ShowcaseSoundSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ShowcaseWingSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ShowcaseWinningTileSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationShowcaseEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.PersistentTableOverlayCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.winningHandFaceDownIndices
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import org.koin.core.annotation.Single
import kotlin.random.Random
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/** 胡牌加碼 showcase 舞台的生成與真實牌／局況顯示交接服務。 */
@Single
class FabricWinCelebrationShowcaseScheduler(
    private val showcaseRegistry: WinCelebrationShowcaseRegistry,
    private val overlays: PersistentTableOverlayCoordinator,
    private val spawnGateway: FabricEntitySpawnGateway,
) {
    private val logger = mahjongCraftLogger(FabricWinCelebrationShowcaseScheduler::class)
    private val warnedUnknownCues = mutableSetOf<String>()

    /**
     * 排程用的單一贏家翼。
     *
     * @property seatIndex 贏家座位。
     * @property cueIds 規則給的展示理由；由 [WinCelebrationShowcaseRegistry.select] 挑出要播放的定義。
     * @property cards 展示用的牌，依編隊順序排列：手牌在前，之後依序為各組副露；不含和牌張。
     * @property standYaw 起飛前站起來後的朝向，即贏家座位的正面朝向。
     */
    data class Wing(
        val seatIndex: Int,
        val cueIds: List<String>,
        val cards: List<Card>,
        val standYaw: Float,
    )

    /**
     * 翼中的一張牌。
     *
     * @property tileId 桌上的真實牌。
     * @property assetKey 牌面素材。
     * @property group 所屬的組：`0` 為手牌，之後依序為各組副露。
     * @property faceDown 是否露出牌背，例如暗槓兩端。
     */
    data class Card(
        val tileId: Uuid,
        val assetKey: String,
        val group: Int = 0,
        val faceDown: Boolean = false,
    )

    /**
     * [cueIds] 是否選得出已登記的展示定義；選不出時這位贏家不播放展示，並對同一組理由記錄一次警告。
     *
     * @param cueIds 規則給這位贏家的展示理由。
     * @return 可以播放展示時為 `true`。
     */
    fun hasShowcase(cueIds: List<String>): Boolean {
        if (cueIds.isEmpty()) return false
        val found = showcaseRegistry.select(cueIds) != null
        if (!found && warnedUnknownCues.add(cueIds.toString())) {
            logger.warn("Unknown win celebration showcase cues {}; skipping the showcase", cueIds)
        }
        return found
    }

    /**
     * 本局在胡牌後繼續時，[celebration] 的 showcase 是否要讓仍在本局中的玩家等它播完。
     *
     * @param celebration 這次胡牌的展示請求。
     * @return 見 [WinCelebrationShowcaseRegistry.pausesContinuingRound]。
     */
    fun pausesContinuingRound(celebration: WinCelebrationRequest): Boolean = showcaseRegistry.pausesContinuingRound(celebration.winners.map { it.cueIds })

    /**
     * 生成共享舞台；成功時隱藏局況顯示到舞台結束，並讓真實牌在 stage 起點交接為隱形。
     *
     * [wings] 只能包含 [hasShowcase] 為 `true` 的贏家。找不到和牌張、或有贏家一張牌都找不到時不播放；
     * 有牌找不到時記錄一筆警告，列出各座位缺少的牌。
     */
    fun schedule(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
        stagePlacement: MahjongTileWallPlacement,
        startGameTime: Long,
        winningTileId: Uuid,
        winningTileAssetKey: String,
        wings: List<Wing>,
    ): Long? {
        if (wings.isEmpty()) return null
        val definitions = wings.map { wing ->
            requireNotNull(showcaseRegistry.select(wing.cueIds)) { "Showcase wing has no registered definition: ${wing.cueIds}" }
        }
        val showcaseDurationTicks = definitions.maxOf { it.showcaseDurationTicks }
        val endGameTime = startGameTime + WinCelebrationShowcaseEntity.totalDurationTicks(showcaseDurationTicks)
        val winningTile = world.getEntity(winningTileId.toJavaUuid()) as? MahjongTileEntity
        if (winningTile == null) {
            logger.warn("Win celebration showcase cancelled on table {}: winning tile entity {} is missing", tableId, winningTileId)
            return null
        }
        val winningTileSnapshot = ShowcaseWinningTileSnapshot(
            assetKey = winningTileAssetKey,
            startOffsetX = winningTile.x - stagePlacement.x,
            startOffsetY = winningTile.y - stagePlacement.y,
            startOffsetZ = winningTile.z - stagePlacement.z,
            startYaw = winningTile.yaw,
        )
        val missingBySeat = mutableMapOf<Int, MutableList<Uuid>>()
        val snapshots = wings.mapIndexed { wingIndex, wing ->
            ShowcaseWingSnapshot(
                seatIndex = wing.seatIndex,
                cueKey = definitions[wingIndex].cueKey,
                cards = wing.cards.mapIndexedNotNull { order, card ->
                    val tile = world.getEntity(card.tileId.toJavaUuid()) as? MahjongTileEntity
                    if (tile == null) {
                        missingBySeat.getOrPut(wing.seatIndex, ::mutableListOf) += card.tileId
                        return@mapIndexedNotNull null
                    }
                    ShowcaseCardSnapshot(
                        wingIndex = wingIndex,
                        order = order,
                        assetKey = card.assetKey,
                        startOffsetX = tile.x - stagePlacement.x,
                        startOffsetY = tile.y - stagePlacement.y,
                        startOffsetZ = tile.z - stagePlacement.z,
                        startYaw = tile.yaw,
                        group = card.group,
                        standYaw = wing.standYaw,
                        faceDown = card.faceDown,
                    )
                },
            )
        }
        val cancelled = snapshots.any { it.cards.isEmpty() }
        missingPresentationTilesText(missingBySeat)?.let { missing ->
            if (cancelled) {
                logger.warn("Win celebration showcase cancelled on table {}: a winner has no tile entities left ({})", tableId, missing)
            } else {
                logger.warn("Win celebration showcase on table {} skips tiles whose entities are missing ({})", tableId, missing)
            }
        }
        if (cancelled) return null
        val extraSounds = definitions
            .flatMap { it.extraSounds }
            .distinct()
            .map { ShowcaseSoundSnapshot(it.soundId, it.tickOffset, it.volume, it.pitch) }
        val stage = WinCelebrationShowcaseEntity(world = world).apply {
            configure(tableId, startGameTime, endGameTime, Random.nextLong(), winningTileSnapshot, snapshots, extraSounds)
            refreshPositionAndAngles(stagePlacement.x, stagePlacement.y, stagePlacement.z, stagePlacement.yaw, 0.0f)
        }
        if (!spawnGateway.spawn(world, stage, "win-celebration-showcase", tableId)) return null

        wings.flatMap { it.cards }.map { it.tileId }.plus(winningTileId).distinct().forEach { tileId ->
            (world.getEntity(tileId.toJavaUuid()) as? MahjongTileEntity)?.enqueueAll(
                listOf(AnimationStep.WaitUntil(startGameTime), AnimationStep.SetInvisible(true)),
            )
        }
        overlays.hideUntil(world, tableId, controllerPos, endGameTime)
        return endGameTime
    }
}

/**
 * 依結算面板的手牌排法排出一翼的牌：手牌在前，之後依鳴牌順序接上各組副露，暗槓兩端露出牌背。
 *
 * @param standingTileIds 已排好序的手牌，不含和牌張。
 * @param melds 依鳴牌順序的副露，每組的牌已依面板順序排列。
 * @param assetKeyOf 取得牌面素材；取不到的牌不列入。
 * @return 依編隊順序排列的牌。
 */
internal fun showcaseWingCards(
    standingTileIds: List<Uuid>,
    melds: List<MahjongMeldTileGroup>,
    assetKeyOf: (Uuid) -> String?,
): List<FabricWinCelebrationShowcaseScheduler.Card> {
    val hand = standingTileIds.mapNotNull { id -> assetKeyOf(id)?.let { FabricWinCelebrationShowcaseScheduler.Card(tileId = id, assetKey = it) } }
    val meldCards = melds.flatMapIndexed { meldIndex, meld ->
        val faceDown = winningHandFaceDownIndices(meld.type, meld.tileIds.size)
        meld.tileIds.mapIndexedNotNull { index, id ->
            assetKeyOf(id)?.let { asset ->
                FabricWinCelebrationShowcaseScheduler.Card(
                    tileId = id,
                    assetKey = asset,
                    group = meldIndex + 1,
                    faceDown = index in faceDown,
                )
            }
        }
    }
    return hand + meldCards
}
