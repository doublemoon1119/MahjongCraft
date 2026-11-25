package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar

/**
 * 牌譜回放畫面的版面。
 *
 * - 寬視窗：左邊是場景與下緣的交易說明，右欄上方是視角與手牌選擇器、下方是交易列表。
 *   場景下方是進度條與播放控制列，最底下是四格按鈕。
 * - 窄視窗：場景佔滿寬度，選擇器疊在場景左上角，交易列表由標題列旁的按鈕叫出、疊在場景右側。
 * - 全螢幕：場景佔滿整個視窗，進度條與播放控制列疊在場景底部，交易說明與其他控制項不顯示。
 *
 * @property fullscreen 是否為全螢幕版面。
 * @property wide 是否為寬視窗版面。
 * @property scene 場景範圍。
 * @property caption 目前交易說明。
 * @property viewSelector 視角選擇器。
 * @property handSelector 手牌選擇器。
 * @property visibilityButton 開啟可見性子頁的按鈕。
 * @property selectorLabelWidth 選擇器左側標籤的寬度。
 * @property list 交易列表範圍，不含捲軸。窄視窗為疊在場景上的範圍。
 * @property listScrollbarX 交易列表捲軸的左界。
 * @property listToggle 窄視窗叫出交易列表的按鈕。寬視窗為 null。
 * @property progress 進度條範圍。
 * @property controls 播放控制列的範圍。
 * @property footerTop 底部四格按鈕的上界。
 * @property speedSelectorWidth 速度選擇器的寬度。
 * @property fullscreenToggle 場景右上角切換全螢幕的按鈕。
 * @property controlBar 全螢幕時疊在場景底部、包住進度條與播放控制列的範圍。一般版面為 null。
 */
internal data class HistoryReplayLayout(
    val fullscreen: Boolean,
    val wide: Boolean,
    val scene: HistoryScreenLayout.Bounds,
    val caption: HistoryScreenLayout.Bounds,
    val viewSelector: HistoryScreenLayout.Bounds,
    val handSelector: HistoryScreenLayout.Bounds,
    val visibilityButton: HistoryScreenLayout.Bounds,
    val selectorLabelWidth: Int,
    val list: HistoryScreenLayout.Bounds,
    val listScrollbarX: Int,
    val listToggle: HistoryScreenLayout.Bounds?,
    val progress: HistoryScreenLayout.Bounds,
    val controls: HistoryScreenLayout.Bounds,
    val footerTop: Int,
    val speedSelectorWidth: Int,
    val fullscreenToggle: HistoryScreenLayout.Bounds,
    val controlBar: HistoryScreenLayout.Bounds?,
) {
    /**
     * 播放控制列中一顆按鈕的範圍。
     *
     * @param index 由左至右的第幾顆，0 到 4：第一筆、上一筆、播放、下一筆、最後一筆。
     * @return 按鈕範圍。
     */
    fun controlButton(index: Int): HistoryScreenLayout.Bounds {
        val x = controls.x + (0 until index).sumOf { controlButtonWidth(it) + CONTROL_GAP }
        return HistoryScreenLayout.Bounds(x = x, y = controls.y, width = controlButtonWidth(index), height = controls.height)
    }

    /** 播放控制按鈕右側、計數文字的左界。 */
    val counterX: Int
        get() = controlButton(CONTROL_BUTTONS - 1).right + COUNTER_GAP

    /**
     * 速度選擇器的範圍，靠右對齊。
     *
     * @return 選擇器範圍。
     */
    fun speedSelector(): HistoryScreenLayout.Bounds = HistoryScreenLayout.Bounds(
        x = controls.right - speedSelectorWidth,
        y = controls.y,
        width = speedSelectorWidth,
        height = controls.height,
    )

    /**
     * 一顆播放控制按鈕的寬度。播放鍵較寬。
     *
     * @param index 由左至右的第幾顆。
     * @return 寬度。
     */
    private fun controlButtonWidth(index: Int): Int = if (index == PLAY_BUTTON_INDEX) PLAY_BUTTON_WIDTH else FormScreenLayout.BUTTON_HEIGHT

    /** 版面尺寸。 */
    companion object {
        /** 寬視窗版面的最小寬度。 */
        const val WIDE_MIN_WIDTH: Int = 420

        /** 播放控制按鈕數。 */
        const val CONTROL_BUTTONS: Int = 5

        /** 播放鍵在控制列中的位置。 */
        const val PLAY_BUTTON_INDEX: Int = 2

        /** 播放鍵的寬度。 */
        const val PLAY_BUTTON_WIDTH: Int = 26

        /** 控制按鈕之間的距離。 */
        const val CONTROL_GAP: Int = 2

        /** 控制按鈕與計數文字的距離。 */
        const val COUNTER_GAP: Int = 6

        /** 交易說明的高度。 */
        const val CAPTION_HEIGHT: Int = 14

        /** 進度條的高度，包含標記的高度。 */
        const val PROGRESS_HEIGHT: Int = 8

        /** 各區塊之間的距離。 */
        const val GAP: Int = 4

        /** 寬視窗右欄寬度佔內容寬度的比例。 */
        const val SIDE_RATIO: Double = 0.32

        /** 寬視窗右欄上方、交易列表之前的控制項列數：視角、手牌、可見性。 */
        const val SIDE_CONTROL_ROWS: Int = 3

        /** 寬視窗右欄的最小寬度。 */
        const val SIDE_MIN_WIDTH: Int = 130

        /** 寬視窗右欄的最大寬度。 */
        const val SIDE_MAX_WIDTH: Int = 200

        /** 窄視窗疊在場景上的選擇器高度。 */
        const val COMPACT_SELECTOR_HEIGHT: Int = 16

        /** 窄視窗疊在場景上的選擇器寬度。 */
        const val COMPACT_SELECTOR_WIDTH: Int = 76

        /** 窄視窗交易列表的最大寬度。 */
        const val COMPACT_LIST_MAX_WIDTH: Int = 160

        /** 窄視窗叫出交易列表按鈕的寬度。 */
        const val LIST_TOGGLE_WIDTH: Int = 64

        /** 全螢幕切換按鈕的邊長。 */
        const val FULLSCREEN_TOGGLE_SIZE: Int = 16

        /**
         * 場景右上角全螢幕切換按鈕的位置。
         *
         * @param scene 場景範圍。
         * @return 按鈕範圍。
         */
        private fun fullscreenToggle(scene: HistoryScreenLayout.Bounds): HistoryScreenLayout.Bounds = HistoryScreenLayout.Bounds(
            x = scene.right - GAP - FULLSCREEN_TOGGLE_SIZE,
            y = scene.y + GAP,
            width = FULLSCREEN_TOGGLE_SIZE,
            height = FULLSCREEN_TOGGLE_SIZE,
        )

        /**
         * 全螢幕版面：場景佔滿整個視窗，進度條與播放控制列疊在場景底部。不顯示交易說明。
         *
         * @param width 視窗寬度。
         * @param height 視窗高度。
         * @param speedSelectorWidth 速度選擇器完整顯示最寬選項所需的寬度。
         * @return 版面。
         */
        fun fullscreen(width: Int, height: Int, speedSelectorWidth: Int): HistoryReplayLayout {
            val margin = FormScreenLayout.REGULAR_MARGIN
            val left = margin
            val right = (width - margin).coerceAtLeast(left)
            val controlsY = (height - margin - FormScreenLayout.BUTTON_HEIGHT).coerceAtLeast(0)
            val progress = HistoryScreenLayout.Bounds(x = left, y = (controlsY - GAP - PROGRESS_HEIGHT).coerceAtLeast(0), width = right - left, height = PROGRESS_HEIGHT)
            val scene = HistoryScreenLayout.Bounds(x = 0, y = 0, width = width, height = height)
            val hidden = HistoryScreenLayout.Bounds(x = 0, y = 0, width = 0, height = 0)
            val barTop = (progress.y - GAP).coerceAtLeast(0)
            return HistoryReplayLayout(
                fullscreen = true,
                wide = false,
                scene = scene,
                caption = hidden,
                viewSelector = hidden,
                handSelector = hidden,
                visibilityButton = hidden,
                selectorLabelWidth = 0,
                list = hidden,
                listScrollbarX = 0,
                listToggle = null,
                progress = progress,
                controls = HistoryScreenLayout.Bounds(x = left, y = controlsY, width = right - left, height = FormScreenLayout.BUTTON_HEIGHT),
                footerTop = height,
                speedSelectorWidth = speedSelectorWidth,
                fullscreenToggle = fullscreenToggle(scene),
                controlBar = HistoryScreenLayout.Bounds(x = 0, y = barTop, width = width, height = height - barTop),
            )
        }

        /**
         * 依視窗大小排出版面。
         *
         * @param width 視窗寬度。
         * @param height 視窗高度。
         * @param labelWidth 選擇器標籤文字的最大寬度。
         * @param speedSelectorWidth 速度選擇器完整顯示最寬選項所需的寬度。
         * @return 版面。
         */
        fun measure(width: Int, height: Int, labelWidth: Int, speedSelectorWidth: Int): HistoryReplayLayout {
            val frame = HistoryPageFrame(windowWidth = width, windowHeight = height)
            val left = frame.contentLeft
            val right = frame.contentRight
            val top = FormScreenLayout.TITLE_BOTTOM
            val controlsY = (frame.footerTop - GAP * 2 - FormScreenLayout.BUTTON_HEIGHT).coerceAtLeast(top)
            val controls = HistoryScreenLayout.Bounds(x = left, y = controlsY, width = right - left, height = FormScreenLayout.BUTTON_HEIGHT)
            val progress = HistoryScreenLayout.Bounds(x = left, y = (controlsY - GAP - PROGRESS_HEIGHT).coerceAtLeast(top), width = right - left, height = PROGRESS_HEIGHT)
            val captionY = (progress.y - GAP - CAPTION_HEIGHT).coerceAtLeast(top)
            val wide = width >= WIDE_MIN_WIDTH
            val labelSpace = labelWidth + GAP
            return if (wide) {
                val sideWidth = ((right - left) * SIDE_RATIO).toInt().coerceIn(SIDE_MIN_WIDTH, SIDE_MAX_WIDTH)
                val sideLeft = right - sideWidth
                val sceneRight = sideLeft - GAP * 2
                val listTop = top + (FormScreenLayout.BUTTON_HEIGHT + GAP) * SIDE_CONTROL_ROWS
                val scrollbarX = right - VerticalScrollbar.WIDTH
                val scene = HistoryScreenLayout.Bounds(x = left, y = top, width = sceneRight - left, height = (captionY - top).coerceAtLeast(0))
                HistoryReplayLayout(
                    fullscreen = false,
                    wide = true,
                    scene = scene,
                    caption = HistoryScreenLayout.Bounds(x = left, y = captionY, width = sceneRight - left, height = CAPTION_HEIGHT),
                    viewSelector = HistoryScreenLayout.Bounds(x = sideLeft + labelSpace, y = top, width = sideWidth - labelSpace, height = FormScreenLayout.BUTTON_HEIGHT),
                    handSelector = HistoryScreenLayout.Bounds(
                        x = sideLeft + labelSpace,
                        y = top + FormScreenLayout.BUTTON_HEIGHT + GAP,
                        width = sideWidth - labelSpace,
                        height = FormScreenLayout.BUTTON_HEIGHT,
                    ),
                    visibilityButton = HistoryScreenLayout.Bounds(
                        x = sideLeft,
                        y = top + (FormScreenLayout.BUTTON_HEIGHT + GAP) * 2,
                        width = sideWidth,
                        height = FormScreenLayout.BUTTON_HEIGHT,
                    ),
                    selectorLabelWidth = labelWidth,
                    list = HistoryScreenLayout.Bounds(
                        x = sideLeft,
                        y = listTop,
                        width = (scrollbarX - GAP - sideLeft).coerceAtLeast(1),
                        height = (captionY + CAPTION_HEIGHT - listTop).coerceAtLeast(0),
                    ),
                    listScrollbarX = scrollbarX,
                    listToggle = null,
                    progress = progress,
                    controls = controls,
                    footerTop = frame.footerTop,
                    speedSelectorWidth = speedSelectorWidth,
                    fullscreenToggle = fullscreenToggle(scene),
                    controlBar = null,
                )
            } else {
                val sceneWidth = right - left
                val sceneHeight = (captionY - top).coerceAtLeast(0)
                val listWidth = (sceneWidth / 2).coerceAtMost(COMPACT_LIST_MAX_WIDTH)
                val scrollbarX = right - GAP - VerticalScrollbar.WIDTH
                val scene = HistoryScreenLayout.Bounds(x = left, y = top, width = sceneWidth, height = sceneHeight)
                HistoryReplayLayout(
                    fullscreen = false,
                    wide = false,
                    scene = scene,
                    caption = HistoryScreenLayout.Bounds(x = left, y = captionY, width = sceneWidth, height = CAPTION_HEIGHT),
                    viewSelector = HistoryScreenLayout.Bounds(x = left + GAP + labelSpace, y = top + GAP, width = COMPACT_SELECTOR_WIDTH, height = COMPACT_SELECTOR_HEIGHT),
                    handSelector = HistoryScreenLayout.Bounds(
                        x = left + GAP + labelSpace,
                        y = top + GAP * 2 + COMPACT_SELECTOR_HEIGHT,
                        width = COMPACT_SELECTOR_WIDTH,
                        height = COMPACT_SELECTOR_HEIGHT,
                    ),
                    visibilityButton = HistoryScreenLayout.Bounds(
                        x = left + GAP,
                        y = top + GAP * 3 + COMPACT_SELECTOR_HEIGHT * 2,
                        width = labelSpace + COMPACT_SELECTOR_WIDTH,
                        height = COMPACT_SELECTOR_HEIGHT,
                    ),
                    selectorLabelWidth = labelWidth,
                    list = HistoryScreenLayout.Bounds(
                        x = right - listWidth + GAP,
                        // 列表從場景右上角的全螢幕按鈕下方開始。
                        y = top + GAP * 2 + FULLSCREEN_TOGGLE_SIZE,
                        width = (scrollbarX - GAP - (right - listWidth + GAP)).coerceAtLeast(1),
                        height = (sceneHeight - GAP * 3 - FULLSCREEN_TOGGLE_SIZE).coerceAtLeast(0),
                    ),
                    listScrollbarX = scrollbarX,
                    listToggle = HistoryScreenLayout.Bounds(x = right - LIST_TOGGLE_WIDTH, y = FormScreenLayout.TITLE_Y - GAP, width = LIST_TOGGLE_WIDTH, height = COMPACT_SELECTOR_HEIGHT),
                    progress = progress,
                    controls = controls,
                    footerTop = frame.footerTop,
                    speedSelectorWidth = speedSelectorWidth,
                    fullscreenToggle = fullscreenToggle(scene),
                    controlBar = null,
                )
            }
        }
    }
}
