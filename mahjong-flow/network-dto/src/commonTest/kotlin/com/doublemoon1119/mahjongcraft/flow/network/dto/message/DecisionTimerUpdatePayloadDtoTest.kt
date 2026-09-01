package com.doublemoon1119.mahjongcraft.flow.network.dto.message

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
                                claimedTileOrientation = DecisionTileOrientationDto.ROTATED_RIGHT,
                            ),
                        ),
                        triggerTileAssetKey = "m3",
                        triggerPlayerId = Uuid.random().toString(),
                        triggerPlayerName = "AI 1",
                        triggerPlayerRelation = DecisionPlayerRelationDto.LEFT,
                        triggerActionId = "mahjongcraft:discard",
                        riichiTileIds = listOf(Uuid.random().toString()),
                        discardAnalyses = listOf(
                            DiscardReadinessAnalysisDto(
                                discardTileId = Uuid.random().toString(),
                                waitingTiles = listOf(WaitingTileAvailabilityDto("m2", 3)),
                                statusIndicatorId = "mahjongcraft:discard_furiten",
                            ),
                        ),
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
}
