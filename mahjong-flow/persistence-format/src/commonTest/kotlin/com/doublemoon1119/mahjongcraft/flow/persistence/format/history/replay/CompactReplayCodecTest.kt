package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.GameFlowConfigPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.config.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 驗證精簡 Replay 文件的 envelope 與損壞輸入防護。 */
class CompactReplayCodecTest {
    /** 未知 envelope 欄位不得被靜默忽略。 */
    @Test
    fun `unknown top-level field is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CompactReplayCodec.decodeCompact(
                JsonObject(
                    mapOf(
                        ReplayFormatKeys.FORMAT_VERSION to JsonPrimitive(1),
                        ReplayFormatKeys.PAYLOAD to JsonObject(emptyMap()),
                        "extra" to JsonPrimitive(true),
                    ),
                ),
            )
        }
    }

    /** 未知格式版本不得被當成目前格式解碼。 */
    @Test
    fun `unknown format version is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CompactReplayCodec.decodeCompact(
                JsonObject(mapOf(ReplayFormatKeys.FORMAT_VERSION to JsonPrimitive(2), ReplayFormatKeys.PAYLOAD to JsonObject(emptyMap()))),
            )
        }
    }

    /** 缺少 payload 或交易內容時必須明確失敗。 */
    @Test
    fun `missing replay payload is rejected`() {
        assertFailsWith<IllegalStateException> {
            CompactReplayCodec.decodeCompact(
                JsonObject(mapOf(ReplayFormatKeys.FORMAT_VERSION to JsonPrimitive(1), ReplayFormatKeys.PAYLOAD to JsonArray(emptyList()))),
            )
        }
    }

    /** 規則設定解碼只展開 header，不需要解碼任何局內交易。 */
    @Test
    fun `rule settings decode uses header only`() {
        val registries = bundledPersistenceRegistries()
        val matchId = Uuid.random()
        val document = settingsDocument(matchId, registries.ruleConfigs.encode(RiichiRuleConfig()))

        val settings = CompactReplayCodec.decodeRuleSettings(document, registries, expectedMatchId = matchId)

        assertEquals(RiichiRuleConfig(), settings.ruleConfig)
        assertEquals(GameFlowConfig(), settings.flowConfig)
    }

    /** 開局的非預設規則與流程設定均從 header 還原，不使用目前設定。 */
    @Test
    fun `rule settings decode preserves nondefault rule and flow configuration`() {
        val registries = bundledPersistenceRegistries()
        val matchId = Uuid.random()
        val rule = RiichiRuleConfig(redDoraCount = 0, allowOpenTanyao = false, useLocalYaku = true, minimumWinConstraint = 2)
        val flow = GameFlowConfig(timeControl = ActionTimeControl.from(17, 41), preparationBaseSeconds = 43, spectatingPolicy = SpectatingPolicy.DISABLED)
        val document = settingsDocument(matchId, registries.ruleConfigs.encode(rule), flowConfig = flow)
        val decoded = CompactReplayCodec.decodeRuleSettings(document, registries, matchId)
        assertEquals(rule, decoded.ruleConfig)
        assertEquals(flow, decoded.flowConfig)
    }

    /** 未知規則 codec 不得以目前伺服器設定猜測替代。 */
    @Test
    fun `rule settings decode rejects unknown codec`() {
        val registries = bundledPersistenceRegistries()
        val matchId = Uuid.random()
        val document = settingsDocument(
            matchId,
            TypedPersistenceDto(
                "unknown:rule",
                JsonObject(emptyMap()),
            ),
        )

        assertFailsWith<IllegalStateException> {
            CompactReplayCodec.decodeRuleSettings(document, registries, expectedMatchId = matchId)
        }
    }

    /** 要求的對局 ID 與 Replay header 不一致時必須拒絕。 */
    @Test
    fun `rule settings decode rejects mismatched match`() {
        val registries = bundledPersistenceRegistries()
        val document = settingsDocument(Uuid.random(), registries.ruleConfigs.encode(RiichiRuleConfig()))

        assertFailsWith<IllegalArgumentException> {
            CompactReplayCodec.decodeRuleSettings(document, registries, expectedMatchId = Uuid.random())
        }
    }

    /** 開局規則設定缺少必要 header 時必須拒絕文件。 */
    @Test
    fun `rule settings decode rejects missing configuration header`() {
        val registries = bundledPersistenceRegistries()
        val matchId = Uuid.random()
        val document = settingsDocument(
            matchId,
            registries.ruleConfigs.encode(RiichiRuleConfig()),
            includeRule = false,
        )

        assertFailsWith<IllegalStateException> {
            CompactReplayCodec.decodeRuleSettings(document, registries, expectedMatchId = matchId)
        }
    }

    /** 缺少流程設定不得悄悄改用預設流程設定。 */
    @Test
    fun `rule settings decode rejects missing flow configuration`() {
        val registries = bundledPersistenceRegistries()
        val matchId = Uuid.random()
        val document = settingsDocument(matchId, registries.ruleConfigs.encode(RiichiRuleConfig()), includeFlow = false)
        assertFailsWith<IllegalStateException> {
            CompactReplayCodec.decodeRuleSettings(document, registries, matchId)
        }
    }

    /** 開局設定內容版本不受支援時必須拒絕文件。 */
    @Test
    fun `rule settings decode rejects unknown content version`() {
        val registries = bundledPersistenceRegistries()
        val matchId = Uuid.random()
        val document = settingsDocument(
            matchId,
            registries.ruleConfigs.encode(RiichiRuleConfig()),
            contentVersion = CompactReplayCodec.FORMAT_VERSION + 1,
        )

        assertFailsWith<IllegalArgumentException> {
            CompactReplayCodec.decodeRuleSettings(document, registries, expectedMatchId = matchId)
        }
    }

    /** 局部解碼仍拒絕同一字典索引的不同文字表示，避免重複欄位覆蓋。 */
    @Test
    fun `rule settings dictionary rejects duplicate decoded root keys`() {
        val encoded = CompactReplayDictionary.encode(JsonObject(mapOf(ReplayFormatKeys.VERSION to JsonPrimitive(1))))
        val corrupt = JsonObject(encoded + (ReplayFormatKeys.DATA to JsonObject(mapOf("0" to JsonPrimitive(1), "00" to JsonPrimitive(2)))))
        assertFailsWith<IllegalArgumentException> {
            CompactReplayDictionary.decodeFields(corrupt, setOf(ReplayFormatKeys.VERSION))
        }
    }

    /** 建立最小 Replay，局內內容刻意不提供可解碼的交易資料。
     * @param matchId header 對局識別碼。
     * @param rule 規則持久化 DTO。
     * @param contentVersion 內容版本。
     * @param includeRule 是否加入規則設定。
     * @param flowConfig 開局流程設定。
     * @param includeFlow 是否加入流程設定。
     * @return 供設定查閱測試使用的字典封套。
     */
    private fun settingsDocument(
        matchId: Uuid,
        rule: TypedPersistenceDto,
        contentVersion: Int = CompactReplayCodec.FORMAT_VERSION,
        includeRule: Boolean = true,
        flowConfig: GameFlowConfig = GameFlowConfig(),
        includeFlow: Boolean = true,
    ): JsonObject {
        val flow = Json.encodeToJsonElement(GameFlowConfigPersistenceDto.serializer(), flowConfig.toPersistenceDto())
        val header = buildMap {
            put(ReplayFormatKeys.MATCH, JsonPrimitive(matchId.toString()))
            if (includeRule) put(ReplayFormatKeys.RULE, Json.encodeToJsonElement(rule))
            if (includeFlow) put(ReplayFormatKeys.FLOW, flow)
        }
        val content = JsonObject(
            mapOf(
                ReplayFormatKeys.VERSION to JsonPrimitive(contentVersion),
                ReplayFormatKeys.HEADER to JsonObject(header),
                ReplayFormatKeys.ROUNDS to JsonPrimitive("Transactions are not decoded by this query"),
            ),
        )
        return JsonObject(
            mapOf(
                ReplayFormatKeys.FORMAT_VERSION to JsonPrimitive(CompactReplayCodec.FORMAT_VERSION),
                ReplayFormatKeys.PAYLOAD to CompactReplayDictionary.encode(content),
            ),
        )
    }
}
