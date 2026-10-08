package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import kotlin.uuid.Uuid

/**
 * 決策計時排程每個 tick 的處理，與 Fabric 事件和具體服務分開，讓接線可以單獨測試。
 *
 * 每個 tick 先由 [advanceTick] 把輪到的對局標為待推進；[process] 則依序執行：
 * 1. 全域處理到期時，先對所有對局暫停 busy 桌並重新計時，再判定逾時；逾時已推進的對局清除待推進標記。
 * 2. 推進這個 tick 取出的對局：呈現播放中的略過；有待完成的胡牌或流局流程時先補完、不再驅動玩家；否則驅動自動操作並補做
 *    自動摸牌。單局失敗交給 [onAdvanceFailed]，其他對局照常推進。
 * 3. 全域處理到期時，最後同步所有倒數並回報全域處理完成。
 *
 * 新對局在 [process] 時才登記到輪轉中，在下一次輪到它的位置推進，也就是開始後一個週期內。
 *
 * @property rotation 各對局推進的 tick 位置與待推進標記。
 * @property listGames 列出目前進行中的對局。
 * @property reconcile 暫停 busy 桌並重新計時一局。
 * @property settleTimeouts 判定所有已逾時的決策並推進受影響的對局，回傳已推進的對局。
 * @property isBusy 對局是否仍在播放呈現。
 * @property resumeTransition 補完一局待完成的胡牌或流局流程；有補做時回傳 true。
 * @property drive 驅動一局的自動操作。
 * @property autoDraw 補做一局真人玩家的自動摸牌。
 * @property synchronizeAll 同步所有真人決策者的倒數。
 * @property onAdvanceFailed 接收推進失敗的對局與例外。
 */
internal class DecisionTickProcessor(
    private val rotation: GameAdvanceRotation,
    private val listGames: suspend () -> Collection<Uuid>,
    private val reconcile: suspend (Uuid) -> Unit,
    private val settleTimeouts: suspend () -> Collection<Uuid>,
    private val isBusy: (Uuid) -> Boolean,
    private val resumeTransition: suspend (Uuid) -> Boolean,
    private val drive: suspend (Uuid) -> Unit,
    private val autoDraw: suspend (Uuid) -> Unit,
    private val synchronizeAll: suspend () -> Unit,
    private val onAdvanceFailed: (Uuid, Exception) -> Unit,
) {
    /** 伺服器前進一個 tick；上一個 tick 的處理仍在進行時也要呼叫，讓輪到的對局標為待推進。 */
    fun advanceTick() = rotation.advanceTick()

    /** 執行這個 tick 的處理。 */
    suspend fun process() {
        val gameIds = listGames()
        rotation.syncGames(gameIds)
        val runGlobal = rotation.globalDue
        if (runGlobal) {
            // 必須先暫停所有 busy 桌，才可 claim timeout；否則同一輪剛開始播放動畫的玩家仍可能
            // 先被判定逾時，之後才輪到 busy 檢查。
            gameIds.forEach { gameId -> reconcile(gameId) }
            rotation.markAdvanced(settleTimeouts())
        }
        // 輪到的對局都巡一遍，不是只處理剛好逾時的對局：玩家一旦被標記成強制自動操作，或整桌都是 AI，
        // 就不會再產生任何逾時事件，這種桌子只能靠巡邏推進。桌子本來就沒事要做時，這些呼叫很快就會返回。
        advanceEachGame(gameIds = rotation.takeDue(), advance = ::advanceGame, onFailure = onAdvanceFailed)
        if (runGlobal) {
            synchronizeAll()
            rotation.completeGlobal()
        }
    }

    /** 推進一局。 */
    private suspend fun advanceGame(gameId: Uuid) {
        if (isBusy(gameId)) return
        if (resumeTransition(gameId)) return
        drive(gameId)
        autoDraw(gameId)
    }
}
