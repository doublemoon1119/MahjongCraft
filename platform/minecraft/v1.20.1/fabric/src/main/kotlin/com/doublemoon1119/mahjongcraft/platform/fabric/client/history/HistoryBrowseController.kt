package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryResponseDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * 管理一個 client 歷史瀏覽 session 的查詢、分頁與導航，不訂閱資料庫變更。
 *
 * 所有操作與注入的作用域必須使用同一 client 執行緒。切換子頁不關閉此物件，只有最外層關閉或 session 失效才取消。
 * 最多一項在途查詢及一項最新待送意圖；舊回應可以解除在途工作，但不能覆蓋已改變的條件。
 *
 * @property transport 有 requestId 配對及 session 失效通知的查詢傳輸。
 * @param parentScope 提供 client 排程的父作用域；關閉只取消本瀏覽自己的子作用域。
 * @property now 判斷間隔的單調時間來源，不使用可被調整的日曆時鐘。
 * @property beforeSummaryQuery 在摘要傳送前等待同 session 的其他唯讀查詢釋放配額。
 */
internal class HistoryBrowseController(
    private val transport: HistoryQueryTransport,
    parentScope: CoroutineScope,
    private val now: () -> Duration = { System.nanoTime().nanoseconds },
    private val beforeSummaryQuery: suspend () -> Unit = {},
) {
    /** 此瀏覽獨立的協程生命週期，不取消父作用域或其他瀏覽。 */
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    /** 開啟時的連線／世界世代，重新連線不得重用原快取。 */
    private val sessionRevision = transport.sessionRevision.value

    /** 從有效條件及導航意圖產生的世代。 */
    private var generation = 0L

    /** 已成功到達各頁的請求游標；第一頁固定為 null。 */
    private var cursors: List<String?> = listOf(null)

    /** 已成功到達各頁的累積資料偏移；不以固定 page size 推算。 */
    private var entryOffsets: List<Int> = listOf(0)

    /** 是否已要求首次列表；子頁重建不重送。 */
    private var opened = false

    /** 最後一次開始傳送的單調時間。 */
    private var lastSentAt: Duration? = null

    /** 尚未完成的唯一傳輸。 */
    private var active: Active? = null

    /** 快速切換時只保留一項最新意圖。 */
    private var pending: Pending? = null

    /** 最近失敗且仍可重試的同一查詢。 */
    private var retryIntent: Intent? = null

    /** 尚未開始傳送的條件合併／間隔等待。 */
    private var sendJob: Job? = null

    /** 唯一在途要求的等待上限工作。 */
    private var timeoutJob: Job? = null

    /** 內部可寫快照。 */
    private val mutableState = MutableStateFlow(HistoryBrowseState())

    /** Screen 可觀察的唯讀瀏覽狀態。 */
    val state: StateFlow<HistoryBrowseState> = mutableState.asStateFlow()

    /** 目前伺服器公布的有效查詢間隔。 */
    val minimumInterval: Duration
        get() = transport.minimumInterval.value

    init {
        scope.launch { transport.minimumInterval.collect { schedule() } }
        scope.launch {
            combine(transport.state, transport.sessionRevision) { result, revision -> result to revision }
                .collect { (result, revision) ->
                    if (revision != sessionRevision) invalidateSession() else accept(result)
                }
        }
    }

    /** 首次開啟時查詢第一頁；已開啟或已關閉時不重送。 */
    fun open() {
        if (opened || !available()) return
        opened = true
        generation++
        requestFirstPage(Duration.ZERO)
    }

    /**
     * 套用有效查詢條件；快速修改合併為最後一次，並重設分頁。
     *
     * @param query 已通過輸入驗證的查詢條件；ALL 的個人欄位仍在此正規化。
     * @return 是否改變有效條件。
     */
    fun updateQuery(query: HistoryBrowseQuery): Boolean {
        if (!available()) return false
        val normalized = query.normalized()
        if (normalized == state.value.query) return false
        generation++
        mutableState.value = state.value.copy(
            query = normalized,
            page = if (state.value.page == HistoryBrowsePage.SUMMARY) HistoryBrowsePage.LIST else state.value.page,
        )
        opened = true
        requestFirstPage(HistoryBrowseTiming.conditionDebounce)
        return true
    }

    /**
     * 保留有效條件重新查詢第一頁；載入中不重複刷新。
     *
     * @return 是否接受刷新。
     */
    fun refresh(): Boolean {
        if (!canRefresh()) return false
        generation++
        opened = true
        requestFirstPage(Duration.ZERO)
        return true
    }

    /**
     * 判斷目前是否可以由使用者重新整理列表。
     *
     * @return 沒有在途或待送查詢、未處於冷卻期且目前位於列表頁時為 true。
     */
    fun canRefresh(): Boolean = available() &&
        state.value.page == HistoryBrowsePage.LIST &&
        state.value.list.status != HistoryBrowseStatus.Loading &&
        active == null &&
        pending == null &&
        !isRefreshCoolingDown()

    /**
     * 判斷最近一次查詢是否仍在最短請求間隔內。
     *
     * @return 尚未達到下一次查詢時間時為 true。
     */
    fun isRefreshCoolingDown(): Boolean {
        val sentAt = lastSentAt ?: return false
        return now() < sentAt + minimumInterval
    }

    /**
     * 查詢下一頁，只有成功才加入已到達的游標堆疊。
     *
     * @return 是否接受下一頁查詢。
     */
    fun nextPage(): Boolean {
        if (!canNavigateList()) return false
        val cursor = state.value.list.nextCursor ?: return false
        generation++
        val nextOffset = entryOffsets.last() + state.value.list.entries.size
        requestList(cursors + cursor, entryOffsets + nextOffset)
        return true
    }

    /**
     * 重新查詢上一頁，失敗時保留目前頁及原游標堆疊。
     *
     * @return 是否接受上一頁查詢。
     */
    fun previousPage(): Boolean {
        if (!canNavigateList() || cursors.size <= 1) return false
        generation++
        requestList(cursors.dropLast(1), entryOffsets.dropLast(1))
        return true
    }

    /**
     * 開啟篩選子頁，不另送查詢或取消目前有效工作。
     *
     * @return 是否可以進入篩選子頁。
     */
    fun showFilters(): Boolean {
        if (!available() || state.value.page != HistoryBrowsePage.LIST) return false
        mutableState.value = state.value.copy(page = HistoryBrowsePage.FILTERS)
        return true
    }

    /**
     * 開啟最近成功列表中的對局摘要；相同成功摘要可直接使用快取。
     *
     * @param matchId 列表中已公開的對局識別碼。
     * @return 是否接受摘要導航。
     */
    fun showSummary(matchId: String): Boolean {
        if (!available() || state.value.page != HistoryBrowsePage.LIST || state.value.list.status != HistoryBrowseStatus.Ready) return false
        if (state.value.list.entries.none { it.matchId == matchId }) return false
        generation++
        val previous = state.value.summary?.takeIf { it.matchId == matchId && it.status == HistoryBrowseStatus.Ready }
        mutableState.value = state.value.copy(
            page = HistoryBrowsePage.SUMMARY,
            list = state.value.list.copy(selectedMatchId = matchId),
            summary = previous ?: HistoryBrowseSummaryState(matchId, status = HistoryBrowseStatus.Loading),
        )
        if (previous == null) enqueue(Intent.Summary(generation, matchId, state.value.query), Duration.ZERO)
        return true
    }

    /**
     * 返回保留的列表，不重新查詢；尚未完成的摘要不得覆蓋列表。
     *
     * @return 是否接受返回。
     */
    fun backToList(): Boolean {
        if (!available() || state.value.page == HistoryBrowsePage.LIST) return false
        if (state.value.page == HistoryBrowsePage.SUMMARY) {
            generation++
            pending = null
            sendJob?.cancel()
            retryIntent = null
        }
        mutableState.value = state.value.copy(page = HistoryBrowsePage.LIST)
        return true
    }

    /**
     * 保留目前列表的呈現位置，不改變查詢條件。
     *
     * @param scrollOffset 有限且非負的捲動位置。
     * @param selectedMatchId 列表中的選取對局；null 表示清除選取。
     * @return 是否接受位置更新。
     */
    fun rememberListPosition(scrollOffset: Double, selectedMatchId: String? = state.value.list.selectedMatchId): Boolean {
        if (!available() || !scrollOffset.isFinite() || scrollOffset < 0.0) return false
        if (selectedMatchId != null && state.value.list.entries.none { it.matchId == selectedMatchId }) return false
        mutableState.value = state.value.copy(list = state.value.list.copy(scrollOffset = scrollOffset, selectedMatchId = selectedMatchId))
        return true
    }

    /**
     * 重送仍屬於目前條件的失敗要求；不自動重試伺服器忙碌或頻率限制。
     *
     * @return 是否接受重試。
     */
    fun retry(): Boolean {
        if (!available() || active != null || pending != null) return false
        val intent = retryIntent?.takeIf { it.generation == generation } ?: return false
        when (intent) {
            is Intent.ListQuery -> mutableState.value = state.value.copy(list = state.value.list.copy(status = HistoryBrowseStatus.Loading))
            is Intent.Summary -> mutableState.value = state.value.copy(summary = state.value.summary?.copy(status = HistoryBrowseStatus.Loading))
        }
        enqueue(intent, Duration.ZERO)
        return true
    }

    /** 關閉最外層瀏覽並清除快取；不取消父作用域或伺服器 SQLite 工作。 */
    fun close() {
        if (state.value.closed) return
        stop()
        mutableState.value = HistoryBrowseState(closed = true)
    }

    /**
     * 驗證原 session 仍有效，避免切換通知尚未排程時送到新連線。
     *
     * @return 此瀏覽是否仍可操作。
     */
    private fun available(): Boolean {
        if (transport.sessionRevision.value != sessionRevision) invalidateSession()
        return !state.value.closed
    }

    /**
     * 判斷目前列表是否可進行分頁。
     *
     * @return 是否沒有尚未完成的列表操作。
     */
    private fun canNavigateList(): Boolean = available() &&
        state.value.page == HistoryBrowsePage.LIST &&
        state.value.list.status != HistoryBrowseStatus.Loading &&
        state.value.list.status != HistoryBrowseStatus.Idle

    /**
     * 清除舊條件的列表及摘要並準備第一頁。
     *
     * @param debounce 條件合併等待；刷新為零。
     */
    private fun requestFirstPage(debounce: Duration) {
        cursors = listOf(null)
        entryOffsets = listOf(0)
        mutableState.value = state.value.copy(list = HistoryBrowseListState(status = HistoryBrowseStatus.Loading), summary = null)
        enqueue(Intent.ListQuery(generation, state.value.query, cursors, entryOffsets), debounce)
    }

    /**
     * 準備指定分頁，成功前不修改已到達堆疊。
     *
     * @param requestedCursors 欲成功到達的游標堆疊。
     * @param requestedEntryOffsets 依實際回應筆數累積的各頁偏移。
     */
    private fun requestList(requestedCursors: List<String?>, requestedEntryOffsets: List<Int>) {
        mutableState.value = state.value.copy(list = state.value.list.copy(status = HistoryBrowseStatus.Loading), summary = null)
        enqueue(Intent.ListQuery(generation, state.value.query, requestedCursors, requestedEntryOffsets), Duration.ZERO)
    }

    /**
     * 以新意圖取代尚未送出的舊意圖。
     *
     * @param intent 欲查詢的內容及條件世代。
     * @param debounce 送出之前的條件合併時間。
     */
    private fun enqueue(intent: Intent, debounce: Duration) {
        retryIntent = null
        pending = Pending(intent, now() + debounce)
        schedule()
    }

    /** 等在途工作完成，並同時遵守條件合併與最短請求間隔。 */
    private fun schedule() {
        sendJob?.cancel()
        if (active != null || state.value.closed) return
        val next = pending ?: return
        val readyAt = maxOf(next.readyAt, lastSentAt?.plus(minimumInterval) ?: next.readyAt)
        sendJob = scope.launch {
            delay((readyAt - now()).coerceAtLeast(Duration.ZERO))
            if (state.value.closed || active != null || pending !== next) return@launch
            pending = null
            dispatch(next.intent)
        }
    }

    /**
     * 送出唯一查詢並啟動有界等待；傳輸的同步失敗也會即時處理。
     *
     * @param intent 仍有效的最新待送意圖。
     */
    private suspend fun dispatch(intent: Intent) {
        if (intent is Intent.Summary) beforeSummaryQuery()
        if (!available() || intent.generation != generation) return
        lastSentAt = now()
        val requestId = when (intent) {
            is Intent.ListQuery -> transport.queryList(intent.query.toRequest(intent.cursors.last()))
            is Intent.Summary -> transport.querySummary(HistorySummaryRequestDto("", intent.matchId, intent.query.scope))
        }
        active = Active(requestId, intent)
        timeoutJob = scope.launch {
            delay(HistoryBrowseTiming.responseTimeout)
            if (active?.requestId == requestId) {
                transport.cancel(requestId)
                fail(HistoryBrowseFailure.CLIENT_TIMEOUT)
                finish()
            }
        }
        accept(transport.state.value)
    }

    /**
     * 接收仍對應唯一在途工作的結果；舊條件只解除工作，不更新內容。
     *
     * @param result 傳輸配對後的狀態。
     */
    private fun accept(result: ClientHistoryQueryState) {
        if (state.value.closed) return
        val request = active ?: return
        when (result) {
            is ClientHistoryQueryState.ListResult -> {
                if (result.response.requestId != request.requestId || request.intent !is Intent.ListQuery) return
                if (request.intent.generation == generation) acceptList(result.response, request.intent)
            }
            is ClientHistoryQueryState.SummaryResult -> {
                if (result.response.requestId != request.requestId || request.intent !is Intent.Summary) return
                if (request.intent.generation == generation) acceptSummary(result.response, request.intent)
            }
            is ClientHistoryQueryState.SendFailed -> {
                if (result.requestId != request.requestId) return
                fail(HistoryBrowseFailure.SEND_FAILED)
            }
            else -> return
        }
        finish()
    }

    /**
     * 成功時才提交頁次及堆疊；空集合是有效結果。
     *
     * @param response 已配對的列表回應。
     * @param intent 此次分頁目標。
     */
    private fun acceptList(response: HistoryListResponseDto, intent: Intent.ListQuery) {
        mutableState.value = state.value.copy(allowAll = response.allowAll)
        if (!response.allowAll && state.value.query.scope == HistoryQueryScopeDto.ALL) {
            updateQuery(state.value.query.copy(scope = HistoryQueryScopeDto.OWN))
            return
        }
        response.errorCode?.let {
            fail(it.toBrowseFailure())
            return
        }
        val changedPage = cursors != intent.cursors
        cursors = intent.cursors
        entryOffsets = intent.entryOffsets
        mutableState.value = state.value.copy(
            list = state.value.list.copy(
                entries = response.entries.toList(),
                pageNumber = cursors.size,
                firstEntryIndex = if (response.entries.isEmpty()) 0 else intent.entryOffsets.last() + 1,
                nextCursor = response.nextCursor,
                status = HistoryBrowseStatus.Ready,
                scrollOffset = if (changedPage) 0.0 else state.value.list.scrollOffset,
                selectedMatchId = state.value.list.selectedMatchId?.takeIf { id -> response.entries.any { it.matchId == id } },
            ),
        )
    }

    /**
     * 接受同場摘要；無內容的成功封套視為無法取得，不偽造摘要。
     *
     * @param response 已配對的摘要回應。
     * @param intent 本次已選對局。
     */
    private fun acceptSummary(response: HistorySummaryResponseDto, intent: Intent.Summary) {
        response.errorCode?.let {
            fail(it.toBrowseFailure())
            return
        }
        val detail = response.detail
        if (detail == null || detail.summary.matchId != intent.matchId) {
            fail(HistoryBrowseFailure.NOT_AVAILABLE)
            return
        }
        mutableState.value = state.value.copy(summary = HistoryBrowseSummaryState(intent.matchId, detail, HistoryBrowseStatus.Ready))
    }

    /**
     * 只為目前世代保存失敗與可重試意圖。
     *
     * @param reason 公開失敗種類。
     */
    private fun fail(reason: HistoryBrowseFailure) {
        val intent = active?.intent?.takeIf { it.generation == generation } ?: return
        retryIntent = intent
        val status = HistoryBrowseStatus.Failed(reason)
        mutableState.value = when (intent) {
            is Intent.ListQuery -> state.value.copy(list = state.value.list.copy(status = status))
            is Intent.Summary -> state.value.copy(summary = state.value.summary?.copy(status = status))
        }
    }

    /** 解除唯一工作；不是失敗重試，只送出使用者另外提出的最新意圖。 */
    private fun finish() {
        timeoutJob?.cancel()
        timeoutJob = null
        active = null
        schedule()
    }

    /** 原世界／連線已失效時清除全部資料，並保留可辨識的斷線結果。 */
    private fun invalidateSession() {
        if (state.value.closed) return
        stop()
        mutableState.value = HistoryBrowseState(
            list = HistoryBrowseListState(status = HistoryBrowseStatus.Failed(HistoryBrowseFailure.DISCONNECTED)),
            closed = true,
        )
    }

    /** 停止此瀏覽的配對與子工作，不影響其他 session 的要求。 */
    private fun stop() {
        active?.let { transport.cancel(it.requestId) }
        active = null
        pending = null
        retryIntent = null
        cursors = listOf(null)
        entryOffsets = listOf(0)
        scope.cancel()
    }

    /** 查詢意圖的不可變條件與世代。 */
    private sealed interface Intent {
        /** 只有同世代回應能更新呈現。 */
        val generation: Long

        /**
         * 列表的查詢及欲提交游標堆疊。
         *
         * @property generation 提出意圖時的條件世代。
         * @property query 有效且正規化的查詢條件。
         * @property cursors 只有成功回應才提交的分頁目標。
         * @property entryOffsets 各頁第一筆資料前的累積數量，依實際回應筆數計算。
         */
        data class ListQuery(
            override val generation: Long,
            val query: HistoryBrowseQuery,
            val cursors: List<String?>,
            val entryOffsets: List<Int>,
        ) : Intent

        /**
         * 同場摘要查詢。
         *
         * @property generation 提出意圖時的導航世代。
         * @property matchId 欲查閱的對局。
         * @property query 當時的有效查閱範圍。
         */
        data class Summary(override val generation: Long, val matchId: String, val query: HistoryBrowseQuery) : Intent
    }

    /**
     * 最新待送的唯一意圖。
     *
     * @property intent 查詢內容。
     * @property readyAt 條件合併後最早可送出的單調時間。
     */
    private data class Pending(val intent: Intent, val readyAt: Duration)

    /**
     * 唯一在途工作。
     *
     * @property requestId 傳輸實際分配的配對鍵。
     * @property intent 該要求對應的條件及分頁目標。
     */
    private data class Active(val requestId: String, val intent: Intent)
}
