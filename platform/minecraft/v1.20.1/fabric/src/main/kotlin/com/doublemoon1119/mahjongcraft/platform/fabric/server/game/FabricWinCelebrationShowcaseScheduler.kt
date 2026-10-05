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
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
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
     * @property tileIdsAndAssets 展示用的牌與牌面素材。
     */
    data class Wing(val seatIndex: Int, val cueIds: List<String>, val tileIdsAndAssets: List<Pair<Uuid, String>>)

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
     * [wings] 只能包含 [hasShowcase] 為 `true` 的贏家。
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
        val winningTile = world.getEntity(winningTileId.toJavaUuid()) as? MahjongTileEntity ?: return null
        val winningTileSnapshot = ShowcaseWinningTileSnapshot(
            assetKey = winningTileAssetKey,
            startOffsetX = winningTile.x - stagePlacement.x,
            startOffsetY = winningTile.y - stagePlacement.y,
            startOffsetZ = winningTile.z - stagePlacement.z,
            startYaw = winningTile.yaw,
        )
        val snapshots = wings.mapIndexed { wingIndex, wing ->
            ShowcaseWingSnapshot(
                seatIndex = wing.seatIndex,
                cueKey = definitions[wingIndex].cueKey,
                cards = wing.tileIdsAndAssets.mapIndexedNotNull { order, (tileId, asset) ->
                    val tile = world.getEntity(tileId.toJavaUuid()) as? MahjongTileEntity ?: return@mapIndexedNotNull null
                    ShowcaseCardSnapshot(
                        wingIndex = wingIndex,
                        order = order,
                        assetKey = asset,
                        startOffsetX = tile.x - stagePlacement.x,
                        startOffsetY = tile.y - stagePlacement.y,
                        startOffsetZ = tile.z - stagePlacement.z,
                        startYaw = tile.yaw,
                    )
                },
            )
        }
        if (snapshots.any { it.cards.isEmpty() }) return null
        val extraSounds = definitions
            .flatMap { it.extraSounds }
            .distinct()
            .map { ShowcaseSoundSnapshot(it.soundId, it.tickOffset, it.volume, it.pitch) }
        val stage = WinCelebrationShowcaseEntity(world = world).apply {
            configure(tableId, startGameTime, endGameTime, Random.nextLong(), winningTileSnapshot, snapshots, extraSounds)
            refreshPositionAndAngles(stagePlacement.x, stagePlacement.y, stagePlacement.z, stagePlacement.yaw, 0.0f)
        }
        if (!spawnGateway.spawn(world, stage, "win-celebration-showcase", tableId)) return null

        wings.flatMap { it.tileIdsAndAssets }.map { it.first }.plus(winningTileId).distinct().forEach { tileId ->
            (world.getEntity(tileId.toJavaUuid()) as? MahjongTileEntity)?.enqueueAll(
                listOf(AnimationStep.WaitUntil(startGameTime), AnimationStep.SetInvisible(true)),
            )
        }
        overlays.hideUntil(world, tableId, controllerPos, endGameTime)
        return endGameTime
    }
}
