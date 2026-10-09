package com.doublemoon1119.mahjongcraft.flow.server.game.repository

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

/**
 * [GameRepository.withExpectedGame] 的結果。
 *
 * @param T 命令的回傳型別。
 */
sealed interface ExpectedGameResult<out T> {
    /**
     * 命令已在預期的遊戲上執行完畢。
     *
     * @property value 命令的回傳值。
     */
    data class Applied<T>(val value: T) : ExpectedGameResult<T>

    /** 寫入前權威遊戲已不是預期的遊戲；命令沒有寫入這一局，並在寫入時被中止。 */
    data object Stale : ExpectedGameResult<Nothing>
}

/**
 * 在 [GameRepository.withExpectedGame] 範圍內對同一局做了第二次權威寫入。
 *
 * 第一次寫入可能已經提交，因此不能重試，也不代表已回滾。
 *
 * @param gameId 被寫入兩次的遊戲。
 */
class ExpectedGameWrittenTwiceException(gameId: Uuid) : IllegalStateException("Game $gameId was written more than once under an expected game")

/** 寫入前發現權威遊戲已不是預期的遊戲；只在 [runWithExpectedGame] 內傳遞，不外流。 */
private class StaleExpectedGameException : RuntimeException("The authoritative game is no longer the expected game")

/**
 * 一次 [GameRepository.withExpectedGame] 的範圍：預期的遊戲，以及範圍內是否已寫入、判定過期或違反單次寫入契約。
 *
 * 只在單一協程內依序使用。
 *
 * @property gameId 受條件限制的遊戲。
 * @property expectedGame 預期的權威遊戲。
 */
internal class ExpectedGameScope(
    val gameId: Uuid,
    private val expectedGame: Game,
) : AbstractCoroutineContextElement(Key) {
    /** 範圍內是否已判定過期；判定後這一局的寫入一律拒絕。 */
    var stale = false
        private set

    /** 範圍內是否已寫入這一局。 */
    private var written = false

    /** 範圍內是否曾第二次寫入這一局；一旦違反就不會清除，即使命令攔下了例外。 */
    var violated = false
        private set

    /**
     * 在權威交易內、執行修改區塊之前檢查這次寫入。
     *
     * @param current 交易中目前的權威遊戲。
     * @throws ExpectedGameWrittenTwiceException 範圍內已寫入過這一局。
     */
    fun checkBeforeWrite(current: Game?) {
        if (stale) throw StaleExpectedGameException()
        if (written) {
            violated = true
            throw ExpectedGameWrittenTwiceException(gameId)
        }
        written = true
        if (current != expectedGame) {
            stale = true
            throw StaleExpectedGameException()
        }
    }

    /** 在協程情境中查詢 [ExpectedGameScope] 的 key。 */
    companion object Key : CoroutineContext.Key<ExpectedGameScope>
}

/**
 * 目前協程中限制 [gameId] 的範圍；沒有時為 null。
 *
 * @param gameId 即將寫入的遊戲。
 */
internal suspend fun expectedGameScopeFor(gameId: Uuid): ExpectedGameScope? = currentCoroutineContext()[ExpectedGameScope]?.takeIf { it.gameId == gameId }

/**
 * [GameRepository.withExpectedGame] 的共用實作：在帶有 [ExpectedGameScope] 的協程情境中執行 [command]。
 *
 * 命令即使攔下了過期的例外，只要範圍已判定過期，結果仍是 [ExpectedGameResult.Stale]；命令即使攔下了第二次寫入的例外，
 * 範圍結束時仍丟出 [ExpectedGameWrittenTwiceException]，不會被當成正常完成。
 */
internal suspend fun <T> runWithExpectedGame(
    gameId: Uuid,
    expectedGame: Game,
    command: suspend () -> T,
): ExpectedGameResult<T> {
    require(currentCoroutineContext()[ExpectedGameScope] == null) { "Expected game scopes cannot be nested" }
    val scope = ExpectedGameScope(gameId, expectedGame)
    val result = try {
        val value = withContext(scope) { command() }
        if (scope.stale) ExpectedGameResult.Stale else ExpectedGameResult.Applied(value)
    } catch (stale: StaleExpectedGameException) {
        ExpectedGameResult.Stale
    }
    if (scope.violated) throw ExpectedGameWrittenTwiceException(gameId)
    return result
}
