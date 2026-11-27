package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 驗證宣傳截圖挑選牌桌位置的條件與排序。 */
class ShowcaseSiteChooserTest {
    /** 平坦的空地，地面高度 64，上方沒有東西，最上面是高處的櫻花樹葉。 */
    private val open = ShowcaseColumn(ground = 64, obstruction = null, water = false, blossom = true)

    /** 平地上離中心較近的位置勝出，而且不需要墊高。 */
    @Test
    fun `flat open ground picks the nearest spot without raising`() {
        val site = assertNotNull(chooseSite(searchRadius = 4, step = 2) { _, _ -> open })

        assertEquals(0 to 0, site.x to site.z)
        assertEquals(64, site.level)
        assertEquals(emptyList(), site.raised)
    }

    /** 周圍櫻花樹葉較多的位置優先，即使離中心較遠；太少的位置不用。 */
    @Test
    fun `more blossoms win over distance`() {
        val survey = ShowcaseSiteChooser.survey(originX = 0, originZ = 0, searchRadius = 10, step = 10) { x, _ -> open.copy(blossom = x >= 12) }

        assertEquals(10, survey.site?.x)
        assertEquals(mapOf(ShowcaseRejection.FEW_BLOSSOMS to 6), survey.rejections)
    }

    /** 低一格的地面列為要墊高的柱，牌桌放在較高的地面上。 */
    @Test
    fun `a one block step is raised to the higher ground`() {
        val site = assertNotNull(chooseSite { x, _ -> if (x < 0) open.copy(ground = 63) else open })

        assertEquals(64, site.level)
        assertEquals(ShowcaseSiteChooser.TABLE_RADIUS * (ShowcaseSiteChooser.TABLE_RADIUS * 2 + 1), site.raised.size)
        assertEquals(setOf(63), site.raised.map { it.ground }.toSet())
    }

    /** 牌桌範圍內高低差超過一格的位置不用。 */
    @Test
    fun `a slope steeper than one block is skipped`() {
        assertNull(chooseSite { x, _ -> open.copy(ground = 64 + x) })
    }

    /** 鏡頭附近有比牌桌高出兩格的地面時不用，避免鏡頭被山坡擋住。 */
    @Test
    fun `a hillside next to the table is skipped`() {
        assertNull(chooseSite { x, _ -> if (x == ShowcaseSiteChooser.OPEN_RADIUS) open.copy(ground = 66) else open })
    }

    /** 牌桌上方低處有樹葉時不用；樹葉夠高時可以。 */
    @Test
    fun `low leaves block the spot but a high canopy does not`() {
        assertNull(chooseSite { x, z -> if (x == 0 && z == 0) open.copy(obstruction = 66) else open })
        assertNotNull(chooseSite { _, _ -> open.copy(obstruction = 64 + ShowcaseSiteChooser.OPEN_CLEARANCE) })
    }

    /** 牌桌範圍有水時不用。 */
    @Test
    fun `water under the table is skipped`() {
        assertNull(chooseSite { x, _ -> if (x == 1) open.copy(water = true) else open })
    }

    /** 每個原因排除的候選位置數分開記錄，有位置可用時仍會列出被排除的位置。 */
    @Test
    fun `rejections are counted by reason`() {
        val survey = ShowcaseSiteChooser.survey(originX = 0, originZ = 0, searchRadius = 10, step = 10) { x, _ ->
            when {
                x <= -8 -> open.copy(ground = 64 - x)
                x >= 10 -> open.copy(water = true)
                else -> open
            }
        }

        assertEquals(0, survey.site?.x)
        assertEquals(mapOf(ShowcaseRejection.UNEVEN to 3, ShowcaseRejection.WATER to 3), survey.rejections)
    }

    /** 只看搜尋中心一個位置，或依指定範圍挑選，只取結果中的位置。 */
    private fun chooseSite(searchRadius: Int = 0, step: Int = 1, column: (x: Int, z: Int) -> ShowcaseColumn): ShowcaseSite? = ShowcaseSiteChooser.survey(originX = 0, originZ = 0, searchRadius = searchRadius, step = step, column = column).site
}
