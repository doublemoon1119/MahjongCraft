package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WaitingTileAvailabilityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.SuitDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** [DecisionTimerUpdatePayloadDto] 的 JSON 契約測試。 */
class DecisionTimerUpdatePayloadDtoTest {
    /** 驗證有效與停止 payload 均可完整來回。 */
    @Test
    fun `test active and stopped payloads round-trip`() {
        val gameId = Uuid.random().toString()
        val payloads = listOf(
            DecisionTimerUpdatePayloadDto(
                gameId,
                DecisionTimerStatusDto(
                    PlayerDecisionPhaseDto.OWN_TURN,
                    4_000L,
                    20_000L,
                    PlayerDecisionPromptDto(
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
                                        DiscardReadinessAnalysisDto(
                                            discardTileId = Uuid.random().toString(),
                                            waitingTiles = listOf(
                                                WaitingTileAvailabilityDto(
                                                    TileDto.Numeric(SuitDto.DOT, 5),
                                                    2,
                                                    "mahjongcraft:win_available",
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
                            DiscardReadinessAnalysisDto(
                                discardTileId = Uuid.random().toString(),
                                waitingTiles = listOf(
                                    WaitingTileAvailabilityDto(
                                        TileDto.Honor.Red,
                                        3,
                                        "mahjongcraft:win_tsumo_only",
                                    ),
                                ),
                                statusIndicatorId = "mahjongcraft:discard_furiten",
                            ),
                        ),
                        ruleModuleId = null,
                        preparation = null,
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
            "game",
            DecisionTimerStatusDto(
                PlayerDecisionPhaseDto.OWN_TURN,
                1_000L,
                2_000L,
                PlayerDecisionPromptDto(
                    decisionKey = "key",
                    actions = listOf(PlayerDecisionActionDto(token = "pass", actionId = "mahjongcraft:pass", referenceTileAssetKey = null, previewTileAssetKeys = emptyList(), claimedTileIndex = null, tileSelection = null)),
                    triggerPlayerRelation = DecisionPlayerRelationDto.ACROSS,
                    ruleModuleId = null,
                    triggerTileAssetKey = null,
                    triggerPlayerId = null,
                    triggerPlayerName = null,
                    triggerActionId = null,
                    preparation = null,
                    discardAnalyses = emptyList(),
                ),
            ),
        )

        assertEquals(
            """{"gameId":"game","status":{"phase":"OWN_TURN","baseRemainingMillis":1000,"reserveRemainingMillis":2000,""" +
                """"prompt":{"decisionKey":"key","ruleModuleId":null,""" +
                """"actions":[{"token":"pass","actionId":"mahjongcraft:pass","referenceTileAssetKey":null,"previewTileAssetKeys":[],""" +
                """"claimedTileIndex":null,"tileSelection":null}],""" +
                """"triggerTileAssetKey":null,"triggerPlayerId":null,"triggerPlayerName":null,"triggerPlayerRelation":"ACROSS",""" +
                """"triggerActionId":null,"preparation":null,"discardAnalyses":[]}}}""",
            Json.encodeToString(DecisionTimerUpdatePayloadDto.serializer(), payload),
        )
    }
}
