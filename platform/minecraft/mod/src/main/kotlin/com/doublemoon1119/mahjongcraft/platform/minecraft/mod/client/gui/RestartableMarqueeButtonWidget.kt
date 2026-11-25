package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.fitClientText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.network.chat.Component
import kotlin.time.Duration

/**
 * 文字過寬時捲動，並在游標每次重新進入後從頭播放的按鈕。
 *
 * 提示框超出畫面上下緣時推回畫面內。比畫面還高時上下捲動，游標重新進入或重新取得焦點後從頂端開始。
 *
 * 可另外設定五種外觀與操作：標示選中狀態的白色外框、展開清單用的選擇器箭頭、取代文字的像素圖示、不繪製文字
 * （文字只供旁白），以及讓右鍵也觸發動作（供循環切換按鈕往回選）。
 *
 * @param x 按鈕左界。
 * @param y 按鈕上界。
 * @param width 按鈕寬度。
 * @param height 按鈕高度。
 * @param message 按鈕文字。
 * @param onPress 按下按鈕時執行的動作。
 */
internal class RestartableMarqueeButtonWidget private constructor(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
    onPress: OnPress,
) : MahjongButton(x, y, width, height, message, onPress, DEFAULT_NARRATION) {
    /** 是否以白色外框標示選中狀態。 */
    var outlined: Boolean = false

    /** 作為選擇器時回報清單是否展開。不是選擇器時為 null。展開時顯示向上箭頭與白色外框。 */
    var selectorExpanded: (() -> Boolean)? = null

    /** 捲動內容中額外限制滑鼠可點擊的區域。 */
    var clickClip: ((Double, Double) -> Boolean)? = null

    /** 右鍵點擊是否也觸發按鈕動作。 */
    var pressesOnSecondaryClick: Boolean = false

    /** 取代按鈕文字置中繪製的圖示。設定後按鈕文字只供旁白使用。沒有圖示時為 null。 */
    var icon: PixelIcon? = null

    /** 是否在按鈕上繪製文字。關閉時按鈕文字只供旁白使用，例如沒有勾選時是空方塊的勾選方塊。 */
    var showsMessage: Boolean = true

    /** 上一幀游標是否位於按鈕上，用於偵測重新進入。 */
    private var hoveredLastFrame = false

    /** 本次游標進入按鈕的單調時鐘時間。 */
    private var hoverStartedAt = Duration.ZERO

    /** 上一幀是否處於選取狀態，用於偵測重新取得焦點。 */
    private var selectedLastFrame = false

    /** 本次提示框播放起點的單調時鐘時間。 */
    private var tooltipStartedAt = Duration.ZERO

    override fun renderContents(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val originalMessage = message
        try {
            message = Component.empty()
            super.renderContents(context, mouseX, mouseY, delta)
        } finally {
            message = originalMessage
        }

        val hoveredNow = isHovered()
        val selectedNow = isHoveredOrFocused
        if (hoveredNow && !hoveredLastFrame) hoverStartedAt = measuringTimeNow()
        if ((hoveredNow && !hoveredLastFrame) || (selectedNow && !selectedLastFrame)) tooltipStartedAt = measuringTimeNow()
        hoveredLastFrame = hoveredNow
        selectedLastFrame = selectedNow
        val currentIcon = icon
        if (currentIcon == null) {
            if (showsMessage) renderMessage(context, originalMessage, hoveredNow)
        } else {
            PixelIcons.icon(
                context = context,
                icon = currentIcon,
                x = x + (width - currentIcon.width) / 2,
                y = y + (height - currentIcon.height) / 2,
                color = if (active) 0xFFFFFF else 0xA0A0A0,
            )
        }
        val expanded = selectorExpanded?.invoke()
        if (expanded != null) renderSelectorArrow(context, expanded)
        if (outlined || expanded == true) context.renderOutline(x, y, width, height, OUTLINE_COLOR)
    }

    /**
     * 在按鈕右端繪製選擇器箭頭。
     *
     * @param context 本幀繪製環境。
     * @param expanded 清單是否展開。展開時箭頭向上。
     */
    private fun renderSelectorArrow(context: GuiGraphics, expanded: Boolean) {
        PixelIcons.arrow(
            context = context,
            x = x + width - SELECTOR_ARROW_RIGHT_INSET - PixelIcons.ARROW_WIDTH,
            y = y + (height - PixelIcons.ARROW_HEIGHT) / 2,
            pointsUp = expanded,
            color = if (active) 0xFFFFFF else 0xA0A0A0,
        )
    }

    override fun clicked(mouseX: Double, mouseY: Double): Boolean = clickClip?.invoke(mouseX, mouseY) != false && super.clicked(mouseX, mouseY)

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != SECONDARY_MOUSE_BUTTON || !pressesOnSecondaryClick) return super.mouseClicked(mouseX, mouseY, button)
        if (!active || !visible || !clicked(mouseX, mouseY)) return false
        playDownSound(Minecraft.getInstance().soundManager)
        CycleButtonInput.duringSecondaryClick { onPress() }
        return true
    }

    /**
     * 繪製按鈕文字。
     *
     * - 文字過寬時截斷並加上省略號。
     * - 游標停留期間改為播放跑馬燈。
     *
     * @param context 本幀繪製環境。
     * @param text 欲繪製的文字。
     * @param hovered 游標是否位於按鈕上。
     */
    private fun renderMessage(context: GuiGraphics, text: Component, hovered: Boolean) {
        val textRenderer = Minecraft.getInstance().font
        val padding = if (selectorExpanded == null) HORIZONTAL_PADDING else SELECTOR_TEXT_PADDING
        val availableWidth = (width - padding * 2).coerceAtLeast(1)
        val textWidth = textRenderer.width(text)
        val textY = y + (height - VANILLA_VISIBLE_TEXT_HEIGHT) / 2
        val color = if (active) 0xFFFFFF else 0xA0A0A0
        if (textWidth <= availableWidth) {
            context.drawString(textRenderer, text, x + (width - textWidth) / 2, textY, color)
            return
        }
        if (!hovered) {
            val fitted = fitClientText(textRenderer, text, availableWidth)
            context.drawString(textRenderer, fitted, x + (width - textRenderer.width(fitted)) / 2, textY, color)
            return
        }

        val overflow = textWidth - availableWidth
        val elapsed = if (hovered) (measuringTimeNow() - hoverStartedAt).coerceAtLeast(Duration.ZERO) else Duration.ZERO
        val scrollX = MarqueeTiming.offset(elapsed, overflow)
        context.enableScissor(x + padding, y, x + width - padding, y + height)
        context.drawString(textRenderer, text, x + padding - scrollX, textY, color)
        context.disableScissor()
    }

    override fun adjustTooltipPositioner(base: ClientTooltipPositioner): ClientTooltipPositioner = ScreenFittingTooltipPositioner(
        base = base,
        shownSince = tooltipStartedAt,
    )

    /**
     * 設定按鈕的位置與尺寸。預設位於原點，寬 150、高 20。
     *
     * @property message 按鈕文字。
     * @property onPress 按下按鈕時執行的動作。
     */
    internal class Builder(
        private val message: Component,
        private val onPress: OnPress,
    ) {
        /** 按鈕左界。 */
        private var x = 0

        /** 按鈕上界。 */
        private var y = 0

        /** 按鈕寬度。 */
        private var width = 150

        /** 按鈕高度。 */
        private var height = 20

        /** 右鍵點擊是否也觸發按鈕動作。 */
        private var pressesOnSecondaryClick = false

        /**
         * 讓右鍵點擊也觸發按鈕動作。用於循環切換按鈕（右鍵選擇上一項）與開關。
         *
         * @return 此建構器，供串接設定。
         */
        fun pressesOnSecondaryClick(): Builder = apply { pressesOnSecondaryClick = true }

        /**
         * 設定按鈕矩形範圍。
         *
         * @param x 左界。
         * @param y 上界。
         * @param width 寬度。
         * @param height 高度。
         * @return 此建構器，供串接設定。
         */
        fun dimensions(x: Int, y: Int, width: Int, height: Int): Builder = apply {
            this.x = x
            this.y = y
            this.width = width
            this.height = height
        }

        /** 依目前設定建立新的按鈕。 */
        fun build(): RestartableMarqueeButtonWidget = RestartableMarqueeButtonWidget(x, y, width, height, message, onPress).also {
            it.pressesOnSecondaryClick = pressesOnSecondaryClick
        }
    }

    /** 按鈕的繪製尺寸與建構入口。 */
    companion object {
        /** 文字左右預留的像素數。 */
        private const val HORIZONTAL_PADDING = 4

        /** 用於垂直置中文字的可見字高。 */
        private const val VANILLA_VISIBLE_TEXT_HEIGHT = 8

        /** 選擇器文字左右預留的像素數，讓文字不與右端的箭頭重疊並維持置中。 */
        private const val SELECTOR_TEXT_PADDING = 20

        /** 選擇器箭頭右緣與按鈕右界的距離。 */
        private const val SELECTOR_ARROW_RIGHT_INSET = 8

        /** 選中狀態與展開中選擇器的外框顏色。 */
        private const val OUTLINE_COLOR = 0xFFFFFFFF.toInt()

        /** 滑鼠右鍵的按鍵代碼。 */
        private const val SECONDARY_MOUSE_BUTTON = 1

        /**
         * 選擇器完整顯示一段文字所需的寬度，包含兩側避開箭頭的留白。
         *
         * @param textWidth 文字寬度。
         * @return 按鈕寬度。
         */
        fun selectorWidth(textWidth: Int): Int = textWidth + SELECTOR_TEXT_PADDING * 2

        /**
         * 一般按鈕完整顯示一段文字所需的寬度，包含兩側留白。
         *
         * @param textWidth 文字寬度。
         * @return 按鈕寬度。
         */
        fun buttonWidth(textWidth: Int): Int = textWidth + HORIZONTAL_PADDING * 2

        /**
         * 建立按鈕建構器。
         *
         * @param message 按鈕文字。
         * @param onPress 按下按鈕時執行的動作。
         * @return 使用預設位置與尺寸的建構器。
         */
        fun builder(message: Component, onPress: OnPress): Builder = Builder(message, onPress)
    }
}
