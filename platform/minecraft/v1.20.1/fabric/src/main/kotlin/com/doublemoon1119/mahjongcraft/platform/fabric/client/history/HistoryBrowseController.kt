package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateResponseDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsResponseDto
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
import kotlinx.serialization.json.Json
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
 * @property beforeDetailQuery 在詳細資料傳送前等待同 session 的其他唯讀查詢釋放配額。
 */
internal class HistoryBrowseController(
    private val transport: HistoryQueryTransport,
    parentScope: CoroutineScope,
    private val now: () -> Duration = { System.nanoTime().nanoseconds },
    private val beforeDetailQuery: suspend () -> Unit = {},
) {
    /** 此瀏覽獨立的協程生命週期，不取消父作用域或其他瀏覽。 */
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    /** 開啟時的連線／世界世代，重新連線不得重用原快取。 */
    private val sessionRevision = transport.sessionRevision.value

    /** 僅保存本 session 驗證成功的有界單局資料。 */
    private val roundCache = HistoryRoundCache(now = now)

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
            page = if (state.value.page != HistoryBrowsePage.FILTERS) HistoryBrowsePage.LIST else state.value.page,
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

    /** 判斷目前是否有可重送的最新失敗查詢。
     * @return 目前查詢失敗、無其他工作且未進入冷卻期時為 true。
     */
    fun canRetry(): Boolean = available() &&
        active == null &&
        pending == null &&
        retryIntent?.takeIf { it.generation == generation } != null &&
        !isRefreshCoolingDown()

    /**
     * 判斷單局頁查詢控制項是否可接受新的網路操作。
     *
     * 純快取導航仍由既有意圖排程處理；呈現端在在途、待送或冷卻期間停用查詢按鈕。
     *
     * @return 目前位於單局頁、沒有其他查詢且未處於冷卻期時為 true。
     */
    fun canQueryRound(): Boolean = available() &&
        state.value.page in setOf(HistoryBrowsePage.ROUND_EVENTS, HistoryBrowsePage.ROUND_STATE) &&
        active == null &&
        pending == null &&
        !isRefreshCoolingDown()

    /**
     * 確保牌面狀態頁已排程取得同一對局的規則設定。
     *
     * 牌面狀態頁不因背景補查而離開目前頁面；已載入、載入中、失敗或屬於其他對局的結果
     * 都不會被自動覆寫，也不會因失敗而自動重試。
     *
     * @return 是否已接受新的規則設定查詢。
     */
    fun ensureRoundStateRuleSettings(): Boolean {
        val round = currentRound() ?: return false
        if (!available() || state.value.page != HistoryBrowsePage.ROUND_STATE || round.stateStatus != HistoryBrowseStatus.Ready || !canQueryRound()) return false
        val matchId = state.value.summary?.matchId ?: return false
        val existing = state.value.ruleSettings
        if (existing != null) return false
        mutableState.value = state.value.copy(
            ruleSettings = HistoryBrowseRuleSettingsState(matchId, status = HistoryBrowseStatus.Loading),
        )
        enqueue(Intent.RuleSettings(generation, matchId, state.value.query), Duration.ZERO)
        return true
    }

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
            ruleSettings = state.value.ruleSettings?.takeIf { it.matchId == matchId },
            rounds = if (state.value.summary?.matchId == matchId) state.value.rounds else emptyMap(),
            selectedRoundNumber = if (state.value.summary?.matchId == matchId) state.value.selectedRoundNumber else null,
        )
        if (previous == null) enqueue(Intent.Summary(generation, matchId, state.value.query), Duration.ZERO)
        return true
    }

    /** 開啟目前摘要對應的歷史規則設定；成功結果會保留於本瀏覽 session。
     * @return 是否接受規則設定導航。
     */
    fun showRuleSettings(): Boolean {
        if (!available() || state.value.page != HistoryBrowsePage.SUMMARY) return false
        val summary = state.value.summary ?: return false
        if (summary.status != HistoryBrowseStatus.Ready) return false
        val matchId = summary.matchId
        generation++
        val cached = state.value.ruleSettings?.takeIf { it.matchId == matchId && it.status == HistoryBrowseStatus.Ready }
        mutableState.value = state.value.copy(
            page = HistoryBrowsePage.RULE_SETTINGS,
            ruleSettings = cached ?: HistoryBrowseRuleSettingsState(matchId, status = HistoryBrowseStatus.Loading),
        )
        if (cached == null) enqueue(Intent.RuleSettings(generation, matchId, state.value.query), Duration.ZERO)
        return true
    }

    /** 保留摘要頁的呈現位置，不改變查詢條件或摘要資料。
     *
     * @param scrollOffset 有限且非負的捲動位置。
     * @return 是否接受位置更新。
     */
    fun rememberSummaryPosition(scrollOffset: Double): Boolean {
        val summary = state.value.summary ?: return false
        if (!available() ||
            state.value.page != HistoryBrowsePage.SUMMARY ||
            !scrollOffset.isFinite() ||
            scrollOffset < 0.0
        ) {
            return false
        }
        mutableState.value = state.value.copy(summary = summary.copy(scrollOffset = scrollOffset))
        return true
    }

    /** 開啟摘要已列出的單局事件，保留各局的分頁與位置。
     * @param roundNumber 已公開的局序號。
     * @return 是否接受選局。
     */
    fun showRound(roundNumber: Int): Boolean {
        if (!available() || state.value.page !in setOf(HistoryBrowsePage.SUMMARY, HistoryBrowsePage.ROUND_EVENTS, HistoryBrowsePage.ROUND_STATE)) return false
        val summary = state.value.summary ?: return false
        if (summary.status != HistoryBrowseStatus.Ready || summary.detail?.rounds?.none { it.roundNumber == roundNumber } != false) return false
        abandonPending()
        val retained = state.value.rounds.mapValues { (_, round) -> round.copy(events = null, state = null, eventsStatus = HistoryBrowseStatus.Idle, stateStatus = HistoryBrowseStatus.Idle) }
        val selected = retained[roundNumber] ?: HistoryBrowseRoundState(roundNumber)
        mutableState.value = state.value.copy(page = HistoryBrowsePage.ROUND_EVENTS, rounds = retained + (roundNumber to selected), selectedRoundNumber = roundNumber)
        requestRoundEvents(selected.eventStartIndices)
        return true
    }

    /** 查詢下一個事件頁，只有成功才提交分頁位置。
     * @return 是否存在已確認的下一頁。
     */
    fun nextRoundPage(): Boolean {
        val round = currentRound() ?: return false
        if (state.value.page != HistoryBrowsePage.ROUND_EVENTS || round.eventsStatus != HistoryBrowseStatus.Ready) return false
        val next = round.events?.nextTransactionIndex ?: return false
        abandonPending()
        requestRoundEvents(round.eventStartIndices + next)
        return true
    }

    /** 返回已到達的上一個事件頁。
     * @return 是否接受上一頁查詢。
     */
    fun previousRoundPage(): Boolean {
        val round = currentRound() ?: return false
        if (state.value.page != HistoryBrowsePage.ROUND_EVENTS || round.eventStartIndices.size <= 1) return false
        abandonPending()
        requestRoundEvents(round.eventStartIndices.dropLast(1))
        return true
    }

    /** 查閱初始牌面或已確認存在的交易後牌面。
     * @param position 欲查閱的局內位置。
     * @return 是否接受牌面選取。
     */
    fun showRoundState(position: HistoryRoundPositionDto): Boolean {
        val round = currentRound() ?: return false
        if (state.value.page !in setOf(HistoryBrowsePage.ROUND_EVENTS, HistoryBrowsePage.ROUND_STATE)) return false
        if (position is HistoryRoundPositionDto.AfterTransaction && position.index !in round.knownTransactionIndices) return false
        abandonPending()
        mutableState.value = state.value.copy(page = HistoryBrowsePage.ROUND_STATE)
        requestRoundState(position)
        return true
    }

    /** 前往已知的下一筆；未讀頁由同一意圖先確認交易再查牌面。
     * @return 是否接受前進。
     */
    fun nextRoundState(): Boolean = navigateRoundState(1)

    /** 查詢事件明細的完整牌組而不離開事件頁，沿用相同在途限制與快取。
     * @param transactionIndex 已確認存在的交易索引。
     * @return 是否接受查詢。
     */
    fun loadRoundStateForDetails(transactionIndex: Int): Boolean {
        val round = currentRound() ?: return false
        if (state.value.page != HistoryBrowsePage.ROUND_EVENTS || !canQueryRound() || transactionIndex !in round.knownTransactionIndices) return false
        abandonPending()
        requestRoundState(HistoryRoundPositionDto.AfterTransaction(transactionIndex))
        return true
    }

    /** 前往上一筆，第一筆交易之前為初始牌面。
     * @return 是否接受後退。
     */
    fun previousRoundState(): Boolean = navigateRoundState(-1)

    /** 返回目前局的事件列表，保留事件頁與捲動位置。
     * @return 是否接受返回。
     */
    fun backToRound(): Boolean {
        if (!available() || state.value.page != HistoryBrowsePage.ROUND_STATE) return false
        val round = currentRound() ?: return false
        abandonPending()
        mutableState.value = state.value.copy(page = HistoryBrowsePage.ROUND_EVENTS)
        if (round.events == null) requestRoundEvents(round.eventStartIndices)
        return true
    }

    /** 保存目前事件或牌面子頁的捲動位置。
     * @param scrollOffset 有限且非負的位置。
     * @return 是否接受更新。
     */
    fun rememberRoundPosition(scrollOffset: Double): Boolean {
        val round = currentRound() ?: return false
        if (!scrollOffset.isFinite() || scrollOffset < 0.0) return false
        when (state.value.page) {
            HistoryBrowsePage.ROUND_EVENTS -> updateRound(round.roundNumber) { it.copy(eventScrollOffset = scrollOffset) }
            HistoryBrowsePage.ROUND_STATE -> updateRound(round.roundNumber) { it.copy(stateScrollOffset = scrollOffset) }
            else -> return false
        }
        return true
    }

    /** 重新授權查詢目前單局位置，清除該場成功快取。
     * @return 是否沒有其他查詢或冷卻且已接受重新查詢。
     */
    fun refreshRound(): Boolean {
        val round = currentRound() ?: return false
        if (!canQueryRound()) return false
        abandonPending()
        state.value.summary?.matchId?.let(roundCache::evictMatch)
        if (state.value.page == HistoryBrowsePage.ROUND_EVENTS) requestRoundEvents(round.eventStartIndices) else requestRoundState(round.requestedPosition)
        return true
    }

    /** 取得仍屬於有效 session 的選局資料。
     * @return 選局資料或 null。
     */
    private fun currentRound(): HistoryBrowseRoundState? = if (available()) state.value.selectedRoundNumber?.let(state.value.rounds::get) else null

    /** 使待送及重試意圖失效，但讓伺服器已開始的工作正常釋放額度。 */
    private fun abandonPending() {
        generation++
        pending = null
        retryIntent = null
        sendJob?.cancel()
    }

    /** 以已確認牌面為基準進行相鄰交易導航。
     * @param direction 向前為 1，向後為 -1。
     * @return 是否接受導航。
     */
    private fun navigateRoundState(direction: Int): Boolean {
        val round = currentRound() ?: return false
        if (state.value.page != HistoryBrowsePage.ROUND_STATE || round.stateStatus != HistoryBrowseStatus.Ready) return false
        val position = round.confirmedPosition ?: return false
        if (direction > 0 && round.events?.let { it.transactions.isEmpty() && it.nextTransactionIndex == null } == true) return false
        val index = when (position) {
            HistoryRoundPositionDto.Initial -> if (direction < 0) return false else 0
            is HistoryRoundPositionDto.AfterTransaction -> {
                if (direction > 0 && position.index == Int.MAX_VALUE) return false
                position.index + direction
            }
        }
        val knownTarget = HistoryRoundNavigation.nextPosition(round, position, direction)
        if (knownTarget != null) return showRoundState(knownTarget)
        if (index < 0) return false
        if (round.lastTransactionIndex?.let { index > it } == true) return false
        val target = HistoryRoundPositionDto.AfterTransaction(index)
        abandonPending()
        updateRound(round.roundNumber) { it.copy(requestedPosition = target, stateStatus = HistoryBrowseStatus.Loading) }
        requestRoundEvents(listOf(index), target)
        return true
    }

    /** 排程事件頁或相鄰牌面的交易存在性確認。
     * @param starts 欲提交的事件頁堆疊。
     * @param afterEvents 確認交易後的牌面需求；不改事件列表位置。
     */
    private fun requestRoundEvents(starts: List<Int>, afterEvents: HistoryRoundPositionDto? = null) {
        val round = currentRound() ?: return
        val matchId = state.value.summary?.matchId ?: return
        if (afterEvents == null) updateRound(round.roundNumber) { it.copy(eventsStatus = HistoryBrowseStatus.Loading) }
        enqueue(Intent.RoundEvents(generation, matchId, state.value.query, round.roundNumber, starts, afterEvents), Duration.ZERO)
    }

    /** 排程完整牌面，不把舊資料標記為新位置。
     * @param position 欲確認的位置。
     */
    private fun requestRoundState(position: HistoryRoundPositionDto) {
        val round = currentRound() ?: return
        val matchId = state.value.summary?.matchId ?: return
        updateRound(round.roundNumber) { it.copy(requestedPosition = position, stateStatus = HistoryBrowseStatus.Loading) }
        enqueue(Intent.RoundState(generation, matchId, state.value.query, round.roundNumber, position), Duration.ZERO)
    }

    /** 更新指定局的唯讀導航資料。
     * @param roundNumber 局序號。
     * @param transform 不變資料轉換。
     */
    private fun updateRound(roundNumber: Int, transform: (HistoryBrowseRoundState) -> HistoryBrowseRoundState) {
        mutableState.value = state.value.withRound(roundNumber, transform)
    }

    /** 產生指定局更新後的瀏覽快照。
     * @param roundNumber 局序號。
     * @param transform 不變資料轉換。
     * @return 更新後快照；不存在的局不建立。
     */
    private fun HistoryBrowseState.withRound(roundNumber: Int, transform: (HistoryBrowseRoundState) -> HistoryBrowseRoundState): HistoryBrowseState {
        val round = rounds[roundNumber] ?: return this
        return copy(rounds = rounds + (roundNumber to transform(round)))
    }

    /**
     * 返回保留的列表，不重新查詢；尚未完成的摘要不得覆蓋列表。
     *
     * @return 是否接受返回。
     */
    fun backToList(): Boolean {
        if (!available() || state.value.page == HistoryBrowsePage.LIST) return false
        if (state.value.page != HistoryBrowsePage.FILTERS) {
            generation++
            pending = null
            sendJob?.cancel()
            retryIntent = null
        }
        mutableState.value = state.value.copy(page = HistoryBrowsePage.LIST)
        return true
    }

    /** 返回目前對局摘要，不重新查詢。
     * @return 是否接受返回。
     */
    fun backToSummary(): Boolean {
        if (!available() || state.value.page !in setOf(HistoryBrowsePage.RULE_SETTINGS, HistoryBrowsePage.ROUND_EVENTS, HistoryBrowsePage.ROUND_STATE) || state.value.summary == null) return false
        generation++
        pending = null
        sendJob?.cancel()
        retryIntent = null
        mutableState.value = state.value.copy(
            page = HistoryBrowsePage.SUMMARY,
            ruleSettings = state.value.ruleSettings?.takeIf { it.status == HistoryBrowseStatus.Ready },
        )
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
     * 接受仍屬於目前條件的手動重試；冷卻期間沿用有界排程，不提前傳送。
     * 不自動重試伺服器忙碌或頻率限制，呈現端以 [canRetry] 控制立即重試的可用狀態。
     *
     * @return 是否接受重試。
     */
    fun retry(): Boolean {
        if (!available() || active != null || pending != null) return false
        val intent = retryIntent?.takeIf { it.generation == generation } ?: return false
        when (intent) {
            is Intent.ListQuery -> mutableState.value = state.value.copy(list = state.value.list.copy(status = HistoryBrowseStatus.Loading))
            is Intent.Summary -> mutableState.value = state.value.copy(summary = state.value.summary?.copy(status = HistoryBrowseStatus.Loading))
            is Intent.RuleSettings -> mutableState.value = state.value.copy(ruleSettings = state.value.ruleSettings?.copy(status = HistoryBrowseStatus.Loading))
            is Intent.RoundEvents -> updateRound(intent.roundNumber) {
                if (intent.afterEvents == null) it.copy(eventsStatus = HistoryBrowseStatus.Loading) else it.copy(stateStatus = HistoryBrowseStatus.Loading)
            }
            is Intent.RoundState -> updateRound(intent.roundNumber) { it.copy(stateStatus = HistoryBrowseStatus.Loading) }
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
        roundCache.clear()
        mutableState.value = state.value.copy(list = HistoryBrowseListState(status = HistoryBrowseStatus.Loading), summary = null, ruleSettings = null, rounds = emptyMap(), selectedRoundNumber = null)
        enqueue(Intent.ListQuery(generation, state.value.query, cursors, entryOffsets), debounce)
    }

    /**
     * 準備指定分頁，成功前不修改已到達堆疊。
     *
     * @param requestedCursors 欲成功到達的游標堆疊。
     * @param requestedEntryOffsets 依實際回應筆數累積的各頁偏移。
     */
    private fun requestList(requestedCursors: List<String?>, requestedEntryOffsets: List<Int>) {
        mutableState.value = state.value.copy(list = state.value.list.copy(status = HistoryBrowseStatus.Loading), summary = null, ruleSettings = null, rounds = emptyMap(), selectedRoundNumber = null)
        enqueue(Intent.ListQuery(generation, state.value.query, requestedCursors, requestedEntryOffsets), Duration.ZERO)
    }

    /**
     * 以新意圖取代尚未送出的舊意圖。
     *
     * @param intent 欲查詢的內容及條件世代。
     * @param debounce 送出之前的條件合併時間。
     */
    private fun enqueue(intent: Intent, debounce: Duration) {
        if (!available() || intent.generation != generation) return
        retryIntent = null
        pending = null
        sendJob?.cancel()
        if (acceptCachedRound(intent)) return
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
        if (!available() || intent.generation != generation) return
        if (acceptCachedRound(intent)) return
        if (intent !is Intent.ListQuery) beforeDetailQuery()
        if (!available() || intent.generation != generation) return
        lastSentAt = now()
        val requestId = when (intent) {
            is Intent.ListQuery -> transport.queryList(intent.query.toRequest(intent.cursors.last()))
            is Intent.Summary -> transport.querySummary(HistorySummaryRequestDto("", intent.matchId, intent.query.scope))
            is Intent.RuleSettings -> transport.queryRuleSettings(HistoryRuleSettingsRequestDto("", intent.matchId, intent.query.scope))
            is Intent.RoundEvents -> transport.queryRoundEvents(intent.request())
            is Intent.RoundState -> transport.queryRoundState(intent.request())
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
            is ClientHistoryQueryState.RuleSettingsResult -> {
                if (result.response.requestId != request.requestId || request.intent !is Intent.RuleSettings) return
                if (request.intent.generation == generation) acceptRuleSettings(result.response, request.intent)
            }
            is ClientHistoryQueryState.RoundEventsResult -> {
                if (result.response.requestId != request.requestId || request.intent !is Intent.RoundEvents) return
                if (request.intent.generation == generation) acceptRoundEvents(result.response, request.intent)
            }
            is ClientHistoryQueryState.RoundStateResult -> {
                if (result.response.requestId != request.requestId || request.intent !is Intent.RoundState) return
                if (request.intent.generation == generation) acceptRoundState(result.response, request.intent)
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

    /** 接受同場規則設定；缺少設定內容視為無法取得。
     * @param response 已配對的規則設定回應。
     * @param intent 本次對局規則設定查詢。
     */
    private fun acceptRuleSettings(response: HistoryRuleSettingsResponseDto, intent: Intent.RuleSettings) {
        response.errorCode?.let {
            fail(it.toBrowseFailure())
            return
        }
        val config = response.config
        if (config == null) {
            fail(HistoryBrowseFailure.NOT_AVAILABLE)
            return
        }
        mutableState.value = state.value.copy(
            ruleSettings = HistoryBrowseRuleSettingsState(intent.matchId, config, HistoryBrowseStatus.Ready),
        )
    }

    /** 驗證事件頁，僅保存目前世代的成功內容。
     * @param response 已配對的事件頁回覆。
     * @param intent 原始事件頁意圖。
     */
    private fun acceptRoundEvents(response: HistoryRoundEventsResponseDto, intent: Intent.RoundEvents) {
        when (val result = HistoryRoundResponseValidator.validateEvents(intent.request(response.requestId), response)) {
            is HistoryRoundValidationResult.Success -> {
                roundCache.putEvents(intent.cacheKey(), result.value, Json.encodeToString(result.value).toByteArray(Charsets.UTF_8).size)
                applyRoundEvents(result.value, intent)
            }
            is HistoryRoundValidationResult.Error -> fail(result.code.toBrowseFailure())
            is HistoryRoundValidationResult.Invalid -> fail(HistoryBrowseFailure.NOT_AVAILABLE)
        }
    }

    /** 驗證完整牌面，保留資料自己的確認位置。
     * @param response 已配對的牌面回覆。
     * @param intent 原始牌面意圖。
     */
    private fun acceptRoundState(response: HistoryRoundStateResponseDto, intent: Intent.RoundState) {
        when (val result = HistoryRoundResponseValidator.validateState(intent.request(response.requestId), response)) {
            is HistoryRoundValidationResult.Success -> {
                roundCache.putState(intent.cacheKey(), result.value, Json.encodeToString(result.value).toByteArray(Charsets.UTF_8).size)
                applyRoundState(result.value, intent)
            }
            is HistoryRoundValidationResult.Error -> fail(result.code.toBrowseFailure())
            is HistoryRoundValidationResult.Invalid -> fail(HistoryBrowseFailure.NOT_AVAILABLE)
        }
    }

    /** 套用成功事件頁；牌面查找不覆蓋事件列表位置。
     * @param events 已驗證的事件頁。
     * @param intent 原始頁面或交易存在性查詢。
     */
    private fun applyRoundEvents(events: HistoryRoundEventsDto, intent: Intent.RoundEvents) {
        updateRound(intent.roundNumber) { round ->
            val recorded = HistoryRoundNavigation.recordEvents(round, events)
            val last = if (events.nextTransactionIndex == null) {
                events.transactions.lastOrNull()?.index ?: (intent.starts.last() - 1).takeIf { it in round.knownTransactionIndices } ?: round.lastTransactionIndex
            } else {
                round.lastTransactionIndex
            }
            val updated = round.copy(knownTransactionIndices = recorded.knownTransactionIndices, lastTransactionIndex = last)
            if (intent.afterEvents != null) {
                updated
            } else {
                updated.copy(
                    events = events,
                    nextTransactionIndex = events.nextTransactionIndex,
                    eventStartIndices = intent.starts,
                    eventPageNumber = intent.starts.size,
                    eventsStatus = HistoryBrowseStatus.Ready,
                    eventScrollOffset = if (round.eventStartIndices == intent.starts) round.eventScrollOffset else 0.0,
                )
            }
        }
        val target = intent.afterEvents ?: return
        if (target is HistoryRoundPositionDto.AfterTransaction && events.transactions.none { it.index == target.index }) {
            updateRound(intent.roundNumber) { it.copy(requestedPosition = it.confirmedPosition ?: HistoryRoundPositionDto.Initial, stateStatus = if (it.state == null) HistoryBrowseStatus.Idle else HistoryBrowseStatus.Ready) }
            return
        }
        requestRoundState(target)
    }

    /** 套用同局牌面，確認位置與內容同步更新。
     * @param data 已驗證的完整牌面。
     * @param intent 原始位置查詢。
     */
    private fun applyRoundState(data: HistoryRoundStateDto, intent: Intent.RoundState) {
        updateRound(intent.roundNumber) { round ->
            round.copy(
                state = data,
                confirmedPosition = data.position,
                requestedPosition = data.position,
                stateStatus = HistoryBrowseStatus.Ready,
                stateScrollOffset = if (round.confirmedPosition == data.position) round.stateScrollOffset else 0.0,
            )
        }
    }

    /** 使用仍有效的成功快取，不送出請求或消耗新的冷卻。
     * @param intent 欲取得的單局資料。
     * @return 是否已由快取滿足需求。
     */
    private fun acceptCachedRound(intent: Intent): Boolean = when (intent) {
        is Intent.RoundEvents -> roundCache.getEvents(intent.cacheKey())?.let {
            applyRoundEvents(it, intent)
            true
        } ?: false
        is Intent.RoundState -> roundCache.getState(intent.cacheKey())?.let {
            applyRoundState(it, intent)
            true
        } ?: false
        else -> false
    }

    /** 取得包含 session 與權限範圍的事件快取鍵。
     * @return 唯一事件頁條件。
     */
    private fun Intent.RoundEvents.cacheKey(): HistoryRoundCacheKey.Events = HistoryRoundCacheKey.Events(
        sessionRevision,
        matchId,
        roundNumber,
        query.scope,
        starts.last(),
        request().limit,
    )

    /** 取得包含 session 與權限範圍的牌面快取鍵。
     * @return 唯一局內位置條件。
     */
    private fun Intent.RoundState.cacheKey(): HistoryRoundCacheKey.State = HistoryRoundCacheKey.State(
        sessionRevision,
        matchId,
        roundNumber,
        query.scope,
        position,
    )

    /**
     * 只為目前世代保存失敗與可重試意圖。
     *
     * @param reason 公開失敗種類。
     */
    private fun fail(reason: HistoryBrowseFailure) {
        val intent = active?.intent?.takeIf { it.generation == generation } ?: return
        retryIntent = intent
        val status = HistoryBrowseStatus.Failed(reason)
        when (reason) {
            HistoryBrowseFailure.ACCESS_DENIED, HistoryBrowseFailure.QUERY_DISABLED -> roundCache.clear()
            HistoryBrowseFailure.NOT_AVAILABLE -> state.value.summary?.matchId?.let(roundCache::evictMatch)
            else -> Unit
        }
        mutableState.value = when (intent) {
            is Intent.ListQuery -> state.value.copy(list = state.value.list.copy(status = status))
            is Intent.Summary -> state.value.copy(summary = state.value.summary?.copy(status = status))
            is Intent.RuleSettings -> state.value.copy(ruleSettings = state.value.ruleSettings?.copy(status = status))
            is Intent.RoundEvents -> state.value.withRound(intent.roundNumber) {
                if (intent.afterEvents == null) it.copy(eventsStatus = status) else it.copy(stateStatus = status)
            }
            is Intent.RoundState -> state.value.withRound(intent.roundNumber) { it.copy(stateStatus = status) }
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
        roundCache.clear()
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

        /** 同場規則設定查詢。
         * @property generation 提出意圖時的條件世代。
         * @property matchId 欲查閱的對局。
         * @property query 當時的有效查閱範圍。
         */
        data class RuleSettings(override val generation: Long, val matchId: String, val query: HistoryBrowseQuery) : Intent

        /** 單局事件頁及成功後的可選牌面導航。
         * @property generation 導航世代。
         * @property matchId 對局識別碼。
         * @property query 有效查閱範圍。
         * @property roundNumber 局序號。
         * @property starts 成功後才提交的事件頁起點堆疊。
         * @property afterEvents 取得事件頁後欲確認的牌面位置；null 為一般事件翻頁。
         */
        data class RoundEvents(
            override val generation: Long,
            val matchId: String,
            val query: HistoryBrowseQuery,
            val roundNumber: Int,
            val starts: List<Int>,
            val afterEvents: HistoryRoundPositionDto? = null,
        ) : Intent {
            /** 建立本次查詢封套。
             * @param requestId 傳輸配對鍵。
             * @return 事件頁要求。
             */
            fun request(requestId: String = ""): HistoryRoundEventsRequestDto = HistoryRoundEventsRequestDto(requestId, matchId, query.scope, roundNumber, starts.last())
        }

        /** 單局完整牌面查詢。
         * @property generation 導航世代。
         * @property matchId 對局識別碼。
         * @property query 有效查閱範圍。
         * @property roundNumber 局序號。
         * @property position 欲確認的局內位置。
         */
        data class RoundState(
            override val generation: Long,
            val matchId: String,
            val query: HistoryBrowseQuery,
            val roundNumber: Int,
            val position: HistoryRoundPositionDto,
        ) : Intent {
            /** 建立本次查詢封套。
             * @param requestId 傳輸配對鍵。
             * @return 牌面要求。
             */
            fun request(requestId: String = ""): HistoryRoundStateRequestDto = HistoryRoundStateRequestDto(requestId, matchId, query.scope, roundNumber, position)
        }
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
