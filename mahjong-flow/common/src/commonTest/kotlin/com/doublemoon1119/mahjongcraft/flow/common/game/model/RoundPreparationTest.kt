package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 開局準備受控輸入、提交與預設決策測試。 */
class RoundPreparationTest {
    /** 單選預設提交固定選擇第一個合法選項。 */
    @Test
    fun `single choice default is deterministic`() {
        val spec = RoundPreparationInputSpec.SingleChoice(listOf("example:first", "example:second"))

        assertEquals(RoundPreparationSubmission.Choice("example:first"), spec.defaultSubmission())
    }

    /** 選牌預設提交依 Uuid 穩定排序並只選最少張數。 */
    @Test
    fun `tile selection default is deterministic`() {
        val ids = listOf(Uuid.random(), Uuid.random(), Uuid.random(), Uuid.random())
        val spec = RoundPreparationInputSpec.TileSelection(ids.toSet(), minCount = 3, maxCount = 3)
        val expected = ids.sortedBy(Uuid::toString).take(3).toSet()

        assertEquals(RoundPreparationSubmission.Tiles(expected), spec.defaultSubmission())
    }

    /** 核心結構驗證拒絕未知選項、錯誤數量與非本人合法牌。 */
    @Test
    fun `controlled specs reject malformed submissions`() {
        val eligible = setOf(Uuid.random(), Uuid.random(), Uuid.random())
        val choice = RoundPreparationInputSpec.SingleChoice(listOf("example:one"))
        val tiles = RoundPreparationInputSpec.TileSelection(eligible, minCount = 2, maxCount = 2)

        assertFalse(choice.accepts(RoundPreparationSubmission.Choice("example:unknown")))
        assertFalse(tiles.accepts(RoundPreparationSubmission.Tiles(setOf(eligible.first()))))
        assertFalse(tiles.accepts(RoundPreparationSubmission.Tiles(setOf(eligible.first(), Uuid.random()))))
        assertTrue(tiles.accepts(RoundPreparationSubmission.Tiles(eligible.take(2).toSet())))
    }

    /** Pending state 只在每位參與者都有一筆提交後完成。 */
    @Test
    fun `pending step completes after every participant submits`() {
        val first = Uuid.random()
        val second = Uuid.random()
        val spec = RoundPreparationInputSpec.Confirmation
        val pending = PendingRoundPreparation(
            stepId = "example:confirm",
            stepIndex = 0,
            inputSpecsByPlayerId = mapOf(first to spec, second to spec),
        )

        assertFalse(pending.isComplete)
        assertFalse(pending.copy(submissionsByPlayerId = mapOf(first to RoundPreparationSubmission.Confirmed)).isComplete)
        assertTrue(
            pending.copy(
                submissionsByPlayerId = mapOf(
                    first to RoundPreparationSubmission.Confirmed,
                    second to RoundPreparationSubmission.Confirmed,
                ),
            ).isComplete,
        )
    }
}
