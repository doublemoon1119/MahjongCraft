package com.doublemoon1119.mahjongcraft.platform.fabric.client.guide

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideScene
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import net.minecraft.text.Text
import kotlin.math.roundToInt

/** 版面計算需要的文字與面板量測，畫面以文字繪製器與操作面板繪製實作，測試以固定尺寸實作。 */
internal interface GuideMeasurer {
    /**
     * 文字的寬度。
     *
     * @param text 文字。
     * @return 單行寬度。
     */
    fun width(text: Text): Int

    /**
     * 文字在指定寬度內換行後的行數；換行字元一定換行。
     *
     * @param text 文字。
     * @param width 可用寬度。
     * @return 行數，至少 1。
     */
    fun lineCount(text: Text, width: Int): Int

    /**
     * 牌名，例如「1萬」。
     *
     * @param tile 牌。
     * @return 牌名。
     */
    fun tileName(tile: Tile): Text

    /**
     * 範例操作面板連同觸發框的高度。
     *
     * @param prompt 範例決策提示。
     * @param width 可用寬度。
     * @return 高度。
     */
    fun decisionPanelHeight(prompt: PlayerDecisionPromptDto, width: Int): Int
}

/** 「如何開始」內容區中已決定位置的一個元素；座標以內容區左上角為原點，未套用捲動。 */
internal sealed interface GuideElement {
    /** 上界。 */
    val y: Int

    /** 高度。 */
    val height: Int

    /**
     * 一段文字，在 [width] 內換行繪製。
     *
     * @property text 文字。
     * @property x 左界。
     * @property y 上界。
     * @property width 換行寬度；置中時為置中的範圍。
     * @property lineCount 換行後的行數。
     * @property style 文字樣式。
     * @property centered 是否每行水平置中。
     */
    data class Label(
        val text: Text,
        val x: Int,
        override val y: Int,
        val width: Int,
        val lineCount: Int,
        val style: GuideTextStyle,
        val centered: Boolean = false,
    ) : GuideElement {
        override val height: Int get() = lineCount * GuideContentLayout.LINE_HEIGHT
    }

    /**
     * 一張平面牌面。
     *
     * @property tile 牌。
     * @property x 左界。
     * @property y 上界。
     */
    data class TileFace(val tile: Tile, val x: Int, override val y: Int) : GuideElement {
        override val height: Int get() = GuideContentLayout.TILE_HEIGHT
    }

    /**
     * 一條水平分隔線。
     *
     * @property x 左界。
     * @property y 上界。
     * @property width 寬度。
     */
    data class Divider(val x: Int, override val y: Int, val width: Int) : GuideElement {
        override val height: Int get() = 1
    }

    /**
     * 一個示意場景。
     *
     * @property scene 場景。
     * @property x 左界。
     * @property y 上界。
     * @property width 寬度。
     * @property height 高度。
     */
    data class SceneView(
        val scene: GuideScene,
        val x: Int,
        override val y: Int,
        val width: Int,
        override val height: Int,
    ) : GuideElement

    /**
     * 卡片的底色，先於卡片內容繪製。
     *
     * @property x 左界。
     * @property y 上界。
     * @property width 寬度。
     * @property height 高度。
     */
    data class CardBackground(
        val x: Int,
        override val y: Int,
        val width: Int,
        override val height: Int,
    ) : GuideElement

    /**
     * 開啟其他畫面的按鈕。
     *
     * @property label 按鈕文字。
     * @property action 按下後開啟的畫面。
     * @property x 左界。
     * @property y 上界。
     * @property width 寬度。
     */
    data class ActionButton(
        val label: Text,
        val action: GuideAction,
        val x: Int,
        override val y: Int,
        val width: Int,
    ) : GuideElement {
        override val height: Int get() = GuideContentLayout.BUTTON_HEIGHT
    }

    /**
     * 合成配方輪播。
     *
     * @property layout 輪播內各部分的位置。
     */
    data class Recipe(val layout: GuideRecipeLayout) : GuideElement {
        override val y: Int get() = layout.top
        override val height: Int get() = GuideRecipeLayout.HEIGHT
    }

    /**
     * 範例操作面板。
     *
     * @property prompt 範例決策提示。
     * @property x 左界。
     * @property y 上界。
     * @property width 面板可用寬度，面板在其中水平置中。
     * @property height 面板連同觸發框的高度。
     */
    data class DecisionPanel(
        val prompt: PlayerDecisionPromptDto,
        val x: Int,
        override val y: Int,
        val width: Int,
        override val height: Int,
    ) : GuideElement
}

/** 「如何開始」文字的樣式。 */
internal enum class GuideTextStyle {
    /** 段落標題。 */
    HEADING,

    /** 一般內文。 */
    BODY,

    /** 灰色補充說明。 */
    HINT,

    /** 牌組上方與牌下方的小標籤。 */
    LABEL,

    /** 牌組之間的符號，例如加號與箭頭。 */
    OPERATOR,

    /** 強調色文字，例如「和牌」。 */
    ACCENT,
}

/**
 * 合成配方輪播的位置：上方是置中的原版工作台介面（取介面圖上半部的標題、3×3 材料格、箭頭與成品格，
 * 接上介面圖下緣的邊框），下方依序是成品名稱，以及上一個、頁碼與下一個。
 *
 * 材料格與成品格的位置與原版工作台介面相同，因此材質包替換介面圖時格子仍然對齊。
 *
 * @property left 範圍左界。
 * @property top 範圍上界，也就是介面圖的上界。
 * @property width 範圍寬度。
 */
internal data class GuideRecipeLayout(val left: Int, val top: Int, val width: Int) {
    /** 範圍水平中心。 */
    val centerX: Int get() = left + width / 2

    /** 介面圖的左界。 */
    val panelLeft: Int get() = centerX - PANEL_WIDTH / 2

    /** 標題文字的左界。 */
    val titleX: Int get() = panelLeft + TITLE_X

    /** 標題文字的上界。 */
    val titleY: Int get() = top + TITLE_Y

    /** 成品物品的左界。 */
    val outputX: Int get() = panelLeft + OUTPUT_X

    /** 成品物品的上界。 */
    val outputY: Int get() = top + OUTPUT_Y

    /** 成品名稱文字的上界。 */
    val nameY: Int get() = top + PANEL_HEIGHT + NAME_GAP

    /** 上一個與下一個按鈕的上界。 */
    val controlsY: Int get() = nameY + GuideContentLayout.LINE_HEIGHT + CONTROLS_GAP

    /** 上一個按鈕的左界。 */
    val previousX: Int get() = centerX - CONTROLS_WIDTH / 2

    /** 下一個按鈕的左界。 */
    val nextX: Int get() = centerX + CONTROLS_WIDTH / 2 - CONTROL_SIZE

    /**
     * 第 [index] 個材料格中物品的左上角，由左到右、由上到下。
     *
     * @param index 材料格順序，0 到 8。
     * @return 左界與上界。
     */
    fun slot(index: Int): Pair<Int, Int> = (panelLeft + GRID_X + index % GRID_SIZE * SLOT_SIZE) to (top + GRID_Y + index / GRID_SIZE * SLOT_SIZE)

    /** 原版工作台介面圖中的位置與輪播的尺寸。 */
    internal companion object {
        /** 材料格每邊的格數。 */
        const val GRID_SIZE: Int = 3

        /** 相鄰材料格的距離。 */
        const val SLOT_SIZE: Int = 18

        /** 物品圖示的邊長。 */
        const val ITEM_SIZE: Int = 16

        /** 介面圖寬度，與原版工作台介面相同。 */
        const val PANEL_WIDTH: Int = 176

        /** 取用介面圖上半部的高度，含標題、材料格與成品格。 */
        const val PANEL_TOP_HEIGHT: Int = 78

        /** 接在上半部之後的介面圖下緣邊框在介面圖中的上界。 */
        const val PANEL_BORDER_V: Int = 162

        /** 介面圖下緣邊框的高度。 */
        const val PANEL_BORDER_HEIGHT: Int = 4

        /** 畫出的介面圖總高度。 */
        const val PANEL_HEIGHT: Int = PANEL_TOP_HEIGHT + PANEL_BORDER_HEIGHT

        /** 標題文字在介面圖中的左界。 */
        const val TITLE_X: Int = 29

        /** 標題文字在介面圖中的上界。 */
        const val TITLE_Y: Int = 6

        /** 第一個材料格物品在介面圖中的左界。 */
        const val GRID_X: Int = 30

        /** 第一個材料格物品在介面圖中的上界。 */
        const val GRID_Y: Int = 17

        /** 成品物品在介面圖中的左界。 */
        const val OUTPUT_X: Int = 124

        /** 成品物品在介面圖中的上界。 */
        const val OUTPUT_Y: Int = 35

        /** 介面圖與成品名稱的間距。 */
        const val NAME_GAP: Int = 4

        /** 成品名稱與按鈕列的間距。 */
        const val CONTROLS_GAP: Int = 3

        /** 上一個與下一個按鈕的邊長。 */
        const val CONTROL_SIZE: Int = 14

        /** 上一個按鈕左界到下一個按鈕右界的寬度。 */
        const val CONTROLS_WIDTH: Int = 96

        /** 輪播高度。 */
        const val HEIGHT: Int = PANEL_HEIGHT + NAME_GAP + GuideContentLayout.LINE_HEIGHT + CONTROLS_GAP + CONTROL_SIZE
    }
}

/**
 * 依可用寬度把一頁的 [GuideBlock] 排成由上而下的 [GuideElement]。
 *
 * @property elements 依繪製順序排列的元素。
 * @property height 內容總高度。
 */
internal data class GuideContentLayout(val elements: List<GuideElement>, val height: Int) {
    /** 版面計算與共用尺寸。 */
    internal companion object {
        /** 一行文字的高度。 */
        const val LINE_HEIGHT: Int = 10

        /** 平面牌面的寬度。 */
        const val TILE_WIDTH: Int = 15

        /** 平面牌面的高度。 */
        const val TILE_HEIGHT: Int = 20

        /** 同一組牌之間的間距。 */
        const val TILE_GAP: Int = 1

        /** 牌列中相鄰項目的間距。 */
        const val ITEM_GAP: Int = 6

        /** 牌列換行時兩行的間距。 */
        const val ROW_GAP: Int = 4

        /** 相鄰區塊的間距。 */
        const val BLOCK_GAP: Int = 6

        /** 段落標題上方額外的間距。 */
        const val HEADING_GAP: Int = 10

        /** 分隔段落的分隔線與標題、內容的間距。 */
        const val SECTION_GAP: Int = 4

        /** 卡片內容與卡片邊緣的距離。 */
        const val CARD_PADDING: Int = 6

        /** 步驟編號所佔的寬度。 */
        const val STEP_INDENT: Int = 12

        /** 相鄰步驟的間距。 */
        const val STEP_GAP: Int = 4

        /** 可見高度極小時場景仍保留的高度。 */
        const val MINIMUM_SCENE_HEIGHT: Int = 20

        /** 按鈕高度。 */
        const val BUTTON_HEIGHT: Int = 20

        /** 按鈕寬度下限。 */
        const val MINIMUM_BUTTON_WIDTH: Int = 120

        /** 按鈕文字左右留白合計。 */
        const val BUTTON_PADDING: Int = 24

        /**
         * 排版一頁。
         *
         * @param blocks 由上而下的區塊。
         * @param width 內容區寬度。
         * @param measurer 文字與面板量測。
         * @param hasRecipes 是否找得到合成配方；找不到時配方區塊不佔空間。
         * @param viewportHeight 內容區可見的高度；場景連同說明不超過這個高度，捲到場景時能看到整個場景。
         * @return 排好的版面。
         */
        fun measure(
            blocks: List<GuideBlock>,
            width: Int,
            measurer: GuideMeasurer,
            hasRecipes: Boolean,
            viewportHeight: Int,
        ): GuideContentLayout {
            val builder = Builder(
                width = width.coerceAtLeast(1),
                measurer = measurer,
                hasRecipes = hasRecipes,
                viewportHeight = viewportHeight,
            )
            val bottom = builder.blocks(blocks = blocks, left = 0, top = 0, width = width.coerceAtLeast(1))
            return GuideContentLayout(elements = builder.elements, height = bottom)
        }
    }

    /**
     * 累積元素的排版過程。
     *
     * @property width 內容區寬度。
     * @property measurer 文字與面板量測。
     * @property hasRecipes 是否找得到合成配方。
     * @property viewportHeight 內容區可見的高度。
     */
    private class Builder(
        val width: Int,
        val measurer: GuideMeasurer,
        val hasRecipes: Boolean,
        val viewportHeight: Int,
    ) {
        /** 已排好的元素。 */
        val elements = mutableListOf<GuideElement>()

        /**
         * 由上而下排一串區塊，區塊之間留 [BLOCK_GAP]。
         *
         * @param blocks 區塊。
         * @param left 左界。
         * @param top 第一個區塊的上界。
         * @param width 可用寬度。
         * @return 最後一個區塊的下界；沒有佔空間的區塊時為 [top]。
         */
        fun blocks(
            blocks: List<GuideBlock>,
            left: Int,
            top: Int,
            width: Int,
        ): Int {
            var y = top
            var first = true
            blocks.forEach { block ->
                val start = when {
                    first -> y
                    block is GuideBlock.Heading -> y + BLOCK_GAP + HEADING_GAP
                    else -> y + BLOCK_GAP
                }
                val end = block(block = block, left = left, top = start, width = width)
                if (end > start) {
                    y = end
                    first = false
                }
            }
            return y
        }

        /**
         * 排一個區塊。
         *
         * @param block 區塊。
         * @param left 左界。
         * @param top 上界。
         * @param width 可用寬度。
         * @return 區塊下界；不佔空間時為 [top]。
         */
        private fun block(
            block: GuideBlock,
            left: Int,
            top: Int,
            width: Int,
        ): Int = when (block) {
            is GuideBlock.Heading -> label(text = block.text, left = left, top = top, width = width, style = GuideTextStyle.HEADING)
            is GuideBlock.Paragraph -> label(
                text = block.text,
                left = left,
                top = top,
                width = width,
                style = if (block.hint) GuideTextStyle.HINT else GuideTextStyle.BODY,
            )
            is GuideBlock.Steps -> steps(steps = block.steps, left = left, top = top, width = width)
            is GuideBlock.Scene -> scene(block = block, left = left, top = top, width = width)
            is GuideBlock.Tiles -> tiles(items = block.items, left = left, top = top, width = width)
            is GuideBlock.Section -> section(block = block, left = left, top = top, width = width)
            is GuideBlock.Card -> card(block = block, left = left, top = top, width = width)
            is GuideBlock.Button -> {
                val buttonWidth = (measurer.width(block.label) + BUTTON_PADDING).coerceAtLeast(MINIMUM_BUTTON_WIDTH).coerceAtMost(width)
                elements += GuideElement.ActionButton(label = block.label, action = block.action, x = left, y = top, width = buttonWidth)
                top + BUTTON_HEIGHT
            }
            GuideBlock.Recipe -> if (hasRecipes) {
                elements += GuideElement.Recipe(GuideRecipeLayout(left = left, top = top, width = width))
                top + GuideRecipeLayout.HEIGHT
            } else {
                top
            }
            is GuideBlock.DecisionPanel -> {
                val height = measurer.decisionPanelHeight(prompt = block.prompt, width = width)
                elements += GuideElement.DecisionPanel(prompt = block.prompt, x = left, y = top, width = width, height = height)
                top + height
            }
        }

        /**
         * 排一段換行文字。
         *
         * @param text 文字。
         * @param left 左界。
         * @param top 上界。
         * @param width 換行寬度。
         * @param style 文字樣式。
         * @param centered 是否每行置中。
         * @return 文字下界。
         */
        private fun label(
            text: Text,
            left: Int,
            top: Int,
            width: Int,
            style: GuideTextStyle,
            centered: Boolean = false,
        ): Int {
            val element = GuideElement.Label(
                text = text,
                x = left,
                y = top,
                width = width,
                lineCount = measurer.lineCount(text = text, width = width),
                style = style,
                centered = centered,
            )
            elements += element
            return top + element.height
        }

        /**
         * 排編號步驟：編號靠左，步驟與補充縮排對齊。
         *
         * @param steps 步驟。
         * @param left 左界。
         * @param top 上界。
         * @param width 可用寬度。
         * @return 最後一步的下界。
         */
        private fun steps(
            steps: List<GuideStep>,
            left: Int,
            top: Int,
            width: Int,
        ): Int {
            var y = top
            val textLeft = left + STEP_INDENT
            val textWidth = (width - STEP_INDENT).coerceAtLeast(1)
            steps.forEachIndexed { index, step ->
                if (index > 0) y += STEP_GAP
                elements += GuideElement.Label(
                    text = Text.literal("${index + 1}."),
                    x = left,
                    y = y,
                    width = STEP_INDENT,
                    lineCount = 1,
                    style = GuideTextStyle.BODY,
                )
                y = label(text = step.text, left = textLeft, top = y, width = textWidth, style = GuideTextStyle.BODY)
                step.hint?.let { hint -> y = label(text = hint, left = textLeft, top = y, width = textWidth, style = GuideTextStyle.HINT) }
            }
            return y
        }

        /**
         * 排示意場景與下方置中的說明；高度依寬度比例並限制在上下限內，再縮到場景連同說明放得進可見高度。
         *
         * @param block 場景區塊。
         * @param left 左界。
         * @param top 上界。
         * @param width 可用寬度。
         * @return 說明下界。
         */
        private fun scene(
            block: GuideBlock.Scene,
            left: Int,
            top: Int,
            width: Int,
        ): Int {
            val captionHeight = block.captions.sumOf { measurer.lineCount(text = it, width = width) } * LINE_HEIGHT
            val height = (width * block.heightRatio).roundToInt()
                .coerceIn(block.minimumHeight, block.maximumHeight)
                .coerceAtMost(viewportHeight - captionHeight)
                .coerceAtLeast(MINIMUM_SCENE_HEIGHT)
            elements += GuideElement.SceneView(scene = block.scene, x = left, y = top, width = width, height = height)
            var y = top + height
            block.captions.forEach { caption ->
                y = label(text = caption, left = left, top = y, width = width, style = GuideTextStyle.HINT, centered = true)
            }
            return y
        }

        /**
         * 排分隔段落：分隔線、標題，接著是內容。
         *
         * @param block 分隔段落。
         * @param left 左界。
         * @param top 上界。
         * @param width 可用寬度。
         * @return 內容下界。
         */
        private fun section(
            block: GuideBlock.Section,
            left: Int,
            top: Int,
            width: Int,
        ): Int {
            elements += GuideElement.Divider(x = left, y = top, width = width)
            val titleBottom = label(text = block.title, left = left, top = top + 1 + SECTION_GAP, width = width, style = GuideTextStyle.HEADING)
            return blocks(blocks = block.body, left = left, top = titleBottom + SECTION_GAP, width = width)
        }

        /**
         * 排卡片：底色包住標題與內容，四周留 [CARD_PADDING]。
         *
         * @param block 卡片。
         * @param left 左界。
         * @param top 上界。
         * @param width 可用寬度。
         * @return 卡片下界。
         */
        private fun card(
            block: GuideBlock.Card,
            left: Int,
            top: Int,
            width: Int,
        ): Int {
            val backgroundIndex = elements.size
            val innerLeft = left + CARD_PADDING
            val innerWidth = (width - CARD_PADDING * 2).coerceAtLeast(1)
            val titleBottom = label(text = block.title, left = innerLeft, top = top + CARD_PADDING, width = innerWidth, style = GuideTextStyle.BODY)
            val bodyBottom = blocks(blocks = block.body, left = innerLeft, top = titleBottom + SECTION_GAP, width = innerWidth)
            val bottom = bodyBottom + CARD_PADDING
            elements.add(backgroundIndex, GuideElement.CardBackground(x = left, y = top, width = width, height = bottom - top))
            return bottom
        }

        /**
         * 排一列牌：項目由左到右排列，放不下時換行，符號跟著後面的項目一起換行。
         * 每一行分成標籤帶、牌面帶與牌名帶，只有用到的帶才佔高度；符號與強調文字在牌面帶垂直置中。
         *
         * @param items 項目。
         * @param left 左界。
         * @param top 上界。
         * @param width 可用寬度。
         * @return 最後一行下界。
         */
        private fun tiles(
            items: List<GuideTileItem>,
            left: Int,
            top: Int,
            width: Int,
        ): Int {
            var y = top
            rows(items = items, width = width).forEachIndexed { index, row ->
                if (index > 0) y += ROW_GAP
                y = tileRow(row = row, left = left, top = y)
            }
            return y
        }

        /**
         * 把項目分成數行；符號與後一個項目視為一體，不會留在行尾。
         *
         * @param items 項目。
         * @param width 可用寬度。
         * @return 每一行的項目。
         */
        private fun rows(items: List<GuideTileItem>, width: Int): List<List<GuideTileItem>> {
            val units = mutableListOf<List<GuideTileItem>>()
            var pending = mutableListOf<GuideTileItem>()
            items.forEach { item ->
                pending += item
                if (item !is GuideTileItem.Operator) {
                    units += pending
                    pending = mutableListOf()
                }
            }
            if (pending.isNotEmpty()) units += pending
            val rows = mutableListOf<MutableList<GuideTileItem>>()
            var rowWidth = 0
            units.forEach { unit ->
                val unitWidth = unit.sumOf(::itemWidth) + (unit.size - 1) * ITEM_GAP
                val current = rows.lastOrNull()
                if (current == null || rowWidth + ITEM_GAP + unitWidth > width) {
                    rows += unit.toMutableList()
                    rowWidth = unitWidth
                } else {
                    current += unit
                    rowWidth += ITEM_GAP + unitWidth
                }
            }
            return rows
        }

        /**
         * 排一行牌列。
         *
         * @param row 這一行的項目。
         * @param left 左界。
         * @param top 上界。
         * @return 這一行的下界。
         */
        private fun tileRow(row: List<GuideTileItem>, left: Int, top: Int): Int {
            val labelBand = if (row.any { it is GuideTileItem.Group && it.label != null }) LINE_HEIGHT else 0
            val nameBand = if (row.any { it is GuideTileItem.Named }) LINE_HEIGHT else 0
            val tileTop = top + labelBand
            val centerTextY = tileTop + (TILE_HEIGHT - LINE_HEIGHT) / 2 + 1
            var x = left
            row.forEachIndexed { index, item ->
                if (index > 0) x += ITEM_GAP
                val itemWidth = itemWidth(item)
                when (item) {
                    is GuideTileItem.Group -> {
                        item.label?.let { text -> labelAt(text = text, x = x, y = top, width = itemWidth, style = GuideTextStyle.LABEL) }
                        item.tiles.forEachIndexed { tileIndex, tile ->
                            elements += GuideElement.TileFace(tile = tile, x = x + tileIndex * (TILE_WIDTH + TILE_GAP), y = tileTop)
                        }
                    }
                    is GuideTileItem.Named -> {
                        elements += GuideElement.TileFace(tile = item.tile, x = x + (itemWidth - TILE_WIDTH) / 2, y = tileTop)
                        labelAt(
                            text = measurer.tileName(item.tile),
                            x = x,
                            y = tileTop + TILE_HEIGHT + 1,
                            width = itemWidth,
                            style = GuideTextStyle.LABEL,
                            centered = true,
                        )
                    }
                    is GuideTileItem.Operator -> labelAt(text = item.symbol, x = x, y = centerTextY, width = itemWidth, style = GuideTextStyle.OPERATOR)
                    is GuideTileItem.Result -> labelAt(text = item.text, x = x, y = centerTextY, width = itemWidth, style = GuideTextStyle.ACCENT)
                }
                x += itemWidth
            }
            return tileTop + TILE_HEIGHT + nameBand
        }

        /**
         * 放一段不換行的短文字。
         *
         * @param text 文字。
         * @param x 左界。
         * @param y 上界。
         * @param width 範圍寬度。
         * @param style 文字樣式。
         * @param centered 是否在範圍內置中。
         */
        private fun labelAt(
            text: Text,
            x: Int,
            y: Int,
            width: Int,
            style: GuideTextStyle,
            centered: Boolean = false,
        ) {
            elements += GuideElement.Label(text = text, x = x, y = y, width = width, lineCount = 1, style = style, centered = centered)
        }

        /**
         * 項目的寬度。
         *
         * @param item 項目。
         * @return 寬度。
         */
        private fun itemWidth(item: GuideTileItem): Int = when (item) {
            is GuideTileItem.Group -> maxOf(
                a = item.tiles.size * TILE_WIDTH + (item.tiles.size - 1).coerceAtLeast(0) * TILE_GAP,
                b = item.label?.let(measurer::width) ?: 0,
            )
            is GuideTileItem.Named -> maxOf(TILE_WIDTH, measurer.width(measurer.tileName(item.tile)))
            is GuideTileItem.Operator -> measurer.width(item.symbol)
            is GuideTileItem.Result -> measurer.width(item.text)
        }
    }
}
