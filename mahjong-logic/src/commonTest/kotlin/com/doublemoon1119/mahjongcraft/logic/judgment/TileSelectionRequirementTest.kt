package com.doublemoon1119.mahjongcraft.logic.judgment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 驗證動作選牌契約的結構限制。 */
class TileSelectionRequirementTest {
    /** 合法範圍應完整保留。 */
    @Test
    fun `valid selection range is retained`() {
        val tileIds = setOf(Uuid.random(), Uuid.random())

        val requirement = TileSelectionRequirement(tileIds, minCount = 1, maxCount = 2)

        assertEquals(tileIds, requirement.eligibleTileIds)
        assertEquals(1, requirement.minCount)
        assertEquals(2, requirement.maxCount)
    }

    /** 最少選牌數不得小於一。 */
    @Test
    fun `zero minimum is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            TileSelectionRequirement(setOf(Uuid.random()), minCount = 0)
        }
    }

    /** 最大選牌數不得小於最少選牌數。 */
    @Test
    fun `reversed range is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            TileSelectionRequirement(setOf(Uuid.random(), Uuid.random()), minCount = 2, maxCount = 1)
        }
    }

    /** 最大選牌數不得超過候選牌數。 */
    @Test
    fun `maximum exceeding eligible tiles is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            TileSelectionRequirement(setOf(Uuid.random()), minCount = 1, maxCount = 2)
        }
    }
}
