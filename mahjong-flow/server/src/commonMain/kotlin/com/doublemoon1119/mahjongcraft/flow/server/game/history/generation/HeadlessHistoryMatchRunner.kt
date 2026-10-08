package com.doublemoon1119.mahjongcraft.flow.server.game.history.generation

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * 開發對局生成支援的日麻規則與場長，開局仍沿用正式隨機初始化；座位全部由 AI 坐滿。
 *
 * @property identifier 生成情境的穩定識別碼。
 * @property ruleConfig 對局規則設定；座位數取其人數上限。
 */
enum class HeadlessHistoryScenario(val identifier: String, val ruleConfig: RiichiFamilyRuleConfig) {
    /** 四人東風戰。 */
    RIICHI_EAST("mahjongcraft:riichi_east", RiichiRuleConfig(gameLength = RiichiGameLength.East)),

    /** 四人半莊戰。 */
    RIICHI_HANCHAN("mahjongcraft:riichi_hanchan", RiichiRuleConfig(gameLength = RiichiGameLength.TwoWinds)),

    /** 三人東風戰。 */
    THREE_PLAYER_RIICHI_EAST("mahjongcraft:riichi_three_player_east", ThreePlayerRiichiRuleConfig(gameLength = RiichiGameLength.East)),

    /** 三人半莊戰。 */
    THREE_PLAYER_RIICHI_HANCHAN("mahjongcraft:riichi_three_player_hanchan", ThreePlayerRiichiRuleConfig(gameLength = RiichiGameLength.TwoWinds)),
}

/**
 * 單場生成與逐批交付的有界限制。
 *
 * @property maxSteps 最多允許的權威流程步數。
 * @property maxDuration 單場包含保存等待的最大時間。
 * @property maxTransferBatchSize 單批最多事件數；不得切斷權威交易。
 * @property transferWaitTimeout 收集端每批保存及確認的最大時間。
 */
data class HeadlessHistoryRunLimits(
    val maxSteps: Int = 10_000,
    val maxDuration: Duration = 5.minutes,
    val maxTransferBatchSize: Int = 64,
    val transferWaitTimeout: Duration = 60.seconds,
) {
    init {
        require(maxSteps > 0) { "Maximum runner steps must be positive" }
        require(maxDuration.isPositive()) { "Maximum runner duration must be positive" }
        require(maxTransferBatchSize in 1..64) { "Transfer batch size must be between 1 and 64" }
        require(transferWaitTimeout.isPositive()) { "Transfer wait timeout must be positive" }
    }
}

/**
 * 交付收集端的一筆完整交易或已排空的終點。
 *
 * @property snapshot 來源已提交的權威快照。
 * @property game 目前來源對局；返回房間後為 null。
 * @property matchId 整場識別碼。
 * @property venueId 隔離場地識別碼。
 * @property terminal 僅在返回房間且所有事件已確認後為 true。
 * @property events 本批完整權威交易；終點批次為空。
 */
data class HeadlessHistoryProgress(
    val snapshot: AuthoritativeStateSnapshot,
    val game: Game?,
    val matchId: Uuid,
    val venueId: Uuid,
    val terminal: Boolean,
    val events: List<HistoryOutboxEvent>,
)

/** 真實 Flow 的隔離執行環境，每次只推進一個自動操作步驟。 */
interface HeadlessHistoryMatchRuntime {
    /** 場長情境。 */
    val scenario: HeadlessHistoryScenario

    /** 隔離的權威來源儲存。 */
    val store: AuthoritativeStateStore

    /** 來源場地識別碼。 */
    val venueId: Uuid

    /**
     * 取得初始化時固定的整場識別碼。
     * @return 整場識別碼。
     */
    suspend fun matchId(): Uuid

    /**
     * 推進一次正式自動流程。
     * @return 是否造成權威進展。
     */
    suspend fun step(): Boolean

    /**
     * 讀取來源目前對局。
     * @return 目前對局，或已返回房間的 null。
     */
    suspend fun currentGame(): Game?
}

/**
 * 以 cold Flow 交付完整交易；收集端確認後才可推進下一步。
 *
 * 不預先緩衝、不丟棄事件。收集端須在持久化寫入確認後，呼叫來源 store 的
 * acknowledgeHistoryEvents；只把資料加入正式 outbox 不能作為確認依據。
 *
 * @property runtime 真實的隔離執行環境。
 * @property limits 單場步數、批次及時間限制。
 */
class HeadlessHistoryMatchRunner(
    private val runtime: HeadlessHistoryMatchRuntime,
    private val limits: HeadlessHistoryRunLimits = HeadlessHistoryRunLimits(),
) {
    /**
     * 開始收集後才逐步執行來源對局；同一 runtime 只供單次完整收集。
     *
     * @return 完整交易進度，以及最後唯一一筆空事件終點。
     * @throws IllegalStateException 當交易過大、來源有缺口、未確認或無法收斂時。
     */
    fun events(): Flow<HeadlessHistoryProgress> = flow {
        withTimeout(limits.maxDuration) {
            val matchId = runtime.matchId()
            check(runtime.currentGame()?.matchId == matchId) { "History runtime must have an opening game" }
            var steps = 0
            while (true) {
                val snapshot = runtime.store.snapshot()
                val recording = snapshot.historyRecordingState
                check(matchId !in recording.firstMissingSequenceByMatchId) { "History source has an event sequence gap" }
                val pending = recording.pendingEvents.filter { it.matchId == matchId }
                if (pending.isNotEmpty()) {
                    val firstTransaction = pending.first().transactionFirstSequence
                    val batch = pending.takeWhile { it.transactionFirstSequence == firstTransaction }
                    check(batch.size <= limits.maxTransferBatchSize) { "A history transaction exceeds the transfer batch limit" }
                    check(batch.first().sequence == firstTransaction) { "History source transaction is incomplete" }
                    withTimeout(limits.transferWaitTimeout) {
                        emit(HeadlessHistoryProgress(snapshot, runtime.currentGame(), matchId, runtime.venueId, false, batch))
                    }
                    val remaining = runtime.store.snapshot().historyRecordingState.pendingEvents
                    val ids = batch.mapTo(mutableSetOf()) { it.matchId to it.sequence }
                    check(remaining.none { (it.matchId to it.sequence) in ids }) {
                        "History collector must acknowledge the entire emitted batch before the next step"
                    }
                    continue
                }
                if (runtime.currentGame() == null) {
                    check(recording.terminalByMatchId[matchId]?.completed == true) { "History source has no completed terminal evidence" }
                    withTimeout(limits.transferWaitTimeout) {
                        emit(HeadlessHistoryProgress(snapshot, null, matchId, runtime.venueId, true, emptyList()))
                    }
                    break
                }
                check(steps < limits.maxSteps) { "Headless history runner exceeded its step limit" }
                check(runtime.step()) { "Headless history runner made no progress before match completion" }
                steps++
            }
        }
    }
}
