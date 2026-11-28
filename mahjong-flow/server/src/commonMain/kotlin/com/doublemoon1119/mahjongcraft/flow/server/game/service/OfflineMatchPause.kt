package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.concurrent.concurrentMutableMap
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * 判斷對局是否因所有真人都離線而暫停，並累計暫停的時間。
 *
 * - 對局有真人座位、而且所有真人都離線時算暫停。只有 AI 的對局不受影響。
 * - 每次判斷到仍在暫停時，把距離上次判斷經過的時間累加到 [Game.offlinePauseElapsed]。第一次判斷到暫停時只記下時刻。
 * - 以 [clock] 量測，伺服器停止期間不計入；重新啟動後從下一次判斷開始重新計算。
 *
 * @property gameRepository 讀取對局的真人座位並寫回累計時間。
 * @property presence 回答真人玩家是否在線。
 * @property clock 量測暫停時間的單調時間。
 */
class OfflineMatchPause(
    private val gameRepository: GameRepository,
    private val presence: PlayerPresence,
    private val clock: MonotonicClock,
) {
    /** 正在暫停的對局，以及暫停中的場次與上次累加暫停時間的時刻。 */
    private val marks = concurrentMutableMap<Uuid, PauseMark>()

    /**
     * 判斷對局目前是否暫停，仍在暫停時累加經過的時間。
     *
     * @param gameId 欲判斷的遊戲識別碼。
     * @return 對局有真人座位、而且所有真人都離線時為 true。
     */
    suspend fun update(gameId: Uuid): Boolean {
        val game = gameRepository.getGame(gameId)
        val humans = game?.humanPlayerIds.orEmpty()
        if (game == null || humans.isEmpty() || humans.any(presence::isOnline)) {
            marks.remove(gameId)
            return false
        }
        val now = clock.now()
        val previous = marks.put(gameId, PauseMark(game.matchId, now))
        // 同一張桌子的下一場沿用相同的對局識別碼，上一場留下的紀錄不算進這一場。
        if (previous == null || previous.matchId != game.matchId) {
            // 開始新的暫停時順便移除已不存在的對局，例如暫停中被中止的對局。
            val gameIds = gameRepository.getAllGameIds()
            marks.keys.retainAll(gameIds)
            return true
        }
        val elapsed = now - previous.at
        if (elapsed.isPositive()) {
            gameRepository.updateGame(gameId) { game ->
                (game?.copy(offlinePauseElapsed = game.offlinePauseElapsed + elapsed) ?: game) to Unit
            }
        }
        return true
    }

    /**
     * 查詢最近一次判斷時對局是否正在暫停，不累加時間。
     *
     * @param gameId 欲查詢的遊戲識別碼。
     * @return 最近一次 [update] 判斷為暫停時為 true。伺服器啟動後尚未判斷過的對局為 false。
     */
    fun isPaused(gameId: Uuid): Boolean = gameId in marks

    /** 清除所有暫停中的時刻紀錄。伺服器工作階段結束時呼叫，下一個工作階段從第一次判斷重新計算。 */
    fun clear() {
        marks.clear()
    }

    /**
     * 一場暫停中對局的紀錄。
     *
     * @property matchId 暫停中的場次。
     * @property at 上次累加暫停時間的時刻。
     */
    private data class PauseMark(val matchId: Uuid, val at: Duration)
}
