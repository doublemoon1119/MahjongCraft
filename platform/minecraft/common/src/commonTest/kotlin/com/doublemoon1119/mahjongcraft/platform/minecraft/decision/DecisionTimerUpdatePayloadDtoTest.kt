package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WaitingTileAvailabilityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.SuitDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/** [DecisionTimerUpdatePayloadDto] 的 JSON 契約測試。 */
class DecisionTimerUpdatePayloadDtoTest {
    /** 比照一般動作計時的規則選擇具有固定的網路列舉值。 */
    @Test
    fun `rule action phase keeps its wire value`() {
        assertEquals(
            expected = "\"RULE_ACTION\"",
            actual = Json.encodeToString(PlayerDecisionPhaseDto.serializer(), PlayerDecisionPhaseDto.RULE_ACTION),
        )
    }

    /** 驗證有效與停止 payload 均可完整來回。 */
    @Test
    fun `test active and stopped payloads round-trip`() {
        val gameId = Uuid.random().toString()
        val payloads = listOf(
            DecisionTimerUpdatePayloadDto(
                gameId = gameId,
                status = DecisionTimerStatusDto(
                    phase = PlayerDecisionPhaseDto.OWN_TURN,
                    baseRemaining = 4_000.milliseconds,
                    reserveRemaining = 20_000.milliseconds,
                    prompt = PlayerDecisionPromptDto(
                        decisionKey = "game:player:own-turn:tile",
                        actions = listOf(
                            PlayerDecisionActionDto(
                                token = "chi",
                                actionId = "mahjongcraft:chi",
                                referenceTileAssetKey = "m3",
                                previewTileAssetKeys = listOf("m1", "m2", "m3"),
                                claimedTileIndex = 2,
                                tileSelection = null,
                            ),
                            PlayerDecisionActionDto(
                                token = "riichi",
                                actionId = "mahjongcraft:riichi",
                                previewTileAssetKeys = listOf("m1"),
                                tileSelection = PlayerDecisionActionTileSelectionDto(
                                    eligibleTileIds = listOf(Uuid.random().toString()),
                                    minCount = 1,
                                    maxCount = 1,
                                    discardAnalyses = listOf(
                                        element = DiscardReadinessAnalysisDto(
                                            discardTileId = Uuid.random().toString(),
                                            waitingTiles = listOf(
                                                element = WaitingTileAvailabilityDto(
                                                    tile = TileDto.Numeric(SuitDto.DOT, 5),
                                                    remainingCount = 2,
                                                    winAvailability = "mahjongcraft:win_available",
                                                ),
                                            ),
                                            statusIndicatorId = null,
                                        ),
                                    ),
                                ),
                                referenceTileAssetKey = null,
                                claimedTileIndex = null,
                            ),
                        ),
                        triggerTileAssetKey = "m3",
                        triggerPlayerId = Uuid.random().toString(),
                        triggerPlayerName = "AI 1",
                        triggerPlayerRelation = DecisionPlayerRelationDto.LEFT,
                        triggerActionId = "mahjongcraft:discard",
                        discardAnalyses = listOf(
                            element = DiscardReadinessAnalysisDto(
                                discardTileId = Uuid.random().toString(),
                                waitingTiles = listOf(
                                    element = WaitingTileAvailabilityDto(
                                        tile = TileDto.Honor.Red,
                                        remainingCount = 3,
                                        winAvailability = "mahjongcraft:win_tsumo_only",
                                    ),
                                ),
                                statusIndicatorId = "mahjongcraft:discard_furiten",
                            ),
                        ),
                        ruleModuleId = null,
                        ruleDecision = null,
                    ),
                ),
            ),
            DecisionTimerUpdatePayloadDto(gameId, null),
        )

        payloads.forEach { payload ->
            val encoded = Json.encodeToString(DecisionTimerUpdatePayloadDto.serializer(), payload)
            assertEquals(payload, Json.decodeFromString(DecisionTimerUpdatePayloadDto.serializer(), encoded))
        }
    }

    /** 驗證封包寫出每個欄位，且欄位名稱與列舉值固定。 */
    @Test
    fun `test payload keeps its wire field names`() {
        val payload = DecisionTimerUpdatePayloadDto(
            gameId = "game",
            status = DecisionTimerStatusDto(
                phase = PlayerDecisionPhaseDto.OWN_TURN,
                baseRemaining = 1_000.milliseconds,
                reserveRemaining = 2_000.milliseconds,
                prompt = PlayerDecisionPromptDto(
                    decisionKey = "key",
                    actions = listOf(PlayerDecisionActionDto(token = "pass", actionId = "mahjongcraft:pass", referenceTileAssetKey = null, previewTileAssetKeys = emptyList(), claimedTileIndex = null, tileSelection = null)),
                    triggerPlayerRelation = DecisionPlayerRelationDto.ACROSS,
                    ruleModuleId = null,
                    triggerTileAssetKey = null,
                    triggerPlayerId = null,
                    triggerPlayerName = null,
                    triggerActionId = null,
                    ruleDecision = null,
                    discardAnalyses = emptyList(),
                ),
            ),
        )

        assertEquals(
            expected = """{"gameId":"game","status":{"phase":"OWN_TURN","baseRemaining":"PT1S","reserveRemaining":"PT2S",""" +
                """"prompt":{"decisionKey":"key","ruleModuleId":null,""" +
                """"actions":[{"token":"pass","actionId":"mahjongcraft:pass","referenceTileAssetKey":null,"previewTileAssetKeys":[],""" +
                """"claimedTileIndex":null,"tileSelection":null}],""" +
                """"triggerTileAssetKey":null,"triggerPlayerId":null,"triggerPlayerName":null,"triggerPlayerRelation":"ACROSS",""" +
                """"triggerActionId":null,"ruleDecision":null,"discardAnalyses":[]}}}""",
            actual = Json.encodeToString(DecisionTimerUpdatePayloadDto.serializer(), payload),
        )
    }
}
