package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid

/**
 * 把對局的自動推進分散到一個週期內的各個 tick，避免所有對局擠在同一個 tick 推進。
 *
 * 每局固定分到週期中的一個位置，新對局分到目前對局最少的位置；每個 tick 把分到這個位置的對局標為待推進，再依標記先後
 * 取出一批推進。每局最多只有一次待推進，不累積補跑次數；每 tick 最多取出「進行中對局數 ÷ 週期（無條件進位）＋ 1」局，
 * 正常負載下每局每個週期推進一次，處理工作延後時待推進的對局依序逐步消化，不以突發補跑追回節奏。
 *
 * 另外記錄全域處理（例如決策逾時與倒數同步）的時機：距離上次全域處理滿一個週期時到期，由呼叫端在處理後回報完成。
 *
 * 只在同一個執行緒上依序使用。
 *
 * @property cycleTicks 一個週期的 tick 數；每局在一個週期內推進一次。
 */
class GameAdvanceRotation(private val cycleTicks: Int) {
    init {
        require(cycleTicks > 0) { "Rotation cycle must be positive" }
    }

    /** 每局分到的位置。 */
    private val slotByGame = HashMap<Uuid, Int>()

    /** 每個位置上的對局。 */
    private val gamesBySlot = Array(cycleTicks) { LinkedHashSet<Uuid>() }

    /** 待推進的對局，依標記先後排列。 */
    private val due = LinkedHashSet<Uuid>()

    /** 目前所在的位置。 */
    private var slot = cycleTicks - 1

    /** 距離上次全域處理經過的 tick 數。 */
    private var ticksSinceGlobal = 0

    /** 目前分配中的對局數。 */
    val gameCount: Int get() = slotByGame.size

    /** 待推進的對局數。 */
    val dueCount: Int get() = due.size

    /** 是否到了全域處理的時機：距離上次全域處理已滿一個週期。 */
    val globalDue: Boolean get() = ticksSinceGlobal >= cycleTicks

    /**
     * 依目前進行中的對局更新分配：新對局分到對局最少的位置（相同時取最前面的位置），已不存在的對局釋放位置並移除待推進。
     *
     * @param activeGameIds 目前進行中的對局。
     */
    fun syncGames(activeGameIds: Collection<Uuid>) {
        val active = activeGameIds.toSet()
        slotByGame.keys.filterNot { it in active }.forEach { gameId ->
            gamesBySlot[slotByGame.getValue(gameId)].remove(gameId)
            slotByGame.remove(gameId)
            due.remove(gameId)
        }
        active.filterNot { it in slotByGame }.forEach { gameId ->
            val target = gamesBySlot.indices.minBy { gamesBySlot[it].size }
            gamesBySlot[target] += gameId
            slotByGame[gameId] = target
        }
    }

    /** 前進一個 tick：把分到下一個位置的對局標為待推進；已待推進的對局保留原本的順序。 */
    fun advanceTick() {
        slot = (slot + 1) % cycleTicks
        due += gamesBySlot[slot]
        ticksSinceGlobal++
    }

    /**
     * 取出這個 tick 要推進的一批對局，依標記先後，最多「對局數 ÷ 週期（無條件進位）＋ 1」局。
     *
     * @return 要推進的對局；取出的對局不再待推進。
     */
    fun takeDue(): List<Uuid> {
        val budget = (gameCount + cycleTicks - 1) / cycleTicks + 1
        val taken = due.take(budget)
        due -= taken.toSet()
        return taken
    }

    /**
     * 這些對局已由其他流程推進（例如決策逾時後的自動操作），移除它們的待推進標記。
     *
     * @param gameIds 已推進的對局。
     */
    fun markAdvanced(gameIds: Collection<Uuid>) {
        due -= gameIds.toSet()
    }

    /** 全域處理已完成，下一次在一個週期後到期。 */
    fun completeGlobal() {
        ticksSinceGlobal = 0
    }
}

/**
 * 依序推進每局；單局丟出例外時交給 [onFailure]，其他對局照常推進。
 *
 * @param gameIds 要推進的對局。
 * @param advance 推進一局。
 * @param onFailure 接收推進失敗的對局與例外。
 */
internal suspend fun advanceEachGame(
    gameIds: List<Uuid>,
    advance: suspend (Uuid) -> Unit,
    onFailure: (Uuid, Exception) -> Unit,
) {
    gameIds.forEach { gameId ->
        try {
            advance(gameId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            onFailure(gameId, error)
        }
    }
}
