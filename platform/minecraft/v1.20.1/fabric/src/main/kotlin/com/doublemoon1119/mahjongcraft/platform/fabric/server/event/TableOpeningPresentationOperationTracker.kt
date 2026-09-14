package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 追蹤 Fabric 同桌同一批開局 presentation 的世代與失敗狀態。
 *
 * Flow 的 publisher 契約刻意不攜帶平台 operation token；[begin] 在收到具有開門動畫的牌牆請求時建立
 * 新世代，後續同步發布的方法以 [capture] 取得同一張 [Ticket]，直到初次發牌完成註冊後由
 * [finishRegistration] 關閉捕捉窗口。已經捕捉的工作仍可執行；新世代會使舊世代失效。
 *
 * 本類別只負責診斷期的同批 fail-stop，不取代 [TablePresentationBusyTracker]。第一次不可恢復例外會由
 * [fail] 標記，讓同批尚未執行的工作停止碰觸可能已損壞的世界 entity index。
 */
@Single
class TableOpeningPresentationOperationTracker {
    private val latestByTable = mutableMapOf<Uuid, State>()
    private val openRegistrationByTable = mutableMapOf<Uuid, Ticket>()

    /** 建立新世代並開啟後續 presentation 的捕捉窗口。 */
    @Synchronized
    fun begin(tableId: Uuid): Ticket {
        val generation = (latestByTable[tableId]?.ticket?.generation ?: 0L) + 1L
        val ticket = Ticket(tableId, generation)
        latestByTable[tableId] = State(ticket)
        openRegistrationByTable[tableId] = ticket
        return ticket
    }

    /** 取得目前仍在同步註冊中的開局批次；一般回合 presentation 回傳 `null`。 */
    @Synchronized
    fun capture(tableId: Uuid): Ticket? = openRegistrationByTable[tableId]

    /** 關閉捕捉窗口；已排入 coroutine 的 [ticket] 仍保持有效。 */
    @Synchronized
    fun finishRegistration(ticket: Ticket) {
        if (openRegistrationByTable[ticket.tableId] == ticket) {
            openRegistrationByTable.remove(ticket.tableId)
        }
    }

    /** 判斷工作是否仍屬最新且尚未失敗的世代；沒有開局 ticket 的一般 presentation 一律可執行。 */
    @Synchronized
    fun mayRun(ticket: Ticket?): Boolean {
        if (ticket == null) return true
        val state = latestByTable[ticket.tableId] ?: return false
        return state.ticket == ticket && !state.failed
    }

    /** 將最新世代標記為失敗；舊世代或已失敗世代不重複改寫第一筆失敗資料。 */
    @Synchronized
    fun fail(ticket: Ticket?, stage: String, cause: Throwable): OpeningPresentationOperationException {
        if (ticket != null) {
            val state = latestByTable[ticket.tableId]
            if (state?.ticket == ticket && !state.failed) {
                state.failed = true
                state.failedStage = stage
            }
        }
        return OpeningPresentationOperationException(ticket, stage, cause)
    }

    /** 清除 server session 的所有記憶體狀態。 */
    @Synchronized
    fun clearAll() {
        latestByTable.clear()
        openRegistrationByTable.clear()
    }

    /** 一次開局 presentation 批次的穩定身分。 */
    data class Ticket(
        val tableId: Uuid,
        val generation: Long,
    )

    /** 最新世代的可變失敗狀態。 */
    private data class State(
        val ticket: Ticket,
        var failed: Boolean = false,
        var failedStage: String? = null,
    )
}

/** 附加開局批次、世代與 stage 的不可恢復 presentation 例外。 */
class OpeningPresentationOperationException(
    val ticket: TableOpeningPresentationOperationTracker.Ticket?,
    val stage: String,
    cause: Throwable,
) : RuntimeException(
    "Opening presentation failed: tableId=${ticket?.tableId}, generation=${ticket?.generation}, stage=$stage",
    cause,
)
