package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationInputSpec
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.vocabularyActionId
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionPlayerRelationDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionActionTileSelectionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.RoundPreparationPromptDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.aiPlayerDisplayName
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import java.security.MessageDigest
import kotlin.uuid.Uuid

/**
 * 只從權威桌況建立指定玩家可見的操作 HUD prompt。
 *
 * @property gameRepository 讀取對局權威狀態。
 * @property candidateResolver 解析合法動作及手牌候選。
 * @property moduleRegistry 解析規則模組與牌序。
 * @property tileAssetRegistry 將牌面轉成素材 key。
 * @property analysisDtoMapper 將捨牌分析轉成同步 DTO。
 */
class PlayerDecisionPromptFactory(
    private val gameRepository: GameRepository,
    private val candidateResolver: GameActionCandidateResolver,
    private val moduleRegistry: MahjongModuleRegistry,
    private val tileAssetRegistry: MinecraftTileAssetRegistry,
    private val analysisDtoMapper: ReadinessAnalysisDtoMapper,
) {
    /**
     * 建立目前決策的私人 prompt；遊戲或玩家已失效時回傳 null。
     *
     * @param gameId 對局識別碼。
     * @param playerId prompt 接收玩家識別碼。
     * @param phase 目前決策階段。
     * @return 只包含該玩家可見資料的 prompt；狀態已失效時為 null。
     */
    suspend fun create(gameId: Uuid, playerId: Uuid, phase: PlayerDecisionPhase): PlayerDecisionPromptDto? {
        val game = gameRepository.getGame(gameId) ?: return null
        val state = game.tableState
        val player = state.players.firstOrNull { it.id == playerId } ?: return null
        val module = moduleRegistry.getModule(state.config)
        val tileOrder = module.tileOrder
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
            resolvedCandidates.discardAnalyses.map(analysisDtoMapper::toDto)
        } else {
            emptyList()
        }
        val trigger = state.triggerContext(playerId)
        val reactedTile = state.reactedTile()
        val orderedAiPlayerIds = game.roomPlayerIds.filter(game::isAi)
        return PlayerDecisionPromptDto(
            ruleModuleId = resolvedCandidates.ruleModuleId,
            decisionKey = buildPlayerDecisionKey(
                gameId,
                playerId,
                phase,
                preparation,
                when (phase) {
                    PlayerDecisionPhase.OWN_TURN -> player.hand.lastDrawn?.id ?: player.actionHistory.lastOrNull()?.hashCode()
                    PlayerDecisionPhase.DISCARD_REACTION -> state.pendingReaction?.tileId
                    PlayerDecisionPhase.ROBBING_REACTION -> state.pendingRobbingReaction?.robbedTile?.id
                    PlayerDecisionPhase.ROUND_PREPARATION -> game.pendingRoundPreparation?.stepIndex
                },
                if (phase == PlayerDecisionPhase.OWN_TURN) actions.decisionIdentity() else null,
            ),
            actions = actions.map { candidate ->
                val setAsideTile = (candidate.action as? GameAction.Extension)?.let { module.tileSetAsideBy(player, it) }
                val preview = candidate.action.previewTiles(
                    hand = player.hand,
                    reactedTile = reactedTile,
                    tileOrder = tileOrder,
                    setAsideTile = setAsideTile?.tile,
                )
                val requirement = candidate.tileSelectionRequirement
                val selectionTiles = requirement?.let {
                    candidateResolver.listTileSelectionCandidates(playerId, candidate)
                }.orEmpty()
                val actionAnalyses = if (phase == PlayerDecisionPhase.OWN_TURN && requirement != null) {
                    candidate.discardAnalyses.map(analysisDtoMapper::toDto)
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
            // 自己回合顯示剛摸到的牌，反應他家時顯示正在反應的那張牌。
            triggerTileAssetKey = when (phase) {
                PlayerDecisionPhase.OWN_TURN -> player.hand.lastDrawn?.tile?.toAssetKey(tileAssetRegistry)
                else -> reactedTile?.tile?.toAssetKey(tileAssetRegistry)
            },
            triggerPlayerId = trigger?.playerId?.toString(),
            triggerPlayerName = trigger?.playerId?.let { sourceId ->
                if (game.isAi(sourceId)) aiPlayerDisplayName(sourceId, orderedAiPlayerIds) else null
            },
            triggerPlayerRelation = trigger?.relation,
            triggerActionId = trigger?.actionId,
            preparation = preparation,
            discardAnalyses = analyses,
        )
    }
}

/**
 * 組合不依同步時間變化的決策識別碼。
 *
 * @param gameId 對局識別碼。
 * @param playerId prompt 接收玩家識別碼。
 * @param phase 決策階段。
 * @param preparation 目前受控的準備輸入 prompt。
 * @param reference 用來區分目前決策的參考狀態。
 * @param actionIdentity 目前合法動作候選的穩定識別片段；非自己回合時為 null。
 * @return 由上述狀態組成的穩定識別字串。
 */
internal fun buildPlayerDecisionKey(
    gameId: Uuid,
    playerId: Uuid,
    phase: PlayerDecisionPhase,
    preparation: RoundPreparationPromptDto?,
    reference: Any?,
    actionIdentity: String?,
): String {
    val base = listOf(gameId, playerId, phase, reference, preparation?.hashCode())
    return (base + listOfNotNull(actionIdentity?.let(::decisionIdentityFingerprint))).joinToString(":")
}

/**
 * 將候選清單壓成固定長度，避免決策識別碼隨手牌數量膨脹。
 *
 * @param identity 已排序的候選識別字串。
 * @return SHA-256 摘要的十六進位文字。
 */
private fun decisionIdentityFingerprint(identity: String): String = MessageDigest
    .getInstance("SHA-256")
    .digest(identity.encodeToByteArray())
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

/**
 * 將目前合法動作候選轉成不依物件實例或列表順序變化的識別片段。
 *
 * @receiver 目前可提交的完整候選集合。
 * @return 代表候選動作及其選牌限制的固定字串；候選內容相同時結果相同。
 */
internal fun List<GameActionCandidate>.decisionIdentity(): String = asSequence()
    .map { candidate ->
        listOf(
            "${candidate.token.length}:${candidate.token}",
            candidate.action.decisionIdentity(),
            candidate.tileSelectionRequirement?.let { requirement ->
                listOf(
                    requirement.eligibleTileIds.map(Uuid::toString).sorted().joinToString(","),
                    requirement.minCount.toString(),
                    requirement.maxCount.toString(),
                ).joinToString("/")
            },
        ).joinToString("#")
    }
    .sorted()
    .joinToString(";")

/**
 * 將動作轉成包含其實體牌識別碼的穩定字串。
 *
 * @receiver 目前可送出的動作。
 * @return 動作種類、擴充 ID 與相關牌張組成的識別字串。
 */
private fun GameAction.decisionIdentity(): String = when (this) {
    GameAction.Draw -> "draw"
    is GameAction.Discard -> "discard:$tileId"
    is GameAction.Chi -> "chi:$tileId:${withTiles.map(Uuid::toString).sorted().joinToString(",")}"
    is GameAction.Pon -> "pon:$tileId:${withTiles.map(Uuid::toString).sorted().joinToString(",")}"
    is GameAction.Kan -> "kan:${type.name.lowercase()}:$tileId:${withTiles.map(Uuid::toString).sorted().joinToString(",")}"
    is GameAction.Ron -> "ron:$tileId"
    GameAction.Tsumo -> "tsumo"
    GameAction.Pass -> "pass"
    is GameAction.ExhaustiveDraw -> "exhaustive_draw:${reason.id}"
    is GameAction.Extension -> "extension:${value.id}"
    GameAction.GameStarted -> "game_started"
    GameAction.RoundStarted -> "round_started"
    GameAction.MatchEnded -> "match_ended"
    is GameAction.DiceRolled -> "dice_rolled:$roll"
}

/**
 * 將受控 preparation input 轉成不暴露其他玩家提交的私人 prompt。
 *
 * @param resolveAssetKey 以牌張識別碼取得可顯示素材 key 的函式。
 * @return 對應的私人準備階段 prompt。
 */
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
 * 建立動作完成後的牌組預覽。每張牌都依動作帶的牌 ID 查出：來源是自己的手牌（含剛摸到的牌）與正在反應的
 * [reactedTile]（他家的捨牌，或等待搶和的宣告牌）；移出手牌的動作顯示規則回報要移出的 [setAsideTile]，規則沒有
 * 回報時不顯示牌。不依觸發這次決策的牌去推測。
 *
 * 卡片預覽一律直立顯示，不套用鳴牌後最終桌面朝向——那是給實際擺上桌的牌用的空間慣例，套在決策卡片上
 * 反而讓玩家要多一拍才能解讀。只有吃才會標出 [ActionTilePreview.claimedTileIndex]（三張牌本身花色/
 * 數值不同，框出來才有辨識意義）；碰跟槓的牌彼此看起來完全一樣，框哪一張都沒有實質資訊，不標記。
 *
 * @param hand 玩家目前的手牌。
 * @param reactedTile 正在回應的牌；沒有反應來源時為 null。
 * @param tileOrder 該規則的牌序。
 * @param setAsideTile 規則指定要移出手牌的牌；無指定時為 null。
 * @return 動作完成後的牌面預覽及必要時標示的鳴取牌索引。
 */
internal fun GameAction.previewTiles(
    hand: Hand,
    reactedTile: IdentifiedTile?,
    tileOrder: TileOrder,
    setAsideTile: Tile?,
): ActionTilePreview {
    val knownTiles = (hand.standingTiles + listOfNotNull(reactedTile)).associateBy { it.id }
    fun tile(id: Uuid): Tile? = knownTiles[id]?.tile
    return when (this) {
        is GameAction.Chi -> {
            val sorted = (withTiles + tileId).mapNotNull(::tile).sortedWith(tileOrder)
            ActionTilePreview(sorted, claimedTileIndex = tile(tileId)?.let { sorted.indexOf(it) }?.takeIf { it >= 0 })
        }
        is GameAction.Pon -> ActionTilePreview((withTiles + tileId).mapNotNull(::tile))
        is GameAction.Kan -> ActionTilePreview((withTiles + tileId).mapNotNull(::tile))
        is GameAction.Ron -> ActionTilePreview(listOfNotNull(tile(tileId)))
        GameAction.Tsumo -> ActionTilePreview(listOfNotNull(hand.lastDrawn?.tile))
        is GameAction.ExhaustiveDraw -> ActionTilePreview(reason.previewTiles(hand))
        is GameAction.Extension -> ActionTilePreview(listOfNotNull(setAsideTile))
        else -> ActionTilePreview(emptyList())
    }
}

/** 正在反應的那張牌：等待搶和的宣告牌，或等待反應的他家捨牌；不在反應階段時為 null。 */
private fun TableState.reactedTile(): IdentifiedTile? = pendingRobbingReaction?.robbedTile
    ?: pendingReaction?.let { pending ->
        players.firstNotNullOfOrNull { player -> player.discardPile.entries.firstOrNull { it.tile.id == pending.tileId }?.tile }
    }

/**
 * 操作 HUD 一組牌面，[claimedTileIndex] 只有吃才會給值。
 *
 * @property tiles 預覽牌面。
 * @property claimedTileIndex 吃牌時鳴取牌在預覽中的索引；其他動作為 null。
 */
internal data class ActionTilePreview(
    val tiles: List<Tile>,
    val claimedTileIndex: Int? = null,
)

/**
 * 建立反應 HUD 的來源玩家、相對位置及動作語意。
 *
 * 相對位置沿用 [TableState.relativeDirectionOf]；觸發者是自己（[RelativeDirection.Self]）在反應情境不會
 * 發生，視為沒有觸發者。
 *
 * @param playerId prompt 接收玩家的識別碼。
 * @return 反應觸發者及其相對位置；目前沒有他家反應時為 null。
 */
private fun TableState.triggerContext(playerId: Uuid): TriggerContext? {
    val sourceId = pendingReaction?.discarderId ?: pendingRobbingReaction?.declarerId ?: return null
    val relation = when (relativeDirectionOf(playerId, sourceId)) {
        RelativeDirection.Left -> DecisionPlayerRelationDto.LEFT
        RelativeDirection.Across -> DecisionPlayerRelationDto.ACROSS
        RelativeDirection.Right -> DecisionPlayerRelationDto.RIGHT
        RelativeDirection.Self -> return null
    }
    return TriggerContext(
        playerId = sourceId,
        relation = relation,
        actionId = pendingRobbingReaction?.declaredAction?.vocabularyActionId() ?: BuiltInGameActionIds.DISCARD,
    )
}

/**
 * 一次他家反應的公開來源資訊。
 *
 * @property playerId 觸發反應的玩家識別碼。
 * @property relation 該玩家相對 prompt 接收者的位置。
 * @property actionId 觸發反應的動作識別碼。
 */
private data class TriggerContext(val playerId: Uuid, val relation: DecisionPlayerRelationDto, val actionId: String)
