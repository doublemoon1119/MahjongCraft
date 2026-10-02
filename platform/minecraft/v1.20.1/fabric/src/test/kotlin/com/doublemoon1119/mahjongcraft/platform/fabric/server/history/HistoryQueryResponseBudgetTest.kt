package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryIntegrityFilter
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListPage
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryMatchSummary
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryOutcomeFilter
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryParticipantSummary
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryResultSummary
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryRuleSettings
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistorySortField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryErrorCodeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.registerBuiltInRuleConfigDtos
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.buildMahjongDtoSerializersModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** 驗證歷史清單回覆的 UTF-8 大小限制與縮頁 cursor。 */
class HistoryQueryResponseBudgetTest {
    /** 規則設定回應通過正式 registry，大小超限時移除整份設定而不截斷欄位。 */
    @Test
    fun `rule settings budget keeps complete config or returns safe error`() {
        val registries = DefaultNetworkDtoRegistries().apply { registerBuiltInRuleConfigDtos() }
        val json = Json { serializersModule = buildMahjongDtoSerializersModule(registries) }
        val config = GameConfig(RiichiRuleConfig())
        val response = boundedHistoryRuleSettings("rules", HistoryRuleSettings(config), registries, json)
        assertEquals(config, assertNotNull(response.config).toDomain(registries))
        assertNull(response.errorCode)

        val oversized = boundedHistoryRuleSettings("rules", HistoryRuleSettings(config), registries, json, limit = 1)
        assertEquals(HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE, oversized.errorCode)
        assertNull(oversized.config)
    }

    /** 儲存端可解碼但網路 codec 未註冊時，不回傳預設設定或玩家參數錯誤。 */
    @Test
    fun `missing rule network codec returns not available`() {
        val response = boundedHistoryRuleSettings(
            "rules",
            HistoryRuleSettings(GameConfig(RiichiRuleConfig())),
            DefaultNetworkDtoRegistries(),
            Json,
        )
        assertEquals(HistoryQueryErrorCodeDto.NOT_AVAILABLE, response.errorCode)
        assertNull(response.config)
    }

    /** 缩頁後 cursor 應指向實際傳出的最後一筆，不能跳過未傳資料。 */
    @Test
    fun `budget truncation uses last sent entry as cursor`() {
        val first = summary(Uuid.random(), score = 1000)
        val second = summary(Uuid.random(), score = 900)
        val third = summary(Uuid.random(), score = 800)
        val request = HistoryListRequest(sortField = HistorySortField.OWN_SCORE)
        val response = boundedHistoryPage(
            requestId = "request",
            page = HistoryListPage(listOf(first, second, third), null),
            request = request,
            principalId = PLAYER_ID,
            json = Json,
            encodeCursor = { it.matchId.toString() },
            limit = 900,
        )

        assertEquals(1, response.entries.size)
        assertEquals(first.matchId.toString(), response.entries.single().matchId)
        assertEquals(first.matchId.toString(), response.nextCursor)
        assertNull(response.errorCode)
    }

    /** 單筆摘要超過 UTF-8 限制時應回報穩定錯誤。 */
    @Test
    fun `single oversized entry returns content too large`() {
        val response = boundedHistoryPage(
            requestId = "request",
            page = HistoryListPage(listOf(summary(Uuid.random(), diagnostic = "é".repeat(200))), null),
            request = HistoryListRequest(),
            principalId = PLAYER_ID,
            json = Json,
            encodeCursor = { it.matchId.toString() },
            limit = 128,
        )

        assertEquals(HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE, response.errorCode)
        assertEquals(emptyList(), response.entries)
    }

    /** 回覆大小以 UTF-8 位元組計算，而不是以字元數估算。 */
    @Test
    fun `budget counts utf8 bytes`() {
        val response = boundedHistoryPage(
            requestId = "request",
            page = HistoryListPage(listOf(summary(Uuid.random(), diagnostic = "界".repeat(20))), null),
            request = HistoryListRequest(),
            principalId = PLAYER_ID,
            json = Json,
            encodeCursor = { it.matchId.toString() },
            limit = 220,
        )

        assertEquals(HistoryQueryErrorCodeDto.CONTENT_TOO_LARGE, response.errorCode)
    }

    /** 建立測試用摘要。 */
    private fun summary(
        matchId: Uuid,
        score: Int = 1000,
        diagnostic: String? = null,
    ): HistoryMatchSummary = HistoryMatchSummary(
        matchId = matchId,
        ruleId = "mahjongcraft:riichi",
        startedAtEpochMillis = 1,
        endedAtEpochMillis = 2,
        duration = 1.seconds,
        outcome = HistoryOutcomeFilter.COMPLETED,
        integrity = HistoryIntegrityFilter.COMPLETE,
        integrityDiagnostic = diagnostic,
        resultsAvailable = true,
        participants = listOf(HistoryParticipantSummary(0, PLAYER_ID, null)),
        roundCount = 1,
        results = listOf(HistoryResultSummary(PLAYER_ID, score, 1)),
    )

    private companion object {
        /** 測試用的可信玩家 UUID。 */
        val PLAYER_ID: Uuid = Uuid.random()
    }
}
