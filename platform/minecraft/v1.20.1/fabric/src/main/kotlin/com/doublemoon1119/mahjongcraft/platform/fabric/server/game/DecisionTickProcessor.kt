package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import kotlin.uuid.Uuid

/**
 * 決策計時排程每個 tick 的處理，與 Fabric 事件和具體服務分開，讓接線可以單獨測試。
 *
 * 每個 tick 先由 [advanceTick] 把輪到的對局標為待推進；[process] 則依序執行：
 * 1. 全域處理到期時，先對所有對局暫停 busy 桌並重新計時，再判定逾時並為受影響的對局請求推進；這些對局清除待推進標記。
 * 2. 為這個 tick 取出的對局請求推進：呈現播放中或已在推進中的略過，留待下一次輪到它。只請求、不等待推進完成，
 *    推進的內容（補完待完成流程、驅動自動操作、自動摸牌）由推進本身決定。
 * 3. 全域處理到期時，最後同步所有倒數並回報全域處理完成。
 *
 * 全域處理因此不會等待任何一局的 AI 決策。新對局在 [process] 時才登記到輪轉中，在下一次輪到它的位置推進，
 * 也就是開始後一個週期內。
 *
 * @property rotation 各對局推進的 tick 位置與待推進標記。
 * @property listGames 列出目前進行中的對局。
 * @property reconcile 暫停 busy 桌並重新計時一局。
 * @property settleTimeouts 判定所有已逾時的決策並為受影響的對局請求推進，回傳已請求推進的對局。
 * @property isBusy 對局是否仍在播放呈現。
 * @property isAdvancing 對局是否已在推進中。
 * @property requestAdvance 請求推進一局，不等待推進完成。
 * @property synchronizeAll 同步所有真人決策者的倒數。
 */
internal class DecisionTickProcessor(
    private val rotation: GameAdvanceRotation,
    private val listGames: suspend () -> Collection<Uuid>,
    private val reconcile: suspend (Uuid) -> Unit,
    private val settleTimeouts: suspend () -> Collection<Uuid>,
    private val isBusy: (Uuid) -> Boolean,
    private val isAdvancing: (Uuid) -> Boolean,
    private val requestAdvance: (Uuid) -> Unit,
    private val synchronizeAll: suspend () -> Unit,
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
        // 就不會再產生任何逾時事件，這種桌子只能靠巡邏推進。桌子本來就沒事要做時，推進很快就會結束。
        rotation.takeDue().forEach { gameId ->
            if (!isBusy(gameId) && !isAdvancing(gameId)) requestAdvance(gameId)
        }
        if (runGlobal) {
            synchronizeAll()
            rotation.completeGlobal()
        }
    }
}
