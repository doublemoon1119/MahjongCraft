package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementDetailSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementMeldSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementRankingSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementRevealTimingSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementSoundCueSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementWinnerSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationTimelineAnchor
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementRevealSequence
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 將平台無關 request 轉成可持久化 Fabric 胡牌結算舞台。 */
@Single
class FabricWinSettlementPresentationScheduler(
    private val templateRegistry: WinSettlementPresentationTemplateRegistry,
) {
    fun schedule(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
        placement: MahjongTileWallPlacement,
        earliestStartGameTime: Long,
        request: WinSettlementPresentationRequest,
        tileAssetsById: Map<Uuid, String>,
    ): Long? {
        val start = maxOf(world.time, earliestStartGameTime)
        val winners = request.winners.map { winner ->
            WinSettlementWinnerSnapshot(
                playerId = winner.playerId.toString(),
                seatIndex = winner.seatIndex,
                isAi = request.ranking.players.first { it.playerId == winner.playerId }.isAi,
                responsiblePlayerId = winner.responsiblePlayerId?.toString(),
                totalScore = winner.totalScore,
                handAssetKeys = winner.handTileIds.mapNotNull(tileAssetsById::get),
                melds = winner.melds.map { meld ->
                    val assets = meld.tileIds.mapNotNull(tileAssetsById::get)
                    val concealed = when {
                        meld.type != MeldType.CLOSED_KAN -> emptySet()
                        meld.allTilesFaceDown -> assets.indices.toSet()
                        else -> setOf(0, assets.lastIndex)
                    }
                    WinSettlementMeldSnapshot(assets, concealed)
                },
                winningTileAssetKey = winner.winningTileId?.let(tileAssetsById::get).orEmpty(),
                details = winner.detailFields.map { field ->
                    when (val value = field.value) {
                        is WinSettlementDetailValue.Text -> WinSettlementDetailSnapshot(field.id, WinSettlementPresentationEntity.DETAIL_TEXT, listOf(value.translationKey) + value.arguments)
                        is WinSettlementDetailValue.Tiles -> WinSettlementDetailSnapshot(field.id, WinSettlementPresentationEntity.DETAIL_TILES, value.tileIds.mapNotNull(tileAssetsById::get))
                        is WinSettlementDetailValue.Entries -> WinSettlementDetailSnapshot(
                            field.id,
                            WinSettlementPresentationEntity.DETAIL_ENTRIES,
                            value.entries.flatMap {
                                listOf(
                                    it.translationKey,
                                    it.trailingText,
                                    it.trailingTranslationKey.orEmpty(),
                                    it.trailingTranslationArgument.orEmpty(),
                                )
                            },
                        )
                    }
                },
            )
        }
        val rankings = request.ranking.players.map {
            WinSettlementRankingSnapshot(it.playerId.toString(), it.seatIndex, it.isAi, it.previousScore, it.currentScore, it.previousRank, it.currentRank)
        }
        val reveal = templateRegistry.findTemplate(request.templateKey)?.reveal ?: WinSettlementRevealSequence()
        val timing = WinSettlementRevealTimingSnapshot(
            reveal.initialFadeTicks,
            reveal.entryStaggerTicks,
            reveal.scoreRevealTicks,
            reveal.readingTicks,
        )
        val soundCues = buildSoundCues(winners, reveal, timing)
        val stage = WinSettlementPresentationEntity(world = world).apply {
            configure(tableId, start, request.outcomeId, request.templateKey, request.isTsumo, winners, rankings, timing, soundCues)
            refreshPositionAndAngles(placement.x, placement.y + STAGE_HEIGHT_OFFSET, placement.z, placement.yaw, 0f)
        }
        if (!world.spawnEntity(stage)) return null
        world.getEntitiesByClass(MahjongRoundInfoEntity::class.java, Box(controllerPos).expand(2.0, 2.0, 2.0)) {
            it.managedTableId == tableId
        }.firstOrNull()?.hideUntil(stage.endGameTime)
        return stage.endGameTime
    }

    private fun buildSoundCues(
        winners: List<WinSettlementWinnerSnapshot>,
        reveal: WinSettlementRevealSequence,
        timing: WinSettlementRevealTimingSnapshot,
    ): List<WinSettlementSoundCueSnapshot> {
        if (reveal.sounds.isEmpty() && reveal.entrySoundId == null && reveal.scoreSoundId == null) return emptyList()
        var winnerStart = 0L
        return buildList {
            winners.forEach { winner ->
                val entries = winner.details.filter { it.type == WinSettlementPresentationEntity.DETAIL_ENTRIES }
                    .sumOf { it.values.size / WinSettlementPresentationEntity.ENTRY_VALUE_COUNT }
                reveal.sounds.forEach { cue ->
                    val anchorTick = when (cue.anchor) {
                        PresentationTimelineAnchor.PANEL_START -> 0L
                        PresentationTimelineAnchor.ENTRIES_START -> timing.initialFadeTicks.toLong()
                        PresentationTimelineAnchor.AFTER_ENTRIES -> timing.initialFadeTicks + entries.toLong() * timing.entryStaggerTicks
                        PresentationTimelineAnchor.SCORE_REVEAL -> timing.initialFadeTicks + entries.toLong() * timing.entryStaggerTicks +
                            if (winner.hasPostEntrySummary) WinSettlementPresentationEntity.HAN_FU_REVEAL_TICKS else 0L
                    }
                    add(WinSettlementSoundCueSnapshot(winnerStart + anchorTick + cue.offsetTicks, cue.soundId, cue.volume, cue.pitch))
                }
                reveal.entrySoundId?.let { soundId ->
                    repeat(entries) { entryIndex ->
                        add(
                            WinSettlementSoundCueSnapshot(
                                winnerStart + timing.initialFadeTicks + entryIndex.toLong() * timing.entryStaggerTicks,
                                soundId,
                                reveal.soundVolume,
                                reveal.soundPitch + entryIndex * 0.04f,
                            ),
                        )
                    }
                }
                reveal.scoreSoundId?.let { soundId ->
                    add(
                        WinSettlementSoundCueSnapshot(
                            winnerStart + timing.initialFadeTicks + entries.toLong() * timing.entryStaggerTicks,
                            soundId,
                            reveal.soundVolume,
                            reveal.soundPitch,
                        ),
                    )
                }
                winnerStart += WinSettlementPresentationEntity.winnerDurationTicks(winner, timing)
            }
        }
    }

    private companion object {
        const val STAGE_HEIGHT_OFFSET = 1.6
    }

    private val WinSettlementWinnerSnapshot.hasPostEntrySummary: Boolean
        get() = details.any { it.id.endsWith(":riichi_han_fu") || it.id.endsWith(":riichi_yakuman_total") }
}
