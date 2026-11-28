package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository

/**
 * 中止因所有真人都離線而暫停太久的對局。
 *
 * - 暫停的累計時間（`Game.offlinePauseElapsed`）達到 [MatchAbsenceSettings.offlinePauseLimit] 時，以
 *   [GameAbortReasonIds.PLAYERS_OFFLINE] 中止，不計成績，參與者回到房間。
 * - 時限為 0 時不中止任何對局。已經結束的對局不處理。
 *
 * @property gameRepository 列出進行中的對局。
 * @property settings 暫停時限的伺服器設定。
 * @property abortHandler 中止對局並還原房間。
 */
class OfflinePauseExpiryService(
    private val gameRepository: GameRepository,
    private val settings: MatchAbsenceSettings,
    private val abortHandler: GameAbortHandler,
) {
    /** 中止所有暫停超過時限的對局。 */
    suspend fun abortExpired() {
        val limit = settings.offlinePauseLimit
        if (!limit.isPositive()) return
        gameRepository.getAllGameIds().forEach { gameId ->
            val game = gameRepository.getGame(gameId) ?: return@forEach
            if (game.isMatchOver || game.offlinePauseElapsed < limit) return@forEach
            abortHandler.abort(
                expectedGame = game,
                reasonId = GameAbortReasonIds.PLAYERS_OFFLINE,
                diagnostic = "Every human player was offline for ${game.offlinePauseElapsed}; the limit is $limit",
            )
        }
    }
}
