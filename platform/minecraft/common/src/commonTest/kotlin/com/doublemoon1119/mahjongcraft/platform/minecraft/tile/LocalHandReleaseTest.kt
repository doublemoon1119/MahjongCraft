package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證本機排列的手牌只在完成或整副公開時交回共享呈現。 */
class LocalHandReleaseTest {
    /** 手牌的主人。 */
    private val owner = Uuid.random()

    /** 手牌。 */
    private val hand = List(3) { Uuid.random() }.toSet()

    /** 還在打牌、只有部分牌翻開時維持本機排列。 */
    @Test
    fun `hands in play stay arranged locally`() {
        assertFalse(release(faceUpIds = setOf(hand.first()), evidence = setOf(hand.last())))
        assertFalse(release(handIds = emptySet(), faceUpIds = emptySet(), evidence = emptySet()))
    }

    /** 完成、整副翻開或整副作為結算證據公開時交回共享呈現。 */
    @Test
    fun `finished or fully revealed hands are released`() {
        assertTrue(release(finished = setOf(owner)))
        assertTrue(release(faceUpIds = hand))
        assertTrue(release(evidence = hand))
    }

    /**
     * 以預設的手牌主人與手牌判斷。
     *
     * @param handIds 手牌。
     * @param finished 已完成的玩家。
     * @param faceUpIds 已翻成正面的牌。
     * @param evidence 作為結算證據公開的牌。
     * @return 是否交回共享呈現。
     */
    private fun release(
        handIds: Set<Uuid> = hand,
        finished: Set<Uuid> = emptySet(),
        faceUpIds: Set<Uuid> = emptySet(),
        evidence: Set<Uuid> = emptySet(),
    ) = LocalHandRelease.isReleased(owner, handIds, finished, faceUpIds, evidence)
}
