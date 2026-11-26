package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.persistenceJson
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證一局完成摘要保存的下一任莊家在存讀後不變。 */
class RoundCompletionSummaryPersistenceTest {
    /** 連莊、交給下一位與交給指定玩家三種結果都原樣存讀。 */
    @Test
    fun `every next dealer kind round-trips`() {
        val playerIds = List(3) { Uuid.random() }
        val directives = listOf(
            RoundTransitionDirective.RepeatDealer,
            RoundTransitionDirective.PassToNextSeat,
            RoundTransitionDirective.PassTo(playerIds[2]),
        )

        directives.forEach { directive ->
            val summary = RoundCompletionSummary(
                outcomeId = "test:win",
                classification = RoundCompletionClassification.WIN,
                beneficiaryPlayerIds = setOf(playerIds[2]),
                transitionDirective = directive,
                settledScoresByPlayerId = playerIds.associateWith { 25000 },
            )
            val encoded = persistenceJson.encodeToString(RoundCompletionSummaryPersistenceDto.serializer(), summary.toPersistenceDto())

            val decoded = persistenceJson.decodeFromString(RoundCompletionSummaryPersistenceDto.serializer(), encoded).toDomain()

            assertEquals(summary, decoded)
        }
    }
}
