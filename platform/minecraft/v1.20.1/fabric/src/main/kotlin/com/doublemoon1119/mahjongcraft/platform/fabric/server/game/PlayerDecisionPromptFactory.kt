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
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.judgment.DiscardReadinessAnalysis
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailability
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
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
        val actions = candidateResolver.listActionCandidates(playerId)
        val preparation = game.pendingRoundPreparation
            ?.takeIf { playerId !in it.completedPlayerIds }
            ?.inputSpecsByPlayerId
            ?.get(playerId)
            ?.toPrompt { tileId ->
                player.hand.tiles.firstOrNull { it.id == tileId }?.tile?.toAssetKey(tileAssetRegistry)
            }
        val analyzer = moduleRegistry.getModule(state.config).createDiscardReadinessAnalyzer()
        val analyses = if (phase == PlayerDecisionPhase.OWN_TURN) {
            analyzer?.analyze(state, player)?.map { it.toDto() }.orEmpty()
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
                val preview = candidate.action.previewTiles(player.hand, candidate.referenceTile, tileOrder)
                val requirement = candidate.tileSelectionRequirement
                val selectionTiles = requirement?.let {
                    candidateResolver.listTileSelectionCandidates(playerId, candidate)
                }.orEmpty()
                val actionAnalyses = if (phase == PlayerDecisionPhase.OWN_TURN && requirement != null) {
                    analyzer?.analyzeForAction(state, player, candidate.action)
                        ?.filter { it.discardTileId in requirement.eligibleTileIds }
                        ?.map { it.toDto() }
                        .orEmpty()
                } else {
                    emptyList()
                }
                PlayerDecisionActionDto(
                    token = candidate.token,
                    actionId = candidate.action.presentationId(),
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
 * 將內建與第三方動作轉成穩定顯示 ID，client 端 [translationKey] 依此組出完整翻譯鍵。
 *
 * 槓的三種類型字尾須與語系檔 `hud.action.kan_open`／`kan_closed`／`kan_added` 一致。
 */
internal fun GameAction.presentationId(): String = when (this) {
    GameAction.Tsumo -> "mahjongcraft:tsumo"
    is GameAction.Ron -> "mahjongcraft:ron"
    is GameAction.Chi -> "mahjongcraft:chi"
    is GameAction.Pon -> "mahjongcraft:pon"
    is GameAction.Kan -> when (type) {
        GameAction.KanType.OPEN_KAN -> "mahjongcraft:kan_open"
        GameAction.KanType.CLOSED_KAN -> "mahjongcraft:kan_closed"
        GameAction.KanType.ADDED_KAN -> "mahjongcraft:kan_added"
    }
    GameAction.Pass -> "mahjongcraft:pass"
    is GameAction.ExhaustiveDraw -> reason.id
    is GameAction.Extension -> value.id
    else -> "mahjongcraft:action"
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

/** 從目前等待捨牌反應的出牌者解析相對來源方向。 */
private fun TableState.claimSource(playerId: Uuid): ClaimSource {
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
private fun TableState.triggerContext(playerId: Uuid): TriggerContext? {
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
