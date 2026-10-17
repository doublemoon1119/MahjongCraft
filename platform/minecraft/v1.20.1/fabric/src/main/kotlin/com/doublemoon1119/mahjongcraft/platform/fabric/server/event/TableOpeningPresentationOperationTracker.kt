package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import kotlin.uuid.Uuid

/**
 * 追蹤 Fabric 同桌同一批開局 presentation 的世代與失敗狀態。
 *
 * Flow 的 publisher 契約刻意不攜帶平台 operation token；[begin] 在收到具有開門動畫的牌牆請求時建立
 * 新世代，後續同步發布的方法以 [getRegisteringTicket] 取得同一張 [Ticket]，直到初次發牌完成註冊後由
 * [finishRegistration] 關閉捕捉窗口。已經捕捉的工作仍可執行；新世代會使舊世代失效。
 *
 * 本類別只負責診斷期的同批 fail-stop，不取代 [TablePresentationBusyTracker]。第一次不可恢復例外會由
 * [fail] 標記，讓同批尚未執行的工作停止碰觸可能已損壞的世界 entity index。
 */
class TableOpeningPresentationOperationTracker {
    /** 各桌最新開局世代及其第一筆失敗狀態。 */
    private val latestByTable = mutableMapOf<Uuid, State>()

    /** 仍可捕捉後續同步發布工作的開局批次。 */
    private val openRegistrationByTable = mutableMapOf<Uuid, Ticket>()

    /**
     * 建立新世代並開啟後續 presentation 的捕捉窗口。
     *
     * @param tableId 新開局所屬桌子。
     * @return 新世代的批次識別。
     */
    @Synchronized
    fun begin(tableId: Uuid): Ticket {
        val generation = (latestByTable[tableId]?.ticket?.generation ?: 0L) + 1L
        val ticket = Ticket(tableId, generation)
        latestByTable[tableId] = State(ticket)
        openRegistrationByTable[tableId] = ticket
        return ticket
    }

    /**
     * 取得目前仍在同步註冊中的開局批次；一般回合 presentation 回傳 `null`。
     *
     * @param tableId 要查詢的桌子。
     * @return 仍在捕捉窗口中的批次，或一般回合時的 null。
     */
    @Synchronized
    fun getRegisteringTicket(tableId: Uuid): Ticket? = openRegistrationByTable[tableId]

    /**
     * 關閉捕捉窗口；已排入 coroutine 的 [ticket] 仍保持有效。
     *
     * @param ticket 要完成同步註冊的開局批次。
     */
    @Synchronized
    fun finishRegistration(ticket: Ticket) {
        if (openRegistrationByTable[ticket.tableId] == ticket) {
            openRegistrationByTable.remove(ticket.tableId)
        }
    }

    /**
     * 判斷工作是否仍屬最新且尚未失敗的世代；沒有開局 ticket 的一般 presentation 一律可執行。
     *
     * @param ticket 工作捕捉到的批次；一般 presentation 傳 null。
     * @return 沒有批次，或仍屬最新且未失敗批次時為 true。
     */
    @Synchronized
    fun mayRun(ticket: Ticket?): Boolean {
        if (ticket == null) return true
        val state = latestByTable[ticket.tableId] ?: return false
        return state.ticket == ticket && !state.failed
    }

    /**
     * 將最新世代標記為失敗；舊世代或已失敗世代不重複改寫第一筆失敗資料。
     *
     * @param ticket 失敗工作捕捉到的批次；一般 presentation 可為 null。
     * @param stage 失敗的呈現階段名稱。
     * @param cause 原始失敗原因。
     * @return 包含批次與階段資訊的例外。
     */
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

    /** 使指定桌子的開局呈現失效，保留世代計數，避免重新開局後舊工作再次有效。 */
    @Synchronized
    fun invalidate(tableId: Uuid) {
        latestByTable[tableId]?.failed = true
        openRegistrationByTable.remove(tableId)
    }

    /**
     * 一次開局 presentation 批次的穩定身分。
     *
     * @property tableId 批次所屬桌子。
     * @property generation 此桌遞增的開局世代。
     */
    data class Ticket(
        val tableId: Uuid,
        val generation: Long,
    )

    /**
     * 最新世代的可變失敗狀態。
     *
     * @property ticket 此狀態所屬的世代。
     * @property failed 是否已有不可恢復失敗。
     * @property failedStage 第一個失敗的呈現階段；尚未失敗時為 null。
     */
    private data class State(
        val ticket: Ticket,
        var failed: Boolean = false,
        var failedStage: String? = null,
    )
}
