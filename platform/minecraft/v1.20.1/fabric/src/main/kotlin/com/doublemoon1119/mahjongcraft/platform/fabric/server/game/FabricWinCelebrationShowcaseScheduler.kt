package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ShowcaseCardSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ShowcaseSoundSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ShowcaseWingSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ShowcaseWinningTileSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationShowcaseEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import kotlin.random.Random
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/** 胡牌加碼 showcase 舞台的生成與真實牌／局況顯示交接服務。 */
@Single
class FabricWinCelebrationShowcaseScheduler(
    private val showcaseRegistry: WinCelebrationShowcaseRegistry,
) {
    private val logger = LoggerFactory.getLogger(FabricWinCelebrationShowcaseScheduler::class.java)
    private val warnedUnknownCues = mutableSetOf<String>()

    /** 排程用的單一贏家翼。 */
    data class Wing(val seatIndex: Int, val cueKey: String?, val tileIdsAndAssets: List<Pair<Uuid, String>>)

    /**
     * 生成共享舞台；成功時隱藏局況顯示到舞台結束，並讓真實牌在 stage 起點交接為隱形。
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
        val showcaseDurationTicks = wings.maxOf { wing ->
            wing.cueKey?.let(showcaseRegistry::find)?.showcaseDurationTicks ?: DEFAULT_SHOWCASE_DURATION_TICKS
        }
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
            val cue = wing.cueKey?.takeIf { showcaseRegistry.find(it) != null } ?: GENERIC_CUE
            if (wing.cueKey != null && cue == GENERIC_CUE && warnedUnknownCues.add(wing.cueKey)) {
                logger.warn("Unknown win celebration showcase cue {}; using generic fallback", wing.cueKey)
            }
            ShowcaseWingSnapshot(
                seatIndex = wing.seatIndex,
                cueKey = cue,
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
        val extraSounds = wings.mapNotNull { it.cueKey?.let(showcaseRegistry::find) }
            .flatMap { it.extraSounds }
            .distinct()
            .map { ShowcaseSoundSnapshot(it.soundId, it.tickOffset, it.volume, it.pitch) }
        val stage = WinCelebrationShowcaseEntity(world = world).apply {
            configure(tableId, startGameTime, endGameTime, Random.nextLong(), winningTileSnapshot, snapshots, extraSounds)
            refreshPositionAndAngles(stagePlacement.x, stagePlacement.y, stagePlacement.z, stagePlacement.yaw, 0.0f)
        }
        if (!world.spawnEntity(stage)) return null

        wings.flatMap { it.tileIdsAndAssets }.map { it.first }.plus(winningTileId).distinct().forEach { tileId ->
            (world.getEntity(tileId.toJavaUuid()) as? MahjongTileEntity)?.enqueueAll(
                listOf(AnimationStep.WaitUntil(startGameTime), AnimationStep.SetInvisible(true)),
            )
        }
        findRoundInfo(world, tableId, controllerPos)?.hideUntil(endGameTime)
        return endGameTime
    }

    private fun findRoundInfo(world: ServerWorld, tableId: Uuid, controllerPos: BlockPos): MahjongRoundInfoEntity? = world.getEntitiesByClass(MahjongRoundInfoEntity::class.java, Box(controllerPos).expand(2.0, 2.0, 2.0)) {
        it.managedTableId == tableId
    }.firstOrNull()

    private companion object {
        const val GENERIC_CUE = "mahjongcraft:generic"
        const val DEFAULT_SHOWCASE_DURATION_TICKS = 160
    }
}
