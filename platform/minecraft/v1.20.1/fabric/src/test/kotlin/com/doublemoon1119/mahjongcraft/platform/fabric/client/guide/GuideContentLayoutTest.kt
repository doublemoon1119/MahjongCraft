package com.doublemoon1119.mahjongcraft.platform.fabric.client.guide

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.previewPrompt
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideScene
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideSceneView
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import net.minecraft.network.chat.Component
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證「如何開始」內容的排版：區塊間距、換行、牌列與合成配方。 */
class GuideContentLayoutTest {
    /** 每個字元寬 [CHARACTER_WIDTH]、換行字元一定換行的量測。 */
    private val measurer = object : GuideMeasurer {
        override fun width(text: Component): Int = text.string.length * CHARACTER_WIDTH

        override fun lineCount(text: Component, width: Int): Int = text.string.split('\n').sumOf { line ->
            maxOf(1, (line.length * CHARACTER_WIDTH + width - 1) / width)
        }

        override fun tileName(tile: Tile): Component = Component.literal("NN")

        override fun decisionPanelHeight(prompt: PlayerDecisionPromptDto, width: Int): Int = PANEL_HEIGHT
    }

    /** 段落依行數佔高度，區塊之間留間距，段落標題上方多留一段。 */
    @Test
    fun `blocks stack with gaps and headings get extra space`() {
        val layout = measure(
            GuideBlock.Heading(Component.literal("Title")),
            GuideBlock.Paragraph(Component.literal("one\ntwo")),
            GuideBlock.Heading(Component.literal("Next")),
        )

        val labels = layout.elements.filterIsInstance<GuideElement.Label>()
        assertEquals(listOf(0, LINE + GuideContentLayout.BLOCK_GAP, 3 * LINE + GuideContentLayout.BLOCK_GAP * 2 + GuideContentLayout.HEADING_GAP), labels.map { it.y })
        assertEquals(2, labels[1].lineCount)
        assertEquals(labels[2].y + LINE, layout.height)
    }

    /** 長文字依可用寬度換行。 */
    @Test
    fun `paragraphs wrap to the content width`() {
        val layout = GuideContentLayout.measure(
            blocks = listOf(GuideBlock.Paragraph(Component.literal("x".repeat(30)))),
            width = 60,
            measurer = measurer,
            hasRecipes = true,
            viewportHeight = VIEWPORT,
        )

        assertEquals(3 * LINE, layout.height)
    }

    /** 牌列放不下時換行，符號跟著後面的牌組移到下一行，不會留在行尾。 */
    @Test
    fun `tile rows wrap and keep operators with the next group`() {
        val group = GuideTileItem.Group(List(3) { Tile.Honor.East })
        val groupWidth = 3 * GuideContentLayout.TILE_WIDTH + 2 * GuideContentLayout.TILE_GAP
        val plusWidth = CHARACTER_WIDTH
        val width = groupWidth * 2 + GuideContentLayout.ITEM_GAP + GuideContentLayout.ITEM_GAP / 2
        val layout = GuideContentLayout.measure(
            blocks = listOf(GuideBlock.Tiles(listOf(group, GuideTileItem.Operator(Component.literal("+")), group))),
            width = width,
            measurer = measurer,
            hasRecipes = true,
            viewportHeight = VIEWPORT,
        )

        val tiles = layout.elements.filterIsInstance<GuideElement.TileFace>()
        val operator = layout.elements.filterIsInstance<GuideElement.Label>().single()
        assertEquals(6, tiles.size)
        assertEquals(0, tiles[0].y)
        assertEquals(GuideContentLayout.TILE_HEIGHT + GuideContentLayout.ROW_GAP, tiles[3].y)
        assertEquals(0, operator.x)
        assertTrue(operator.y > tiles[0].y + GuideContentLayout.TILE_HEIGHT)
        assertEquals(plusWidth + GuideContentLayout.ITEM_GAP, tiles[3].x)
        assertEquals(2 * GuideContentLayout.TILE_HEIGHT + GuideContentLayout.ROW_GAP, layout.height)
    }

    /** 有標籤的牌組在牌面上方留一行標籤，附名稱的牌在牌面下方留一行牌名。 */
    @Test
    fun `labels and names add bands around the tiles`() {
        val labelled = measure(GuideBlock.Tiles(listOf(GuideTileItem.Group(listOf(Tile.Honor.Red), Component.literal("L")))))
        val named = measure(GuideBlock.Tiles(listOf(GuideTileItem.Named(Tile.Honor.Red))))

        assertEquals(LINE, labelled.elements.filterIsInstance<GuideElement.TileFace>().single().y)
        assertEquals(LINE + GuideContentLayout.TILE_HEIGHT, labelled.height)
        assertEquals(0, named.elements.filterIsInstance<GuideElement.TileFace>().single().y)
        assertEquals(GuideContentLayout.TILE_HEIGHT + LINE, named.height)
    }

    /** 步驟編號靠左，步驟與補充縮排，相鄰步驟之間留間距。 */
    @Test
    fun `steps are numbered and indented`() {
        val layout = measure(
            GuideBlock.Steps(listOf(GuideStep(Component.literal("a"), Component.literal("hint")), GuideStep(Component.literal("b")))),
        )

        val labels = layout.elements.filterIsInstance<GuideElement.Label>()
        assertEquals(listOf("1.", "a", "hint", "2.", "b"), labels.map { it.text.string })
        assertEquals(listOf(0, GuideContentLayout.STEP_INDENT, GuideContentLayout.STEP_INDENT), labels.take(3).map { it.x })
        assertEquals(2 * LINE + GuideContentLayout.STEP_GAP, labels[3].y)
        assertEquals(GuideTextStyle.HINT, labels[2].style)
    }

    /** 找不到合成配方時配方區塊不佔空間，也不多出間距。 */
    @Test
    fun `missing recipes take no space`() {
        val blocks = listOf(GuideBlock.Paragraph(Component.literal("a")), GuideBlock.Recipe, GuideBlock.Paragraph(Component.literal("b")))
        val without = GuideContentLayout.measure(blocks = blocks, width = WIDTH, measurer = measurer, hasRecipes = false, viewportHeight = VIEWPORT)
        val with = GuideContentLayout.measure(blocks = blocks, width = WIDTH, measurer = measurer, hasRecipes = true, viewportHeight = VIEWPORT)

        assertTrue(without.elements.none { it is GuideElement.Recipe })
        assertEquals(2 * LINE + GuideContentLayout.BLOCK_GAP, without.height)
        assertEquals(without.height + GuideRecipeLayout.HEIGHT + GuideContentLayout.BLOCK_GAP, with.height)
    }

    /** 分隔段落先畫分隔線，再放標題與內容。 */
    @Test
    fun `sections start with a divider`() {
        val layout = measure(GuideBlock.Section(Component.literal("T"), listOf(GuideBlock.Paragraph(Component.literal("body")))))

        val divider = layout.elements.first()
        val labels = layout.elements.filterIsInstance<GuideElement.Label>()
        assertTrue(divider is GuideElement.Divider)
        assertEquals(1 + GuideContentLayout.SECTION_GAP, labels[0].y)
        assertEquals(labels[0].y + LINE + GuideContentLayout.SECTION_GAP, labels[1].y)
    }

    /** 卡片底色包住標題與內容，四周留白，底色先於內容繪製。 */
    @Test
    fun `cards wrap their content with padding`() {
        val layout = measure(GuideBlock.Card(Component.literal("T"), listOf(GuideBlock.Paragraph(Component.literal("body")))))

        val background = layout.elements.first() as GuideElement.CardBackground
        val labels = layout.elements.filterIsInstance<GuideElement.Label>()
        val padding = GuideContentLayout.CARD_PADDING
        assertEquals(listOf(padding, padding), labels.map { it.x })
        assertEquals(padding, labels[0].y)
        assertEquals(labels[1].y + LINE + padding, background.height)
        assertEquals(background.height, layout.height)
    }

    /** 場景高度依寬度比例並限制在上下限內，說明置中放在下方。 */
    @Test
    fun `scene height follows the ratio within its limits`() {
        val scene = GuideScene(blocks = emptyList(), figures = emptyList(), tiles = emptyList(), view = GuideSceneView(yawDegrees = 0f, pitchDegrees = 0f))
        val ratio = measure(GuideBlock.Scene(scene = scene, heightRatio = 0.25f, minimumHeight = 10, maximumHeight = 500, captions = listOf(Component.literal("c"))))
        val clamped = measure(GuideBlock.Scene(scene = scene, heightRatio = 2f, minimumHeight = 10, maximumHeight = 120))

        val view = ratio.elements.filterIsInstance<GuideElement.SceneView>().single()
        val caption = ratio.elements.filterIsInstance<GuideElement.Label>().single()
        assertEquals(WIDTH / 4, view.height)
        assertEquals(view.height, caption.y)
        assertTrue(caption.centered)
        assertEquals(120, clamped.height)
    }

    /** 場景連同說明不超過內容區的可見高度，捲到場景時能看到整個場景。 */
    @Test
    fun `scenes fit within the visible content height`() {
        val scene = GuideScene(blocks = emptyList(), figures = emptyList(), tiles = emptyList(), view = GuideSceneView(yawDegrees = 0f, pitchDegrees = 0f))
        val layout = GuideContentLayout.measure(
            blocks = listOf(GuideBlock.Scene(scene = scene, heightRatio = 1f, minimumHeight = 150, maximumHeight = 400, captions = listOf(Component.literal("c")))),
            width = WIDTH,
            measurer = measurer,
            hasRecipes = true,
            viewportHeight = 100,
        )

        assertEquals(100 - LINE, layout.elements.filterIsInstance<GuideElement.SceneView>().single().height)
        assertEquals(100, layout.height)
    }

    /** 按鈕依文字加寬但不窄於下限；範例面板使用量測出的高度。 */
    @Test
    fun `buttons and panels use their measured sizes`() {
        val button = measure(GuideBlock.Button(Component.literal("Open"), GuideAction.HISTORY)).elements.single() as GuideElement.ActionButton
        val wide = measure(GuideBlock.Button(Component.literal("x".repeat(40)), GuideAction.CLIENT_CONFIG)).elements.single() as GuideElement.ActionButton
        val panel = measure(GuideBlock.DecisionPanel(previewPrompt(triggerTileAssetKey = "m1", fromLeftPlayer = false, actions = emptyList())))

        assertEquals(GuideContentLayout.MINIMUM_BUTTON_WIDTH, button.width)
        assertEquals(40 * CHARACTER_WIDTH + GuideContentLayout.BUTTON_PADDING, wide.width)
        assertEquals(PANEL_HEIGHT, panel.height)
    }

    /**
     * 以預設寬度排版。
     *
     * @param blocks 區塊。
     * @return 版面。
     */
    private fun measure(vararg blocks: GuideBlock): GuideContentLayout = GuideContentLayout.measure(blocks = blocks.toList(), width = WIDTH, measurer = measurer, hasRecipes = true, viewportHeight = VIEWPORT)

    /** 測試用尺寸。 */
    private companion object {
        /** 一個字元的寬度。 */
        const val CHARACTER_WIDTH = 6

        /** 內容寬度。 */
        const val WIDTH = 400

        /** 足以放下所有測試內容的可見高度。 */
        const val VIEWPORT = 1000

        /** 範例面板高度。 */
        const val PANEL_HEIGHT = 50

        /** 一行文字的高度。 */
        const val LINE = GuideContentLayout.LINE_HEIGHT
    }
}
