package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto

/**
 * 回放中的一局：整局的事件頁與牌面暫存，並決定下一次向伺服器要什麼。
 *
 * 要的順序是：還不知道交易總數時先要第一頁事件；目前這一步沒有牌面時先要牌面；接著把整局剩下的事件頁讀完，
 * 讓交易列表與進度條標記完整；最後預先要後面的牌面。同一時間只有一次要求在途。
 *
 * @property roundNumber 局序號。
 * @param initialStep 開始的步數：0 是初始牌面，k 是第 k - 1 筆交易完成後。
 * @param transactionCount 已知的本局交易總數；不知道時為 null，等第一頁事件回來再決定。
 */
internal class HistoryReplayRound(
    val roundNumber: Int,
    initialStep: Int,
    transactionCount: Int?,
) {
    /** 還沒有牌面暫存時記住要去的步數；必須在 [buffer] 之前初始化。 */
    private var requestedStep: Int = initialStep

    /** 牌面暫存；還不知道交易總數時為 null。 */
    private var buffer: HistoryReplayBuffer? = transactionCount?.let(::createBuffer)

    /** 已取得的事件頁，依交易順序排列。 */
    private val pages = mutableListOf<HistoryRoundEventsDto>()

    /** 在途事件頁的第一筆交易索引；沒有在途事件頁時為 null。 */
    private var eventsInFlight: Int? = null

    /** 已取得的事件頁。 */
    val eventPages: List<HistoryRoundEventsDto>
        get() = pages.toList()

    /** 整局的事件頁是否都已取得。 */
    val eventsComplete: Boolean
        get() = pages.lastOrNull()?.let { it.nextTransactionIndex == null } ?: false

    /** 目前步數。 */
    val step: Int
        get() = buffer?.current ?: requestedStep

    /** 這一局的總步數；還不知道時為 null。 */
    val stepCount: Int?
        get() = buffer?.stepCount

    /**
     * 取得某一步的牌面。
     *
     * @param step 步數。
     * @return 牌面；還沒取得時為 null。
     */
    fun stateAt(step: Int): HistoryRoundStateDto? = buffer?.stateAt(step)

    /**
     * 移到某一步。
     *
     * @param step 新的步數；知道總步數後超出範圍時夾到第一步或最後一步。
     */
    fun moveTo(step: Int) {
        requestedStep = step
        buffer?.moveTo(step)
    }

    /**
     * 下一次要向伺服器要的東西；有要求在途或都已取得時為 null。
     *
     * @return 事件頁或一段牌面。
     */
    fun nextRequest(): HistoryReplayFetch? {
        if (eventsInFlight != null) return null
        val buffer = buffer ?: return nextEventsStart()?.let(HistoryReplayFetch::Events)
        if (buffer.stateAt(buffer.current) == null) return buffer.nextRequest()?.let(HistoryReplayFetch::States)
        nextEventsStart()?.let { return HistoryReplayFetch.Events(it) }
        return buffer.nextRequest()?.let(HistoryReplayFetch::States)
    }

    /**
     * 記下已送出的要求，在回覆或失敗之前不再送出新的要求。
     *
     * @param fetch 已送出的要求。
     */
    fun markRequested(fetch: HistoryReplayFetch) {
        when (fetch) {
            is HistoryReplayFetch.Events -> eventsInFlight = fetch.start
            is HistoryReplayFetch.States -> buffer?.markRequested(fetch.request)
        }
    }

    /**
     * 收下一頁事件；第一次知道交易總數時建立牌面暫存。
     *
     * @param start 這一頁的第一筆交易索引。
     * @param events 已驗證的事件頁。
     */
    fun acceptEvents(start: Int, events: HistoryRoundEventsDto) {
        eventsInFlight = null
        if (start != expectedEventsStart()) return
        pages += events
        if (buffer == null) buffer = createBuffer(events.transactionCount)
    }

    /**
     * 收下伺服器回覆的連續牌面。
     *
     * @param start 回覆的第一步。
     * @param states 依步數先後排列的牌面。
     */
    fun acceptStates(start: Int, states: List<HistoryRoundStateDto>) {
        buffer?.accept(start, states)
    }

    /** 在途要求失敗或在送出前取消，之後可以重新要求。 */
    fun releaseRequest() {
        eventsInFlight = null
        buffer?.releaseRequest()
    }

    /**
     * 下一頁事件應該從哪一筆交易開始。
     *
     * @return 交易索引；已讀完整局時為 null。
     */
    private fun expectedEventsStart(): Int? = if (pages.isEmpty()) 0 else pages.last().nextTransactionIndex

    /**
     * 還需要讀的下一頁事件。
     *
     * @return 交易索引；已讀完或已有在途事件頁時為 null。
     */
    private fun nextEventsStart(): Int? = if (eventsInFlight != null) null else expectedEventsStart()

    /**
     * 依交易總數建立牌面暫存，並移到記住的步數。
     *
     * @param transactionCount 本局交易總數。
     * @return 牌面暫存。
     */
    private fun createBuffer(transactionCount: Int): HistoryReplayBuffer = HistoryReplayBuffer(stepCount = transactionCount + 1).also { it.moveTo(requestedStep) }
}

/** 回放向伺服器要的一項資料。 */
internal sealed interface HistoryReplayFetch {
    /**
     * 一頁事件。
     *
     * @property start 這一頁的第一筆交易索引。
     */
    data class Events(val start: Int) : HistoryReplayFetch

    /**
     * 一段連續牌面。
     *
     * @property request 牌面暫存決定的範圍。
     */
    data class States(val request: HistoryReplayRequest) : HistoryReplayFetch
}
