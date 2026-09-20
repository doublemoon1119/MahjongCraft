package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationInputSpec
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionPlayerRelationDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionActionTileSelectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoundPreparationPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WaitingTileAvailabilityDto
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.judgment.DiscardReadinessAnalysis
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailability
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.vocabularyActionId
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
        val tileOrder = moduleRegistry.getModule(state.config).tileOrder
        val resolvedCandidates = candidateResolver.resolveActionCandidates(gameId, playerId) ?: return null
        val actions = resolvedCandidates.actions
        val preparation = game.pendingRoundPreparation
            ?.takeIf { playerId !in it.completedPlayerIds }
            ?.inputSpecsByPlayerId
            ?.get(playerId)
            ?.toPrompt { tileId ->
                player.hand.tiles.firstOrNull { it.id == tileId }?.tile?.toAssetKey(tileAssetRegistry)
            }
        val analyses = if (phase == PlayerDecisionPhase.OWN_TURN) {
            resolvedCandidates.discardAnalyses.map { it.toDto() }
        } else {
            emptyList()
        }
        val trigger = state.triggerContext(playerId)
        val orderedAiPlayerIds = game.roomPlayerIds.filter { id -> state.players.any { it.id == id && it.isAi } }
        return PlayerDecisionPromptDto(
            ruleModuleId = resolvedCandidates.ruleModuleId,
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
                val preview = candidate.action.previewTiles(player.hand, candidate.referenceTile, tileOrder)
                val requirement = candidate.tileSelectionRequirement
                val selectionTiles = requirement?.let {
                    candidateResolver.listTileSelectionCandidates(playerId, candidate)
                }.orEmpty()
                val actionAnalyses = if (phase == PlayerDecisionPhase.OWN_TURN && requirement != null) {
                    candidate.discardAnalyses.map { it.toDto() }
                } else {
                    emptyList()
                }
                PlayerDecisionActionDto(
                    token = candidate.token,
                    actionId = candidate.action.vocabularyActionId(),
                    referenceTileAssetKey = candidate.referenceTile?.toAssetKey(tileAssetRegistry),
                    previewTileAssetKeys = if (requirement == null) {
                        preview.tiles.map { it.toAssetKey(tileAssetRegistry) }
                    } else {
                        selectionTiles.map { it.tile.toAssetKey(tileAssetRegistry) }.distinct()
                    },
                    claimedTileIndex = preview.claimedTileIndex,
                    tileSelection = requirement?.let {
                        PlayerDecisionActionTileSelectionDto(
                            eligibleTileIds = selectionTiles.map { tile -> tile.tileId.toString() },
                            minCount = it.minCount,
                            maxCount = it.maxCount,
                            discardAnalyses = actionAnalyses,
                        )
                    },
                )
            },
            // 自己回合一律顯示剛摸到的牌，不依賴候選動作是否帶有 referenceTile。
            triggerTileAssetKey = when (phase) {
                PlayerDecisionPhase.OWN_TURN -> player.hand.lastDrawn?.tile?.toAssetKey(tileAssetRegistry)
                else -> actions.firstNotNullOfOrNull { it.referenceTile }?.toAssetKey(tileAssetRegistry)
            },
            triggerPlayerId = trigger?.playerId?.toString(),
            triggerPlayerName = trigger?.playerId?.let { sourceId ->
                val sourcePlayer = state.players.firstOrNull { it.id == sourceId }
                if (sourcePlayer?.isAi == true) aiPlayerDisplayName(sourceId, orderedAiPlayerIds) else null
            },
            triggerPlayerRelation = trigger?.relation,
            triggerActionId = trigger?.actionId,
            preparation = preparation,
            discardAnalyses = analyses,
        )
    }

    /** 將規則模組打牌分析結果轉為私人 prompt 網路值；牌面轉 asset key 是平台層的職責，規則層只回傳 [Tile]。 */
    private fun DiscardReadinessAnalysis.toDto(): DiscardReadinessAnalysisDto = DiscardReadinessAnalysisDto(
        discardTileId = discardTileId.toString(),
        waitingTiles = waitingTiles.map { it.toDto() },
        statusIndicatorId = statusIndicatorId,
    )

    /** 將一張等待牌的規則層可用性轉為私人 prompt 網路值。 */
    private fun WaitingTileAvailability.toDto(): WaitingTileAvailabilityDto = WaitingTileAvailabilityDto(
        tileAssetKey = tile.toAssetKey(tileAssetRegistry),
        remainingCount = remainingCount,
        winAvailability = winAvailability,
    )

    /** 組合不依同步時間變化的決策識別碼。 */
    private fun buildDecisionKey(
        gameId: Uuid,
        playerId: Uuid,
        phase: PlayerDecisionPhase,
        preparation: RoundPreparationPromptDto?,
        reference: Any?,
    ): String = listOf(gameId, playerId, phase, reference, preparation?.hashCode()).joinToString(":")
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

/**
 * 建立動作完成後的牌組預覽；第三方動作沒有受控牌組資料時安全地只顯示參考牌。
 *
 * 卡片預覽一律直立顯示，不套用鳴牌後最終桌面朝向——那是給實際擺上桌的牌用的空間慣例，套在決策卡片上
 * 反而讓玩家要多一拍才能解讀。只有吃才會標出 [ActionTilePreview.claimedTileIndex]（三張牌本身花色/
 * 數值不同，框出來才有辨識意義）；碰跟槓的牌彼此看起來完全一樣，框哪一張都沒有實質資訊，不標記。
 */
private fun GameAction.previewTiles(
    hand: Hand,
    referenceTile: Tile?,
    tileOrder: TileOrder,
): ActionTilePreview {
    val identifiedById = hand.standingTiles.associateBy { it.id }
    fun tile(id: Uuid): Tile? = identifiedById[id]?.tile
    return when (this) {
        is GameAction.Chi -> {
            val sorted = (withTiles.mapNotNull(::tile) + listOfNotNull(referenceTile)).sortedWith(tileOrder)
            ActionTilePreview(sorted, claimedTileIndex = referenceTile?.let { sorted.indexOf(it) }?.takeIf { it >= 0 })
        }
        is GameAction.Pon -> ActionTilePreview(withTiles.mapNotNull(::tile) + listOfNotNull(referenceTile))
        is GameAction.Kan -> ActionTilePreview(withTiles.mapNotNull(::tile) + listOfNotNull(referenceTile ?: tile(tileId)))
        is GameAction.Ron, GameAction.Tsumo -> ActionTilePreview(listOfNotNull(referenceTile))
        is GameAction.ExhaustiveDraw -> ActionTilePreview(reason.previewTiles(hand))
        else -> ActionTilePreview(listOfNotNull(referenceTile))
    }
}

/** 操作 HUD 一組牌面，[claimedTileIndex] 只有吃才會給值。 */
private data class ActionTilePreview(
    val tiles: List<Tile>,
    val claimedTileIndex: Int? = null,
)

/**
 * 建立反應 HUD 的來源玩家、相對位置及動作語意。
 *
 * 相對位置沿用 [TableState.relativeDirectionOf]；觸發者是自己（[RelativeDirection.Self]）在反應情境不會
 * 發生，視為沒有觸發者。
 */
private fun TableState.triggerContext(playerId: Uuid): TriggerContext? {
    val sourceId = pendingReaction?.discarderId ?: pendingKanReaction?.declarerId ?: return null
    val relation = when (relativeDirectionOf(playerId, sourceId)) {
        RelativeDirection.Left -> DecisionPlayerRelationDto.LEFT
        RelativeDirection.Across -> DecisionPlayerRelationDto.ACROSS
        RelativeDirection.Right -> DecisionPlayerRelationDto.RIGHT
        RelativeDirection.Self -> return null
    }
    return TriggerContext(
        playerId = sourceId,
        relation = relation,
        actionId = pendingKanReaction?.kanAction?.vocabularyActionId() ?: BuiltInGameActionIds.DISCARD,
    )
}

/** 一次他家反應的公開來源資訊。 */
private data class TriggerContext(val playerId: Uuid, val relation: DecisionPlayerRelationDto, val actionId: String)
