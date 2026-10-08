package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionSelectionKindDto
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.TooltipPositioner
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import org.joml.Vector2i

/**
 * 透明且不暫停遊戲的權威操作選擇介面。
 *
 * 內容由 [decisionPanelContent] 組出、版面幾何由 [DecisionCardLayout] 計算、面板由 [drawDecisionPanel] 畫出；
 * 本類別只負責建立 widget、分派滑鼠輸入、畫出按鈕、倒數與卡片說明，並把點擊意圖接回 [PlayerDecisionHudController]。
 */
internal class PlayerDecisionScreen(
    private val prompt: PlayerDecisionPromptDto,
    private val isReaction: Boolean,
    private val controller: PlayerDecisionHudController,
) : Screen(Text.translatable("mahjongcraft.hud.action_title")) {
    /** 畫面所呈現 prompt 的穩定 key，供生命週期協調器關閉過期畫面。 */
    val decisionKey: String
        get() = prompt.decisionKey

    /** 目前呈現的全部選項卡。 */
    private var visibleEntries: List<DecisionEntry> = emptyList()

    /** 跟隨橫向捲動內容移動的選項按鈕。 */
    private var cardButtons: List<ButtonWidget> = emptyList()

    /** 固定在 header 右側的跳過按鈕。 */
    private var skipButton: ButtonWidget? = null

    /** 目前操作卡片的水平捲動量。 */
    private var horizontalScroll = 0.0

    /** 是否已完成畫面開啟時的預設捲動位置；resize 造成的重建不應覆蓋玩家已手動調整的位置。 */
    private var horizontalScrollInitialized = false

    /** 是否正在拖曳 scrollbar thumb。 */
    private var draggingScrollbar = false

    /** 本次決策出現過的最大倒數寬度；只增不減，讓標題列與面板寬度不隨秒數位數變化。 */
    private var reservedTimerWidth = 0

    /** 開始拖曳時的游標與捲動位置。 */
    private var scrollbarDragStartX = 0.0
    private var scrollbarDragStartScroll = 0.0

    /** 讓多人遊戲與 integrated server 在畫面開啟時持續推進。 */
    override fun shouldPause(): Boolean = false

    /** 建立固定單列、可水平捲動的半透明選項卡。 */
    override fun init() {
        visibleEntries = panelContent().entries
        reservedTimerWidth = maxOf(reservedTimerWidth, controller.timerOverlayWidth(HEADER_TIMER_SCALE) ?: 0)
        val layout = layout()
        horizontalScroll = if (horizontalScrollInitialized) {
            horizontalScroll.coerceIn(0.0, layout.maximumScroll)
        } else {
            horizontalScrollInitialized = true
            layout.maximumScroll
        }
        val placements = layout.cardPlacements(horizontalScroll)
        cardButtons = visibleEntries.mapIndexed { index, entry ->
            val bounds = layout.cardButtonBounds(placements[index])
            ButtonWidget.builder(entry.label) { onEntryClicked(entry) }
                .dimensions(bounds.x, bounds.y, bounds.width, bounds.height)
                .build()
        }
        skipButton = if (prompt.preparation == null) {
            val bounds = layout.skipButtonBounds
            ButtonWidget.builder(Text.translatable("mahjongcraft.hud.action.skip")) { onSkipClicked() }
                .dimensions(bounds.x, bounds.y, bounds.width, bounds.height)
                .build()
        } else {
            null
        }
        refreshSubmissionState()
    }

    /** 把卡片的點擊意圖接回 controller。 */
    private fun onEntryClicked(entry: DecisionEntry) {
        when (val intent = entry.intent) {
            is DecisionEntryIntent.SubmitAction ->
                controller.submit(prompt, PlayerDecisionSelectionKindDto.ACTION, intent.token)
            is DecisionEntryIntent.BeginActionTileSelection ->
                controller.beginActionTileSelection(prompt, intent.action)
            DecisionEntryIntent.ConfirmPreparation ->
                controller.submit(prompt, PlayerDecisionSelectionKindDto.PREPARATION_CONFIRM)
            is DecisionEntryIntent.ChoosePreparationOption ->
                controller.submit(prompt, PlayerDecisionSelectionKindDto.PREPARATION_CHOICE, intent.optionId)
            DecisionEntryIntent.BeginPreparationTileSelection ->
                controller.beginPreparationTileSelection(prompt)
        }
    }

    /** 他家捨牌與搶和視窗的跳過提交正式 Pass，自己回合的跳過改為進入普通實體出牌模式。 */
    private fun onSkipClicked() {
        val pass = prompt.actions.firstOrNull { it.actionId == PASS_ACTION_ID }
        if (isReaction && pass != null) {
            controller.submit(prompt, PlayerDecisionSelectionKindDto.ACTION, pass.token)
        } else {
            controller.beginDirectDiscard(prompt)
        }
    }

    /** 最終提交送出或遭拒後，立即同步所有操作按鈕的可用狀態。 */
    fun refreshSubmissionState() {
        val active = !controller.isSubmissionPending(prompt.decisionKey)
        cardButtons.forEach { it.active = active }
        skipButton?.active = active
    }

    /** Esc 只暫時收起，不提交 Pass。 */
    override fun close() {
        controller.dismiss(prompt.decisionKey)
        client?.setScreen(null)
    }

    /** 操作區內的滾輪一律轉為水平捲動。 */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val layout = layout()
        if (
            layout.hasOverflow &&
            mouseX in layout.viewportLeft.toDouble()..layout.viewportRight.toDouble() &&
            mouseY in layout.panelTop.toDouble()..layout.panelBottom.toDouble()
        ) {
            horizontalScroll = layout.scrollFromWheel(horizontalScroll, amount)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    /** Scrollbar thumb 可直接點擊或開始拖曳；卡片按鈕只在裁切 viewport 內接收輸入。 */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val layout = layout()
        if (
            layout.hasOverflow &&
            button == 0 &&
            mouseX in layout.viewportLeft.toDouble()..layout.viewportRight.toDouble() &&
            mouseY in layout.scrollbarTop.toDouble()..(layout.scrollbarTop + DecisionCardLayout.SCROLLBAR_HEIGHT).toDouble()
        ) {
            val thumb = layout.scrollbarThumb(horizontalScroll)
            if (mouseX !in thumb.left.toDouble()..thumb.right.toDouble()) {
                horizontalScroll = layout.scrollFromThumbLeft(mouseX - thumb.width / 2.0, horizontalScroll)
            }
            draggingScrollbar = true
            scrollbarDragStartX = mouseX
            scrollbarDragStartScroll = horizontalScroll
            return true
        }
        if (skipButton?.mouseClicked(mouseX, mouseY, button) == true) return true
        if (
            mouseX in layout.viewportLeft.toDouble()..layout.viewportRight.toDouble() &&
            mouseY in layout.cardTop.toDouble()..layout.cardBottom.toDouble()
        ) {
            return cardButtons.any { it.mouseClicked(mouseX, mouseY, button) }
        }
        return false
    }

    /** 拖曳 thumb 時依 track 的可移動比例更新內容 offset。 */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (draggingScrollbar && button == 0) {
            horizontalScroll = layout().scrollFromDrag(scrollbarDragStartScroll, mouseX - scrollbarDragStartX)
            return true
        }
        return false
    }

    /** 放開左鍵後結束 scrollbar 拖曳。 */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false
            return true
        }
        skipButton?.mouseReleased(mouseX, mouseY, button)
        cardButtons.forEach { it.mouseReleased(mouseX, mouseY, button) }
        return false
    }

    /** 繪製操作面板、按鈕、倒數與卡片說明。 */
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        reservedTimerWidth = maxOf(reservedTimerWidth, controller.timerOverlayWidth(HEADER_TIMER_SCALE) ?: 0)
        val content = panelContent()
        val layout = layout(content)
        val placements = layout.cardPlacements(horizontalScroll)
        updateCardButtonPositions(layout, placements)
        context.drawDecisionPanel(
            renderer = textRenderer,
            layout = layout,
            content = content,
            placements = placements,
            scroll = horizontalScroll,
            mouseX = mouseX,
            mouseY = mouseY,
            drawTile = { assetKey, x, y -> drawTile(context, assetKey, x, y) },
        ) {
            cardButtons.forEach { it.render(context, mouseX, mouseY, delta) }
        }
        skipButton?.render(context, mouseX, mouseY, delta)
        controller.timerOverlayWidth(HEADER_TIMER_SCALE)?.let { timerWidth ->
            controller.renderTimerOverlay(
                context = context,
                y = layout.headerTextTop(textRenderer.fontHeight),
                centerX = layout.timerLeft + timerWidth / 2,
                scale = HEADER_TIMER_SCALE,
            )
        }
        renderDescriptionTooltip(context, layout, placements, mouseX, mouseY)
    }

    /** 游標停在有說明的卡片上時，在面板外緣對齊該卡片顯示說明，不覆蓋卡片列。 */
    private fun renderDescriptionTooltip(
        context: DrawContext,
        layout: DecisionCardLayout,
        placements: List<DecisionBounds>,
        mouseX: Int,
        mouseY: Int,
    ) {
        if (mouseX !in layout.viewportLeft until layout.viewportRight) return
        val index = placements.indexOfFirst { placement ->
            mouseX in placement.x until placement.x + placement.width && mouseY in placement.y until placement.y + placement.height
        }
        if (index < 0) return
        val description = visibleEntries[index].description ?: return
        val card = placements[index]
        val positioner = TooltipPositioner { _, _, _, _, width, height ->
            val position = layout.descriptionTooltipPosition(card, width, height)
            Vector2i(position.x, position.y)
        }
        context.drawTooltip(textRenderer, textRenderer.wrapLines(description, DecisionCardLayout.TOOLTIP_MAX_WIDTH), positioner, mouseX, mouseY)
    }

    /** 目前畫面寬度下的面板內容；觸發文字依畫面寬度換行。 */
    private fun panelContent(): DecisionPanelContent = decisionPanelContent(
        prompt = prompt,
        texts = controller.decisionTexts,
        resolvePlayerName = controller::resolveTriggerPlayerName,
        renderer = textRenderer,
        screenWidth = width,
    )

    /** 目前畫面尺寸、玩家設定與實際文字量測結果下的版面幾何。 */
    private fun layout(content: DecisionPanelContent = panelContent()): DecisionCardLayout = decisionPanelLayout(
        content = content,
        renderer = textRenderer,
        screenWidth = width,
        screenHeight = height,
        panelRatioY = controller.hudLayout().decisionPanelY,
        timerWidth = reservedTimerWidth,
    )

    /** 使用完整牌面 UV 等比例縮放預覽牌；卡片預覽一律直立，不套用鳴牌後最終桌面朝向。 */
    private fun drawTile(context: DrawContext, assetKey: String, x: Int, y: Int) {
        controller.renderTileFace(
            context,
            assetKey,
            x,
            y,
            DecisionCardLayout.PREVIEW_TILE_WIDTH,
            DecisionCardLayout.PREVIEW_TILE_HEIGHT,
        )
    }

    /** 每幀同步因拖曳／滾輪移動後的原版按鈕座標。 */
    private fun updateCardButtonPositions(layout: DecisionCardLayout, placements: List<DecisionBounds>) {
        cardButtons.forEachIndexed { index, button ->
            val bounds = layout.cardButtonBounds(placements[index])
            button.x = bounds.x
            button.y = bounds.y
        }
    }
}
