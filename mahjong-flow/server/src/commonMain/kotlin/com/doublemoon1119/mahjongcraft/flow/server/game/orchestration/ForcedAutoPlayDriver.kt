package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import kotlin.uuid.Uuid

/**
 * 為已耗盡思考時間的真人玩家產生安全的自動操作。
 *
 * 依序在搶槓、捨牌反應視窗中找出第一位尚未回應、且已進入強制自動操作的玩家；兩種視窗都沒有時才看目前行動的玩家。
 *
 * @property gameRepository 讀取包含強制自動操作狀態的權威遊戲。
 * @property commandResolver 依當下階段與規則限制產生安全代打命令。
 */
class ForcedAutoPlayDriver(
    private val gameRepository: GameRepository,
    private val commandResolver: AutomaticGameCommandResolver,
) {
    /**
     * 解析目前下一個必須由伺服器操作的玩家與命令。
     *
     * @param gameId 欲處理的遊戲。
     * @return 玩家與合法代打命令；目前沒有可處理決策、或對局已結束時為 null。
     * @throws RuleContractViolationException 必須代打的玩家沒有安全合法的命令。
     */
    suspend fun resolveNextAction(gameId: Uuid): Pair<Uuid, GameCommand>? {
        val game = gameRepository.getGame(gameId) ?: return null
        if (game.isMatchOver) return null
        if (game.pendingRuleDecision != null) return null
        val state = game.tableState
        val forcedPlayerIds = game.forcedAutoPlayPlayerIds
        val robbing = state.pendingRobbingReaction
        val reaction = state.pendingReaction
        val playerId = robbing?.eligiblePlayerIds?.firstOrNull { it in forcedPlayerIds && it !in robbing.responses }
            ?: reaction?.eligiblePlayerIds?.firstOrNull { it in forcedPlayerIds && it !in reaction.responses }
            ?: state.currentPlayer.id.takeIf { it in forcedPlayerIds && robbing == null && reaction == null }
            ?: return null
        val command = commandResolver.resolve(state, playerId)
            ?: throw RuleContractViolationException(
                reasonId = GameAbortReasonIds.AUTOMATIC_DECISION_FAILED,
                diagnostic = "Forced automatic player $playerId in game $gameId has no safe fallback command",
            )
        return playerId to command
    }
}
