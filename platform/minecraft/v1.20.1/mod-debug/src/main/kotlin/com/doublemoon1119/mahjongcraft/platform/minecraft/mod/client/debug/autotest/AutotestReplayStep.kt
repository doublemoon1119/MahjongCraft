package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayTransactionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryScreenAutomation
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryScreenAutomationPage
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryScreenAutomationSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryScreenController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.history.HistoryGenerationController
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture.HistoryFixture
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture.HistoryFixtures
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 產生固定內容的測試牌譜，從牌譜列表依序打開摘要、規則設定、單局事件、牌面與回放，在回放中跳到要檢查的步驟截圖。
 *
 * 每份測試牌譜照腳本產生，內容固定；要看的步驟依回放中的事件種類找出，不寫死第幾步。
 */
internal object AutotestReplayStep : AutotestStep {
    override val id: String = "replay"

    override suspend fun run(context: AutotestContext) {
        val matchIds = HistoryFixtures.ALL.associateWith { fixture -> context.generate(fixture) ?: return }
        context.client.setScreen(null)
        if (!context.openAllMatches(matchIds.values)) return
        context.screenshot("list")
        val ponRiichi = matchIds.getValue(HistoryFixtures.RIICHI_PON_RIICHI_IPPATSU_TSUMO)
        if (!context.browseMatch(ponRiichi)) return
        if (!context.replayRound(name = "pon-riichi-tsumo", round = 1, targets = PON_RIICHI_TARGETS)) return
        automation().automationClose()
        if (!context.openAllMatches(matchIds.values)) return
        val ronDraw = matchIds.getValue(HistoryFixtures.RIICHI_RON_THEN_EXHAUSTIVE_DRAW)
        if (!context.openPage("summary of the ron fixture", HistoryScreenAutomationPage.SUMMARY) { it.automationOpenSummary(ronDraw) }) return
        if (!context.replayRound(name = "ron", round = 1, targets = RON_TARGETS)) return
        if (!context.openPage("events after the replay", HistoryScreenAutomationPage.ROUND_EVENTS) { it.automationBackToRound() }) return
        if (!context.openPage("summary after the ron round", HistoryScreenAutomationPage.SUMMARY) { it.automationBackToSummary() }) return
        if (!context.replayRound(name = "exhaustive-draw", round = 2, targets = DRAW_TARGETS)) return
        automation().automationClose()
        for ((fixture, rounds) in ROUND_TOURS) {
            if (!context.openAllMatches(matchIds.values)) return
            if (!context.replayRounds(matchIds.getValue(fixture), rounds)) return
            automation().automationClose()
        }
    }

    /**
     * 從牌譜列表打開一場對局的摘要，依序回放指定的局，每局看完回到摘要。
     *
     * @param matchId 對局識別碼。
     * @param rounds 要回放的局。
     * @return 全部局都回放完時為 true。
     */
    private suspend fun AutotestContext.replayRounds(matchId: String, rounds: List<RoundTour>): Boolean {
        if (!openPage("summary of $matchId", HistoryScreenAutomationPage.SUMMARY) { it.automationOpenSummary(matchId) }) return false
        for (tour in rounds) {
            if (!replayRound(name = tour.name, round = tour.round, targets = tour.targets)) return false
            if (!openPage("events after the ${tour.name} replay", HistoryScreenAutomationPage.ROUND_EVENTS) { it.automationBackToRound() }) return false
            if (!openPage("summary after the ${tour.name} round", HistoryScreenAutomationPage.SUMMARY) { it.automationBackToSummary() }) return false
        }
        return true
    }

    /**
     * 產生一份測試牌譜並等它封存完成。
     *
     * @param fixture 測試牌譜。
     * @return 封存的對局識別碼；失敗時為 null。
     */
    private suspend fun AutotestContext.generate(fixture: HistoryFixture): String? {
        if (!runCheckedCommand("mahjongcraft debug history fixture ${fixture.id}")) return null
        val generator = GlobalContext.get().get<HistoryGenerationController>()
        val finished = waitUntil(GENERATION_TIMEOUT) { generator.snapshot()?.let { it.scenarioId == fixture.id && !it.running } == true }
        val progress = generator.snapshot()
        val archived = progress?.archivedMatchIds?.singleOrNull()
        check("Fixture ${fixture.id} is generated and archived", finished && progress?.failure == null && archived != null, "progress $progress")
        return archived.takeIf { finished && progress?.failure == null }
    }

    /**
     * 打開牌譜列表並切換成顯示所有人的對局，確認測試牌譜都在列表中。
     *
     * @param matchIds 應該出現在列表中的對局識別碼。
     * @return 列表打開而且包含全部對局時為 true。
     */
    private suspend fun AutotestContext.openAllMatches(matchIds: Collection<String>): Boolean {
        historyScreens().openFromCommand()
        if (!check("The history list opens", waitForPage(HistoryScreenAutomationPage.LIST) != null, describe())) return false
        if (!check("The history list switches to every match", automation().automationShowAllMatches(), describe())) return false
        val listed = waitForPage(HistoryScreenAutomationPage.LIST)
        return check("The history list shows every fixture", listed?.listedMatchIds?.containsAll(matchIds) == true, "listed ${listed?.listedMatchIds}")
    }

    /**
     * 從摘要依序打開規則設定、第一局事件與初始牌面，各截一張圖，最後回到單局事件。
     *
     * @param matchId 對局識別碼。
     * @return 全部畫面都打開時為 true。
     */
    private suspend fun AutotestContext.browseMatch(matchId: String): Boolean {
        if (!openPage("match summary", HistoryScreenAutomationPage.SUMMARY) { it.automationOpenSummary(matchId) }) return false
        screenshot("summary")
        if (!openPage("rule settings", HistoryScreenAutomationPage.RULE_SETTINGS) { it.automationOpenRuleSettings() }) return false
        screenshot("rule-settings")
        return openPage("summary again", HistoryScreenAutomationPage.SUMMARY) { it.automationBackToSummary() }
    }

    /**
     * 打開一局的事件、初始牌面與回放，在回放中跳到每個要看的步驟截圖，最後打開回放的顯示設定。
     *
     * @param name 截圖名稱前綴。
     * @param round 局序號。
     * @param targets 要看的步驟與找出它的條件。
     * @return 全部步驟都找到並截圖時為 true。
     */
    private suspend fun AutotestContext.replayRound(name: String, round: Int, targets: List<ReplayTarget>): Boolean {
        if (!openPage("round $round events", HistoryScreenAutomationPage.ROUND_EVENTS) { it.automationOpenRound(round) }) return false
        screenshot("$name-events")
        if (!openPage("round $round initial state", HistoryScreenAutomationPage.ROUND_STATE) { it.automationOpenRoundState(null) }) return false
        screenshot("$name-state")
        if (!openPage("round $round events again", HistoryScreenAutomationPage.ROUND_EVENTS) { it.automationBackToRound() }) return false
        if (!openPage("round $round replay", HistoryScreenAutomationPage.ROUND_REPLAY) { it.automationOpenReplay(0) }) return false
        val loaded = waitUntil(LOAD_TIMEOUT) { automation().automationSnapshot()?.replayTransactions?.isNotEmpty() == true }
        if (!check("The round $round replay loads its events", loaded, describe())) return false
        val transactions = automation().automationSnapshot()!!.replayTransactions
        var previous = 0
        for (target in targets) {
            // 每個要看的步驟都在上一個之後；同一種事件出現多次時（例如同一局的幾次槓）依序取用。
            val index = (previous until transactions.size).firstOrNull { transactions[it].facts.any(target.matches) }
            if (!check("The round $round replay has ${target.name}", index != null, describeFacts(transactions))) return false
            val step = checkNotNull(index) + 1
            previous = step
            val shown = automation().automationReplayTo(step) &&
                waitUntil(LOAD_TIMEOUT) { automation().automationSnapshot()?.let { it.replayStep == step && it.replayStepReady } == true }
            if (!check("The replay shows ${target.name} at step $step", shown, describe())) return false
            screenshot("$name-${target.name.replace(' ', '-')}")
        }
        val visibility = automation().automationOpenReplayVisibility()
        if (!check("The replay visibility settings open", visibility)) return false
        screenshot("$name-visibility")
        client.screen?.onClose()
        return check("The replay returns from its visibility settings", waitUntil(LOAD_TIMEOUT) { automation().automationSnapshot()?.page == HistoryScreenAutomationPage.ROUND_REPLAY })
    }

    /**
     * 執行一個切換頁面的操作，等新頁面載入完成。
     *
     * @param name 檢查名稱。
     * @param page 應該切換到的頁面。
     * @param action 切換的操作。
     * @return 切換成功而且載入完成時為 true。
     */
    private suspend fun AutotestContext.openPage(name: String, page: HistoryScreenAutomationPage, action: (HistoryScreenAutomation) -> Boolean): Boolean {
        val accepted = action(automation())
        val ready = accepted && waitForPage(page) != null
        return check("The history $name opens", ready, describe())
    }

    /**
     * 等牌譜瀏覽切換到指定頁面並載入完成。
     *
     * @param page 頁面。
     * @return 載入完成時的狀態；逾時或查詢失敗時為 null。
     */
    private suspend fun AutotestContext.waitForPage(page: HistoryScreenAutomationPage): HistoryScreenAutomationSnapshot? {
        waitUntil(LOAD_TIMEOUT) { automation().automationSnapshot()?.let { it.page == page && (!it.loading || it.failed) } == true }
        return automation().automationSnapshot()?.takeIf { it.page == page && !it.loading && !it.failed }
    }

    /**
     * 目前牌譜瀏覽狀態的說明，放在檢查失敗的細節中。
     *
     * @return 說明文字。
     */
    private fun AutotestContext.describe(): String = "screen ${client.screen?.javaClass?.simpleName}, state ${automation().automationSnapshot()?.copy(replayTransactions = emptyList())}"

    /**
     * 回放中全部事件的種類，放在找不到步驟時的細節中。
     *
     * @param transactions 回放的交易。
     * @return 說明文字。
     */
    private fun describeFacts(transactions: List<HistoryReplayTransactionDto>): String = transactions.joinToString { transaction ->
        transaction.facts.joinToString("+") { fact -> if (fact is HistoryReplayFactDto.KnownAction) "${fact.typeKey}:${fact.actionType}:${fact.extensionTypeId}" else fact.typeKey }
    }

    /**
     * 操作牌譜畫面的入口。
     *
     * @return 入口。
     */
    private fun automation(): HistoryScreenAutomation = historyScreens()

    /**
     * 牌譜畫面的開啟入口，與玩家執行開啟牌譜的指令相同。
     *
     * @return 牌譜畫面的開啟入口。
     */
    private fun historyScreens(): HistoryScreenController = GlobalContext.get().get<HistoryScreenController>()

    /**
     * 回放中要看的一個步驟。
     *
     * @property name 截圖與檢查使用的名稱。
     * @property matches 找出這一步的條件。
     */
    private class ReplayTarget(val name: String, val matches: (HistoryReplayFactDto) -> Boolean)

    /** 碰、立直與一發自摸那一局要看的步驟。 */
    private val PON_RIICHI_TARGETS = listOf(
        ReplayTarget("pon") { it is HistoryReplayFactDto.KnownAction && it.actionType.contains("pon", ignoreCase = true) },
        ReplayTarget("riichi") { it is HistoryReplayFactDto.KnownAction && it.extensionTypeId.orEmpty().contains("riichi") },
        ReplayTarget("tsumo") { it is HistoryReplayFactDto.KnownAction && it.actionType == "tsumo" },
        ReplayTarget("settlement") { it.typeKey == ROUND_COMPLETED },
    )

    /** 榮和那一局要看的步驟。 */
    private val RON_TARGETS = listOf(
        ReplayTarget("ron") { it.typeKey == WIN_SETTLED },
        ReplayTarget("settlement") { it.typeKey == ROUND_COMPLETED },
    )

    /** 流局那一局要看的步驟。 */
    private val DRAW_TARGETS = listOf(ReplayTarget("exhaustive draw") { it.typeKey == ROUND_COMPLETED })

    /** 一局的結算。 */
    private val SETTLEMENT = ReplayTarget("settlement") { it.typeKey == ROUND_COMPLETED }

    /** 自摸。 */
    private val TSUMO = ReplayTarget("tsumo") { it is HistoryReplayFactDto.KnownAction && it.actionType == "tsumo" }

    /**
     * 回放一局時要看的步驟。
     *
     * @property name 截圖名稱前綴。
     * @property round 局序號。
     * @property targets 要看的步驟。
     */
    private class RoundTour(val name: String, val round: Int, val targets: List<ReplayTarget>)

    /**
     * 指定動作種類的步驟。
     *
     * @param name 截圖與檢查使用的名稱。
     * @param actionType 動作種類。
     * @return 要看的步驟。
     */
    private fun action(name: String, actionType: String) = ReplayTarget(name) { it is HistoryReplayFactDto.KnownAction && it.actionType == actionType }

    /** 開局補花的事件種類。 */
    private const val FLOWER_REPLACEMENT = "tile_replacement_round_resolved"

    /** 由規則效果結束一局的花牌和牌。 */
    private val FLOWER_WIN = ReplayTarget("flower win") { it.typeKey == "rule_effect_resolved" || it.typeKey == WIN_SETTLED }

    /** 其餘測試牌譜各自要回放的局。 */
    private val ROUND_TOURS: List<Pair<HistoryFixture, List<RoundTour>>> = listOf(
        HistoryFixtures.RIICHI_CALLS_AND_KANS to listOf(
            element = RoundTour(
                name = "calls-kans",
                round = 1,
                targets = listOf(
                    action("closed kan", "kan"),
                    action("pon", "pon"),
                    action("chi", "chi"),
                    action("added kan", "kan"),
                    action("open kan", "kan"),
                    TSUMO,
                    SETTLEMENT,
                ),
            ),
        ),
        HistoryFixtures.RIICHI_DOUBLE_RON_PAO_KYUUSHU to listOf(
            RoundTour(name = "double-ron", round = 1, targets = listOf(ReplayTarget("double ron") { it.typeKey == WIN_SETTLED }, SETTLEMENT)),
            RoundTour(name = "kyuushu", round = 2, targets = listOf(SETTLEMENT)),
            RoundTour(
                name = "pao",
                round = 3,
                targets = listOf(action("white pon", "pon"), action("green pon", "pon"), action("red pon", "pon"), TSUMO, SETTLEMENT),
            ),
        ),
        HistoryFixtures.THREE_PLAYER_RIICHI_PULL_NORTH_TSUMO to listOf(
            element = RoundTour(
                name = "pull-north",
                round = 1,
                targets = listOf(
                    ReplayTarget("pull north") { it is HistoryReplayFactDto.KnownAction && it.extensionTypeId.orEmpty().contains("pull_north") },
                    TSUMO,
                    SETTLEMENT,
                ),
            ),
        ),
        HistoryFixtures.TAIWAN_FLOWERS_TSUMO_RON_DRAW to listOf(
            RoundTour(
                name = "taiwan-flowers",
                round = 1,
                targets = listOf(
                    ReplayTarget("dealt flower") { it.typeKey == FLOWER_REPLACEMENT },
                    ReplayTarget("drawn flower") { it is HistoryReplayFactDto.KnownAction && it.extensionTypeId.orEmpty().contains("reveal_flower") },
                    TSUMO,
                    SETTLEMENT,
                ),
            ),
            RoundTour(name = "taiwan-ron", round = 2, targets = listOf(ReplayTarget("ron") { it.typeKey == WIN_SETTLED }, SETTLEMENT)),
            RoundTour(name = "taiwan-draw", round = 3, targets = listOf(SETTLEMENT)),
        ),
        HistoryFixtures.TAIWAN_EIGHT_FLOWERS_AND_ROBBING to listOf(
            RoundTour(name = "eight-flowers", round = 1, targets = listOf(FLOWER_WIN)),
            RoundTour(name = "seven-rob-one", round = 2, targets = listOf(FLOWER_WIN)),
        ),
    )

    /** 一局結束的事件種類。 */
    private const val ROUND_COMPLETED = "round_completed"

    /** 和牌結算的事件種類。 */
    private const val WIN_SETTLED = "win_settled"

    /** 等測試牌譜產生並封存的最長時間。 */
    private val GENERATION_TIMEOUT = 3.minutes

    /** 等牌譜畫面查詢回應的最長時間。 */
    private val LOAD_TIMEOUT = 30.seconds
}
