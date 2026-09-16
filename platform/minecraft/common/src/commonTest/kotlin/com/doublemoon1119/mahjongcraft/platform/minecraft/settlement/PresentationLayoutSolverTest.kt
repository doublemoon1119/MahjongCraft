package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayoutSolver.Companion.anchorOffset
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayoutSolver.Companion.arrange
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayoutSolver.Companion.crossAxisOffset
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayoutSolver.Companion.styledSize
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayoutSolver.Companion.tileSequenceWidth
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayoutSolver.Companion.unweighted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證宣告式結算版面的量測與配置。 */
class PresentationLayoutSolverTest {
    /** 文字寬度來自量測結果並套用縮放，高度為單行高度。 */
    @Test
    fun `measures a text node from the measured width`() {
        val size = solver().measure(PresentationLayout.Text(TEXT_FIELD, scale = 2f), SNAPSHOT)

        assertEquals(PresentationNodeSize(TEXT_WIDTH * 2f, PresentationLayoutSolver.TEXT_LINE_HEIGHT * 2f), size)
    }

    /** 引用欄位但解析不到內容的節點不佔空間。 */
    @Test
    fun `measures a missing field as nothing`() {
        val size = solver().measure(PresentationLayout.Text(MISSING_FIELD), SNAPSHOT)

        assertEquals(PresentationNodeSize.ZERO, size)
    }

    /** 玩家識別同時顯示頭像與名稱時，寬度含兩者與中間間距。 */
    @Test
    fun `measures a player identity with its face and name`() {
        val size = solver().measure(PresentationLayout.PlayerIdentity(IDENTITY_FIELD, spacing = 4f), SNAPSHOT)

        assertEquals(PresentationLayoutSolver.FACE_SIZE + 4f + NAME_WIDTH, size.width)
        assertEquals(PresentationLayoutSolver.FACE_SIZE, size.height)
    }

    /** 只顯示名稱時不留頭像寬度，也不留兩者之間的間距。 */
    @Test
    fun `measures a player identity without its face`() {
        val size = solver().measure(PresentationLayout.PlayerIdentity(IDENTITY_FIELD, showFace = false, spacing = 4f), SNAPSHOT)

        assertEquals(NAME_WIDTH, size.width)
    }

    /** 牌列寬度含牌之間的間距。 */
    @Test
    fun `measures a tile list with its gaps`() {
        val size = solver().measure(
            PresentationLayout.TileList(TILE_LIST_FIELD, tileWidth = 10f, tileHeight = 14f, spacing = 2f),
            SNAPSHOT,
        )

        assertEquals(3 * 10f + 2 * 2f, size.width)
        assertEquals(14f, size.height)
    }

    /** 沒有牌時不留高度，也不算進負的間距。 */
    @Test
    fun `measures an empty tile sequence as nothing wide`() {
        assertEquals(0f, tileSequenceWidth(count = 0, tileWidth = 10f, gap = 2f))
        assertEquals(10f, tileSequenceWidth(count = 1, tileWidth = 10f, gap = 2f))
    }

    /** 牌組之間套用較大的組間距。 */
    @Test
    fun `measures tile groups with the wider group spacing`() {
        val size = solver().measure(
            PresentationLayout.TileGroups(TILE_GROUPS_FIELD, tileWidth = 10f, tileHeight = 14f, tileSpacing = 2f, groupSpacing = 6f),
            SNAPSHOT,
        )

        // 兩組共四張牌：四張牌寬 + 三個牌間距 + 一個組間距與牌間距的差額。
        assertEquals(4 * 10f + 3 * 2f + (6f - 2f), size.width)
    }

    /** 條目節點的高度以每欄上限為準，不隨總數無限增加。 */
    @Test
    fun `caps the entry height at one column`() {
        val size = solver().measure(
            PresentationLayout.RepeatEntries(ENTRY_FIELD, entriesPerColumn = 2, width = 100f, rowHeight = 11f),
            SNAPSHOT,
        )

        assertEquals(2 * 11f, size.height)
        assertEquals(100f, size.width)
    }

    /** 橫列寬度為子節點總寬加間距，高度取最高的子節點。 */
    @Test
    fun `measures a row from its children`() {
        val size = solver().measure(
            PresentationLayout.Row(children = listOf(spacer(10f, 4f), spacer(20f, 9f)), spacing = 3f),
            SNAPSHOT,
        )

        assertEquals(33f, size.width)
        assertEquals(9f, size.height)
    }

    /** 直行高度為子節點總高加間距，寬度取最寬的子節點。 */
    @Test
    fun `measures a column from its children`() {
        val size = solver().measure(
            PresentationLayout.Column(children = listOf(spacer(10f, 4f), spacer(20f, 9f)), spacing = 3f),
            SNAPSHOT,
        )

        assertEquals(20f, size.width)
        assertEquals(16f, size.height)
    }

    /** 容器留白計入兩側。 */
    @Test
    fun `adds the container padding to both sides`() {
        assertEquals(
            PresentationNodeSize(10f + 6f, 4f + 6f),
            styledSize(10f, 4f, PresentationContainerStyle(padding = 3f)),
        )
    }

    /** 要求填滿時改用版面上限而非內容寬度。 */
    @Test
    fun `fills the maximum width on request`() {
        val style = PresentationContainerStyle(padding = 2f)
        val size = solver().measure(
            PresentationLayout.Row(children = listOf(spacer(10f, 4f)), fillMaxWidth = true, style = style),
            SNAPSHOT,
        )

        assertEquals(PresentationLayoutSolver.MAX_WIDTH, size.width)
    }

    /** Grid 依欄數換行，尺寸以最大的格子為準。 */
    @Test
    fun `measures a grid by its cell size and row count`() {
        val size = solver().measure(
            PresentationLayout.Grid(
                children = List(5) { spacer(10f, 4f) },
                columns = 2,
                horizontalSpacing = 3f,
                verticalSpacing = 1f,
            ),
            SNAPSHOT,
        )

        assertEquals(2 * 10f + 3f, size.width)
        assertEquals(3 * 4f + 2 * 1f, size.height)
    }

    /** 尺寸限制只會夾小，不會撐大。 */
    @Test
    fun `clamps a child to its size constraint`() {
        val solver = solver()

        assertEquals(
            PresentationNodeSize(8f, 4f),
            solver.measure(PresentationLayout.SizeConstraint(spacer(10f, 4f), maxWidth = 8f), SNAPSHOT),
        )
        assertEquals(
            PresentationNodeSize(10f, 4f),
            solver.measure(PresentationLayout.SizeConstraint(spacer(10f, 4f), maxWidth = 30f), SNAPSHOT),
        )
    }

    /** 欄位有值時才量測子樹。 */
    @Test
    fun `measures a conditional subtree only when its field is present`() {
        val solver = solver()

        assertEquals(
            PresentationNodeSize(10f, 4f),
            solver.measure(PresentationLayout.IfPresent(TEXT_FIELD, spacer(10f, 4f)), SNAPSHOT),
        )
        assertEquals(
            PresentationNodeSize.ZERO,
            solver.measure(PresentationLayout.IfPresent(MISSING_FIELD, spacer(10f, 4f)), SNAPSHOT),
        )
    }

    /** 權重節點的量測沿用被包住的子節點。 */
    @Test
    fun `measures a weighted node as its child`() {
        val child = spacer(10f, 4f)

        assertEquals(solver().measure(child, SNAPSHOT), solver().measure(PresentationLayout.Weighted(child), SNAPSHOT))
        assertEquals(child, PresentationLayout.Weighted(child).unweighted())
        assertEquals(child, child.unweighted())
    }

    /** 剩餘空間依權重比例分配。 */
    @Test
    fun `splits the remaining space by weight`() {
        val slots = solver().allocateMainAxis(
            children = listOf(spacer(20f, 4f), PresentationLayout.Weighted(spacer(1f, 4f), weight = 1f), PresentationLayout.Weighted(spacer(1f, 4f), weight = 3f)),
            available = 100f,
            spacing = 0f,
            snapshot = SNAPSHOT,
        )

        assertEquals(listOf(20f, 20f, 60f), slots.map { it.second })
    }

    /** 分配時扣掉子節點之間的間距。 */
    @Test
    fun `reserves the spacing before splitting the remainder`() {
        val slots = solver().allocateMainAxis(
            children = listOf(PresentationLayout.Weighted(spacer(1f, 4f)), PresentationLayout.Weighted(spacer(1f, 4f))),
            available = 100f,
            spacing = 10f,
            snapshot = SNAPSHOT,
        )

        assertEquals(listOf(45f, 45f), slots.map { it.second })
    }

    /** 不填滿的權重節點最多拿到自己的量測寬度。 */
    @Test
    fun `never stretches a weighted node that does not fill`() {
        val slots = solver().allocateMainAxis(
            children = listOf(PresentationLayout.Weighted(spacer(12f, 4f), fill = false)),
            available = 100f,
            spacing = 0f,
            snapshot = SNAPSHOT,
        )

        assertEquals(listOf(12f), slots.map { it.second })
    }

    /** 空間不足時權重節點分不到寬度，不會出現負值。 */
    @Test
    fun `allocates no negative width when the space runs out`() {
        val slots = solver().allocateMainAxis(
            children = listOf(spacer(80f, 4f), PresentationLayout.Weighted(spacer(1f, 4f))),
            available = 40f,
            spacing = 0f,
            snapshot = SNAPSHOT,
        )

        assertEquals(listOf(80f, 0f), slots.map { it.second })
    }

    /** 垂直分配與水平同規則。 */
    @Test
    fun `splits the vertical space by weight`() {
        val heights = solver().allocateVerticalMainAxis(
            children = listOf(spacer(4f, 20f), PresentationLayout.Weighted(spacer(4f, 1f))),
            available = 100f,
            spacing = 0f,
            snapshot = SNAPSHOT,
        )

        assertEquals(listOf(20f, 80f), heights)
    }

    /** 六種 arrangement 的起點與間距。 */
    @Test
    fun `places the children by arrangement`() {
        val widths = listOf(10f, 10f)

        assertEquals(listOf(0f, 10f), arrange(widths, available = 50f, spacing = 0f, arrangement = PresentationArrangement.START))
        assertEquals(listOf(15f, 25f), arrange(widths, available = 50f, spacing = 0f, arrangement = PresentationArrangement.CENTER))
        assertEquals(listOf(30f, 40f), arrange(widths, available = 50f, spacing = 0f, arrangement = PresentationArrangement.END))
        assertEquals(listOf(0f, 40f), arrange(widths, available = 50f, spacing = 0f, arrangement = PresentationArrangement.SPACE_BETWEEN))
        assertEquals(listOf(7.5f, 32.5f), arrange(widths, available = 50f, spacing = 0f, arrangement = PresentationArrangement.SPACE_AROUND))
        assertEquals(listOf(10f, 30f), arrange(widths, available = 50f, spacing = 0f, arrangement = PresentationArrangement.SPACE_EVENLY))
    }

    /** 內容塞滿時六種 arrangement 都退化成依序排開。 */
    @Test
    fun `falls back to sequential placement without free space`() {
        val widths = listOf(10f, 10f)

        PresentationArrangement.entries.forEach { arrangement ->
            assertEquals(
                listOf(0f, 10f),
                arrange(widths, available = 20f, spacing = 0f, arrangement = arrangement),
                "Expected $arrangement to place the children back to back when nothing is left over.",
            )
        }
    }

    /** 只有一個子節點時 SPACE_BETWEEN 不製造間距。 */
    @Test
    fun `adds no gap between a single child`() {
        assertEquals(
            listOf(0f),
            arrange(listOf(10f), available = 50f, spacing = 0f, arrangement = PresentationArrangement.SPACE_BETWEEN),
        )
    }

    /** 沒有子節點時沒有任何位置。 */
    @Test
    fun `places nothing for an empty child list`() {
        assertEquals(emptyList(), arrange(emptyList(), available = 50f, spacing = 4f, arrangement = PresentationArrangement.CENTER))
    }

    /** 對齊點相對於自身尺寸。 */
    @Test
    fun `anchors relative to its own size`() {
        assertEquals(0f, anchorOffset(20f, PresentationAlignment.START))
        assertEquals(10f, anchorOffset(20f, PresentationAlignment.CENTER))
        assertEquals(20f, anchorOffset(20f, PresentationAlignment.END))
    }

    /** 交叉軸偏移在子節點比容器大時不為負。 */
    @Test
    fun `never offsets a child past the start of its container`() {
        assertEquals(5f, crossAxisOffset(available = 20f, childSize = 10f, alignment = PresentationAlignment.CENTER))
        assertEquals(10f, crossAxisOffset(available = 20f, childSize = 10f, alignment = PresentationAlignment.END))
        PresentationAlignment.entries.forEach { alignment ->
            assertTrue(
                crossAxisOffset(available = 10f, childSize = 30f, alignment = alignment) >= 0f,
                "Expected $alignment to keep an oversized child at the container start.",
            )
        }
    }

    /** 建立測試用的解算器；文字寬度以固定值回應，與字型無關。 */
    private fun solver() = PresentationLayoutSolver(
        FakeTemplateRegistry,
        object : PresentationTextMeasurer {
            override fun measure(text: PresentationValue.TextValue): Float = TEXT_WIDTH

            override fun measurePlain(text: String): Float = NAME_WIDTH
        },
    )

    /** 建立固定尺寸、不引用欄位的節點。 */
    private fun spacer(width: Float, height: Float) = PresentationLayout.Spacer(width, height)

    /** 只回應測試用欄位的 registry 測試替身。 */
    private object FakeTemplateRegistry : WinSettlementPresentationTemplateRegistry {
        override val isFrozen: Boolean get() = true

        override val templateKeys: Set<String> get() = emptySet()

        override val registrationKeys: Set<String> get() = emptySet()

        override fun registerTemplate(template: WinSettlementPresentationTemplate) = error("Unexpected registration")

        override fun registerFieldProvider(fieldId: PresentationFieldId, provider: WinSettlementPresentationFieldProvider) = error("Unexpected registration")

        override fun findTemplate(key: String): WinSettlementPresentationTemplate? = null

        override fun findFieldProvider(fieldId: PresentationFieldId): WinSettlementPresentationFieldProvider? = when (fieldId) {
            TEXT_FIELD -> WinSettlementPresentationFieldProvider { PresentationValue.TextValue("mahjongcraft.test") }
            IDENTITY_FIELD -> WinSettlementPresentationFieldProvider {
                PresentationValue.PlayerIdentityValue(playerId = "player", displayName = "Player", isAi = false)
            }
            TILE_LIST_FIELD -> WinSettlementPresentationFieldProvider { PresentationValue.TileListValue(listOf("m1", "m2", "m3")) }
            TILE_GROUPS_FIELD -> WinSettlementPresentationFieldProvider {
                PresentationValue.TileGroupsValue(listOf(listOf("m1", "m2"), listOf("p1", "p2"), emptyList()))
            }
            ENTRY_FIELD -> WinSettlementPresentationFieldProvider { PresentationValue.EntryListValue(List(5) { PresentationValue.EntryListValue.Entry("mahjongcraft.entry") }) }
            else -> null
        }

        override fun freeze() = error("Unexpected freeze")
    }

    private companion object {
        val TEXT_FIELD = PresentationFieldId("mahjongcraft:text")
        val IDENTITY_FIELD = PresentationFieldId("mahjongcraft:identity")
        val TILE_LIST_FIELD = PresentationFieldId("mahjongcraft:tile_list")
        val TILE_GROUPS_FIELD = PresentationFieldId("mahjongcraft:tile_groups")
        val ENTRY_FIELD = PresentationFieldId("mahjongcraft:entries")
        val MISSING_FIELD = PresentationFieldId("mahjongcraft:missing")

        const val TEXT_WIDTH = 30f
        const val NAME_WIDTH = 24f

        val SNAPSHOT = WinSettlementPresentationFieldSnapshot(
            outcomeId = "mahjongcraft:test",
            isTsumo = true,
            winnerId = "winner",
            winnerDisplayName = "Winner",
            winnerIsAi = false,
            responsiblePlayerId = null,
            responsiblePlayerDisplayName = null,
            responsiblePlayerIsAi = null,
            totalScore = 8000,
            tileAssetKeys = emptyList(),
            tileAssetGroups = emptyList(),
            winningTileAssetKey = null,
        )
    }
}
