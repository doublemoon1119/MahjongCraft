package com.doublemoon1119.mahjongcraft.flow.server.game.history.generation

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.random.MatchSeed
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.persistenceJson
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.GameRuntimeStatePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toRuntimeStatePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryOutboxEventPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionExecutor
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.seededRandomMoveHeadlessHistoryRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.testMatchSeed
import kotlinx.coroutines.test.runTest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 驗證對局種子只出現在伺服器的權威存檔，不會出現在牌譜、歷史事件或對局物件的文字輸出。
 *
 * 牌桌狀態與給玩家的快照屬於規則層，型別上無法引用種子；這裡檢查流程層會往外送或寫進紀錄的資料。
 */
class MatchSeedConfidentialityTest {
    /** 驗證跑完一場對局後，牌譜與歷史事件都找不到種子，而伺服器存檔確實保存了種子。 */
    @Test
    fun `seed stays out of history replay and text output`() = runTest {
        val seed = testMatchSeed(SEED)
        val forms = seedForms(seed)
        val runtime = HeadlessFlowHistoryRuntime.create(
            scenario = HeadlessHistoryScenario.RIICHI_EAST,
            registries = seededRandomMoveHeadlessHistoryRegistries(SEED),
            decisionExecutor = AiDecisionExecutor.direct(failurePolicy = ExtensionFailurePolicy.Default),
            matchSeed = seed,
        )
        val game = checkNotNull(runtime.currentGame())
        assertTrue(forms.any { it in runtimeStateJson(game) }, "The server save must keep the seed, otherwise this search proves nothing.")
        assertFalse(forms.any { it in game.toString() }, "The game text form must hide the seed.")

        val events = mutableListOf<HistoryOutboxEvent>()
        HeadlessHistoryMatchRunner(runtime).events().collect { progress ->
            events += progress.events
            runtime.store.acknowledgeHistoryEvents(progress.events.map { it.matchId to it.sequence }.toSet())
            progress.game?.let { current -> assertFalse(forms.any { it in current.toString() }) }
        }

        val registries = bundledPersistenceRegistries()
        val mapper = HistoryRecordingPersistenceMapper(registries)
        val historyText = events.joinToString(separator = "\n") { event ->
            persistenceJson.encodeToString(HistoryOutboxEventPersistenceDto.serializer(), mapper.encodePendingEvent(event))
        }
        val replayText = CompactReplayCodec.encodeCompact(events, mapper, registries).toString()
        assertFalse(forms.any { it in historyText }, "History events must not contain the seed.")
        assertFalse(forms.any { it in replayText }, "The replay must not contain the seed.")
    }

    /**
     * 把對局的伺服器存檔部分轉成文字。
     *
     * @param game 要保存的對局。
     * @return 存檔 JSON 文字。
     */
    private fun runtimeStateJson(game: Game): String = persistenceJson.encodeToString(GameRuntimeStatePersistenceDto.serializer(), game.toRuntimeStatePersistenceDto())

    /**
     * 列出種子可能出現的文字形式：十六進位大小寫、Base64 與逐位元組的數字列表。
     *
     * @param seed 要搜尋的種子。
     * @return 種子的各種文字表示。
     */
    private fun seedForms(seed: MatchSeed): List<String> {
        val bytes = seed.toByteArray()
        return listOf(
            bytes.toHexString(),
            bytes.toHexString(HexFormat.UpperCase),
            Base64.getEncoder().encodeToString(bytes),
            bytes.joinToString(separator = ","),
        )
    }

    /** 測試固定使用的種子。 */
    private companion object {
        /** 決定對局種子與 AI 選擇的數字。 */
        const val SEED = 7
    }
}
