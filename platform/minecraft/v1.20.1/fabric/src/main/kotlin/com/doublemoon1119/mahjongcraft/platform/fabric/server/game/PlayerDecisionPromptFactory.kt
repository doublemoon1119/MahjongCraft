package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationInputSpec
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionPlayerRelationDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoundPreparationPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WaitingTileAvailabilityDto
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.util.isHonor
import com.doublemoon1119.mahjongcraft.logic.util.isTerminal
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.aiPlayerDisplayName
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 只從權威桌況建立指定玩家可見的操作 HUD prompt。 */
@Single
class PlayerDecisionPromptFactory(
    private val gameRepository: GameRepository,
    private val candidateResolver: GameActionCandidateResolver,
    private val moduleRegistry: MahjongModuleRegistry,
    private val tileAssetRegistry: MinecraftTileAssetRegistry,
) {
    /** 建立目前決策的私人 prompt；遊戲或玩家已失效時回傳 null。 */
    suspend fun create(gameId: Uuid, playerId: Uuid, phase: PlayerDecisionPhase): PlayerDecisionPromptDto? {
        val game = gameRepository.getGame(gameId) ?: return null
        val state = game.tableState
        val player = state.players.firstOrNull { it.id == playerId } ?: return null
        val actions = candidateResolver.listActionCandidates(playerId)
        val riichiTiles = candidateResolver.listRiichiTileCandidates(playerId)
        val preparation = game.pendingRoundPreparation
            ?.takeIf { playerId !in it.completedPlayerIds }
            ?.inputSpecsByPlayerId
            ?.get(playerId)
            ?.toPrompt { tileId ->
                player.hand.tiles.firstOrNull { it.id == tileId }?.tile?.toAssetKey(tileAssetRegistry)
            }
        val analyses = if (phase == PlayerDecisionPhase.OWN_TURN && state.config is RiichiRuleConfig) {
            createRiichiAnalyses(state, playerId)
        } else {
            emptyList()
        }
        val trigger = state.triggerContext(playerId)
        val orderedAiPlayerIds = game.roomPlayerIds.filter { id -> state.players.any { it.id == id && it.isAi } }
        return PlayerDecisionPromptDto(
            decisionKey = buildDecisionKey(
                gameId,
                playerId,
                phase,
                preparation,
                when (phase) {
                    PlayerDecisionPhase.OWN_TURN -> player.hand.lastDrawn?.id ?: player.actionHistory.lastOrNull()?.hashCode()
                    PlayerDecisionPhase.DISCARD_REACTION -> state.pendingReaction?.tileId
                    PlayerDecisionPhase.KAN_REACTION -> state.pendingKanReaction?.robbedTile?.id
                    PlayerDecisionPhase.ROUND_PREPARATION -> game.pendingRoundPreparation?.stepIndex
                },
            ),
            actions = actions.map { candidate ->
                val preview = candidate.action.previewTiles(state, playerId, player.hand, candidate.referenceTile)
                PlayerDecisionActionDto(
                    token = candidate.token,
                    actionId = candidate.action.presentationId(),
                    referenceTileAssetKey = candidate.referenceTile?.toAssetKey(tileAssetRegistry),
                    previewTileAssetKeys = preview.tiles.map { it.toAssetKey(tileAssetRegistry) },
                    claimedTileIndex = preview.claimedTileIndex,
                    claimedTileOrientation = preview.claimedTileOrientation,
                )
            },
            triggerTileAssetKey = actions.firstNotNullOfOrNull { it.referenceTile }?.toAssetKey(tileAssetRegistry),
            triggerPlayerId = trigger?.playerId?.toString(),
            triggerPlayerName = trigger?.playerId?.let { sourceId ->
                val sourcePlayer = state.players.firstOrNull { it.id == sourceId }
                if (sourcePlayer?.isAi == true) aiPlayerDisplayName(sourceId, orderedAiPlayerIds) else null
            },
            triggerPlayerRelation = trigger?.relation,
            triggerActionId = trigger?.actionId,
            riichiTileIds = riichiTiles.map { it.tileId.toString() },
            riichiTileAssetKeys = riichiTiles.mapNotNull { candidate ->
                player.hand.standingTiles.firstOrNull { it.id == candidate.tileId }?.tile?.toAssetKey(tileAssetRegistry)
            }.distinct(),
            preparation = preparation,
            discardAnalyses = analyses,
        )
    }

    /** 日麻逐張假想捨牌，且只以自身與公開資訊估算等待牌餘量。 */
    private fun createRiichiAnalyses(
        state: com.doublemoon1119.mahjongcraft.logic.table.TableState,
        playerId: Uuid,
    ): List<DiscardReadinessAnalysisDto> {
        val player = state.players.first { it.id == playerId }
        val calculator = moduleRegistry.getModule(state.config).createShantenCalculator()
        val visibleTiles = buildList {
            addAll(player.hand.tiles.map { it.tile })
            state.players.forEach { tablePlayer ->
                addAll(tablePlayer.discardPile.entries.map { it.tile.tile })
                addAll(tablePlayer.hand.exposedMelds.flatMap { meld -> meld.tiles.map { it.tile } })
            }
            (state.dynamicRuleState as? RiichiDynamicState)?.getDoraIndicators(state)?.first?.let { indicators ->
                addAll(indicators.map { it.tile })
            }
        }.groupingBy { it.riichiCanonical }.eachCount()
        val riichiState = player.playerRuleState as? RiichiPlayerState
        return player.hand.standingTiles.mapNotNull { discard ->
            val result = player.hand.discardById(discard.id) ?: return@mapNotNull null
            val tenpai = calculator.calculate(Hand(result.hand.tiles, result.hand.melds)) as? ShantenResult.Tenpai
                ?: return@mapNotNull null
            val waits = tenpai.winningTiles.map(Tile::riichiCanonical).distinct()
            val discarded = player.discardPile.entries.map { it.tile.tile.riichiCanonical }.toSet() + discard.tile.riichiCanonical
            val passed = player.passedTilesInRound.map(Tile::riichiCanonical).toSet()
            val status = when {
                riichiState?.isPermanentlyFuriten == true -> PERMANENT_FURITEN
                waits.any { it in discarded } -> DISCARD_FURITEN
                waits.any { it in passed } -> TEMPORARY_FURITEN
                else -> null
            }
            DiscardReadinessAnalysisDto(
                discardTileId = discard.id.toString(),
                waitingTiles = waits.map { tile ->
                    WaitingTileAvailabilityDto(
                        tileAssetKey = tile.toAssetKey(tileAssetRegistry),
                        remainingCount = (COPIES_PER_RIICHI_TILE - (visibleTiles[tile] ?: 0)).coerceAtLeast(0),
                    )
                },
                statusIndicatorId = status,
            )
        }
    }

    /** 組合不依同步時間變化的決策識別碼。 */
    private fun buildDecisionKey(
        gameId: Uuid,
        playerId: Uuid,
        phase: PlayerDecisionPhase,
        preparation: RoundPreparationPromptDto?,
        reference: Any?,
    ): String = listOf(gameId, playerId, phase, reference, preparation?.hashCode()).joinToString(":")

    private companion object {
        const val COPIES_PER_RIICHI_TILE = 4
        const val DISCARD_FURITEN = "mahjongcraft:discard_furiten"
        const val TEMPORARY_FURITEN = "mahjongcraft:temporary_furiten"
        const val PERMANENT_FURITEN = "mahjongcraft:permanent_furiten"
    }
}

/** 將受控 preparation input 轉成不暴露其他玩家提交的私人 prompt。 */
private fun RoundPreparationInputSpec.toPrompt(resolveAssetKey: (Uuid) -> String?): RoundPreparationPromptDto = when (this) {
    RoundPreparationInputSpec.Confirmation -> RoundPreparationPromptDto.Confirmation
    is RoundPreparationInputSpec.SingleChoice -> RoundPreparationPromptDto.SingleChoice(optionIds)
    is RoundPreparationInputSpec.TileSelection -> {
        val sortedTileIds = eligibleTileIds.sortedBy(Uuid::toString)
        RoundPreparationPromptDto.TileSelection(
            eligibleTileIds = sortedTileIds.map(Uuid::toString),
            eligibleTileAssetKeys = sortedTileIds.mapNotNull(resolveAssetKey),
            minCount = minCount,
            maxCount = maxCount,
        )
    }
}

/** 將內建與第三方動作轉成穩定顯示 ID。 */
private fun GameAction.presentationId(): String = when (this) {
    GameAction.Tsumo -> "mahjongcraft:tsumo"
    is GameAction.Ron -> "mahjongcraft:ron"
    is GameAction.Chi -> "mahjongcraft:chi"
    is GameAction.Pon -> "mahjongcraft:pon"
    is GameAction.Kan -> "mahjongcraft:kan_${type.name.lowercase()}"
    GameAction.Pass -> "mahjongcraft:pass"
    is GameAction.ExhaustiveDraw -> reason.id
    is GameAction.Extension -> value.id
    else -> "mahjongcraft:action"
}

/** 建立動作完成後的牌組預覽；第三方動作沒有受控牌組資料時安全地只顯示參考牌。 */
private fun GameAction.previewTiles(
    state: com.doublemoon1119.mahjongcraft.logic.table.TableState,
    playerId: Uuid,
    hand: Hand,
    referenceTile: Tile?,
): ActionTilePreview {
    val identifiedById = hand.standingTiles.associateBy { it.id }
    fun tile(id: Uuid): Tile? = identifiedById[id]?.tile
    val rawTiles = when (this) {
        is GameAction.Chi -> withTiles.mapNotNull(::tile) + listOfNotNull(referenceTile)
        is GameAction.Pon ->
            hand.standingTiles
                .map { it.tile }
                .filter { candidate -> referenceTile != null && candidate.riichiCanonical == referenceTile.riichiCanonical }
                .take(2) + listOfNotNull(referenceTile)
        is GameAction.Kan -> withTiles.mapNotNull(::tile) + listOfNotNull(referenceTile ?: tile(tileId))
        is GameAction.Ron, GameAction.Tsumo -> listOfNotNull(referenceTile)
        is GameAction.ExhaustiveDraw -> if (reason == RiichiExhaustiveDrawReason.KyuushuKyuuhai) {
            hand.tiles.map { it.tile }.filter { it.isTerminal || it.isHonor }
        } else {
            emptyList()
        }
        else -> listOfNotNull(referenceTile)
    }
    val hasClaimedTile = this is GameAction.Chi || this is GameAction.Pon || this is GameAction.Kan && type == GameAction.KanType.OPEN_KAN
    if (!hasClaimedTile || rawTiles.isEmpty()) return ActionTilePreview(rawTiles)
    val source = state.claimSource(playerId)
    val claimedTile = rawTiles.last()
    val remaining = rawTiles.dropLast(1).toMutableList()
    val claimedIndex = when (source) {
        ClaimSource.LEFT -> 0
        ClaimSource.ACROSS -> 1.coerceAtMost(remaining.size)
        ClaimSource.RIGHT -> 2.coerceAtMost(remaining.size)
    }
    remaining.add(claimedIndex, claimedTile)
    return ActionTilePreview(
        tiles = remaining,
        claimedTileIndex = claimedIndex,
        claimedTileOrientation = when (source) {
            ClaimSource.LEFT -> DecisionTileOrientationDto.ROTATED_LEFT
            ClaimSource.ACROSS, ClaimSource.RIGHT -> DecisionTileOrientationDto.ROTATED_RIGHT
        },
    )
}

/** 操作 HUD 一組牌面及其中被鳴牌的方向。 */
private data class ActionTilePreview(
    val tiles: List<Tile>,
    val claimedTileIndex: Int? = null,
    val claimedTileOrientation: DecisionTileOrientationDto = DecisionTileOrientationDto.UPRIGHT,
)

/** 從目前等待捨牌反應的出牌者解析相對來源方向。 */
private fun com.doublemoon1119.mahjongcraft.logic.table.TableState.claimSource(playerId: Uuid): ClaimSource {
    val sourceId = pendingReaction?.discarderId ?: pendingKanReaction?.declarerId ?: return ClaimSource.ACROSS
    val playerIndex = players.indexOfFirst { it.id == playerId }
    val sourceIndex = players.indexOfFirst { it.id == sourceId }
    val offset = (sourceIndex - playerIndex + players.size) % players.size
    return when {
        offset == players.lastIndex -> ClaimSource.LEFT
        offset == 1 -> ClaimSource.RIGHT
        else -> ClaimSource.ACROSS
    }
}

/** 建立反應 HUD 的來源玩家、相對位置及動作語意。 */
private fun com.doublemoon1119.mahjongcraft.logic.table.TableState.triggerContext(playerId: Uuid): TriggerContext? {
    val sourceId = pendingReaction?.discarderId ?: pendingKanReaction?.declarerId ?: return null
    val source = claimSource(playerId)
    val actionId = pendingKanReaction?.kanAction?.presentationId() ?: "mahjongcraft:discard"
    return TriggerContext(
        playerId = sourceId,
        relation = when (source) {
            ClaimSource.LEFT -> DecisionPlayerRelationDto.LEFT
            ClaimSource.ACROSS -> DecisionPlayerRelationDto.ACROSS
            ClaimSource.RIGHT -> DecisionPlayerRelationDto.RIGHT
        },
        actionId = actionId,
    )
}

/** 一次他家反應的公開來源資訊。 */
private data class TriggerContext(val playerId: Uuid, val relation: DecisionPlayerRelationDto, val actionId: String)

/** 被鳴牌相對於操作玩家的來源。 */
private enum class ClaimSource { LEFT, ACROSS, RIGHT }
