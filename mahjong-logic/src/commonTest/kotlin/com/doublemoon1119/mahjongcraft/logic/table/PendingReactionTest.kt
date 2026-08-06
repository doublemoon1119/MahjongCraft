package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [PendingReaction] 的單元測試類別。
 *
 * 驗證 [PendingReaction.isComplete] 的判斷邏輯。
 */
class PendingReactionTest {

    private val discarderId = Uuid.random()
    private val tileId = Uuid.random()
    private val playerAId = Uuid.random()
    private val playerBId = Uuid.random()

    /**
     * 驗證尚未有任何玩家回應時，isComplete 為 false。
     */
    @Test
    fun `test isComplete is false when no one has responded`() {
        val pendingReaction = PendingReaction(
            discarderId = discarderId,
            tileId = tileId,
            eligiblePlayerIds = setOf(playerAId, playerBId),
        )

        assertFalse(pendingReaction.isComplete)
    }

    /**
     * 驗證只有部分有資格的玩家回應時，isComplete 仍為 false。
     */
    @Test
    fun `test isComplete is false when only some eligible players have responded`() {
        val pendingReaction = PendingReaction(
            discarderId = discarderId,
            tileId = tileId,
            eligiblePlayerIds = setOf(playerAId, playerBId),
            responses = mapOf(playerAId to GameAction.Pass),
        )

        assertFalse(pendingReaction.isComplete)
    }

    /**
     * 驗證所有有資格的玩家都已回應時，isComplete 為 true。
     */
    @Test
    fun `test isComplete is true when all eligible players have responded`() {
        val pendingReaction = PendingReaction(
            discarderId = discarderId,
            tileId = tileId,
            eligiblePlayerIds = setOf(playerAId, playerBId),
            responses = mapOf(playerAId to GameAction.Pass, playerBId to GameAction.Pon(tileId)),
        )

        assertTrue(pendingReaction.isComplete)
    }

    /**
     * 驗證沒有任何玩家有資格回應時（空集合），isComplete 一律為 true（沒有東西需要等）。
     */
    @Test
    fun `test isComplete is true when there are no eligible players`() {
        val pendingReaction = PendingReaction(
            discarderId = discarderId,
            tileId = tileId,
            eligiblePlayerIds = emptySet(),
        )

        assertTrue(pendingReaction.isComplete)
    }
}
