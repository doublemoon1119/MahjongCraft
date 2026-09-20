package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionPlayerRelationDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionSelectionKindDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ClaimedTileMarker
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import java.util.UUID

/**
 * 透明且不暫停遊戲的權威操作選擇介面。
 *
 * 版面幾何全部由 [DecisionCardLayout] 計算，卡片清單由 [decisionEntriesFrom] 組出；本類別只負責建立
 * widget、分派滑鼠輸入、把結果畫到 [DrawContext]，並把點擊意圖接回 [PlayerDecisionHudController]。
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

    /** 開始拖曳時的游標與捲動位置。 */
    private var scrollbarDragStartX = 0.0
    private var scrollbarDragStartScroll = 0.0

    /** 讓多人遊戲與 integrated server 在畫面開啟時持續推進。 */
    override fun shouldPause(): Boolean = false

    /** 建立固定單列、可水平捲動的半透明選項卡。 */
    override fun init() {
        visibleEntries = decisionEntriesFrom(controller.decisionTexts, prompt)
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

    /** 他家捨牌與搶槓視窗的跳過提交正式 Pass，自己回合的跳過改為進入普通實體出牌模式。 */
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

    /** 繪製觸發牌、完整副露預覽與半透明深色選項面板。 */
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val layout = layout()
        context.fill(layout.panelLeft, layout.panelTop, layout.panelRight, layout.panelBottom, PANEL_BACKGROUND)
        context.drawCenteredTextWithShadow(
            textRenderer,
            title,
            width / 2,
            layout.headerTextTop(textRenderer.fontHeight),
            HEADER_TEXT_COLOR,
        )
        renderTriggerPanel(context, layout)
        val placements = layout.cardPlacements(horizontalScroll)
        updateCardButtonPositions(layout, placements)
        context.enableScissor(layout.viewportLeft, layout.cardTop, layout.viewportRight, layout.cardBottom)
        visibleEntries.forEachIndexed { index, entry ->
            renderCard(context, layout, entry, placements[index], mouseX, mouseY)
        }
        cardButtons.forEach { it.render(context, mouseX, mouseY, delta) }
        context.disableScissor()
        skipButton?.render(context, mouseX, mouseY, delta)
        if (layout.hasOverflow) renderScrollbar(context, layout)
        controller.renderTimerOverlay(context, layout.timerTop)
    }

    /** 繪製單一卡片的背景、預覽牌與鳴牌指標。 */
    private fun renderCard(
        context: DrawContext,
        layout: DecisionCardLayout,
        entry: DecisionEntry,
        placement: DecisionBounds,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = mouseX in placement.x until placement.x + placement.width &&
            mouseY in placement.y until placement.y + placement.height
        context.fill(
            placement.x,
            placement.y,
            placement.x + placement.width,
            placement.y + placement.height,
            if (hovered) CARD_HOVER_BACKGROUND else CARD_BACKGROUND,
        )
        val tiles = entry.previewTileAssetKeys
        layout.previewTilePlacements(placement, tiles.size).forEachIndexed { index, tile ->
            drawTile(context, tiles[index], tile.x, tile.y)
            if (index == entry.claimedTileIndex) {
                drawClaimedTileMarker(context, tile.x + tile.width / 2, layout.claimedTileMarkerTop(tile))
            }
        }
    }

    /** 完整顯示來源玩家、相對位置與動作；寬度不足時換行，不截斷資訊。 */
    private fun renderTriggerPanel(context: DrawContext, layout: DecisionCardLayout) {
        val assetKey = prompt.triggerTileAssetKey ?: return
        val panel = layout.triggerPanelBounds
        context.fill(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, PANEL_BACKGROUND)
        triggerTextLines().forEachIndexed { index, line ->
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, layout.triggerTextLineTop(index), 0xFFFFFF)
        }
        val tile = layout.triggerTileBounds
        drawTile(context, assetKey, tile.x, tile.y)
    }

    /** 以 client player list 解析來源名稱，並使用完整本地化句型；自己回合摸牌沒有來源玩家，改顯示專用句型。 */
    private fun triggerText(): Text? {
        val playerId = prompt.triggerPlayerId
            ?: return if (prompt.triggerTileAssetKey != null) Text.translatable("mahjongcraft.hud.trigger.self_draw") else null
        val uuid = runCatching { UUID.fromString(playerId) }.getOrNull()
        val playerName = prompt.triggerPlayerName
            ?: uuid?.let { MinecraftClient.getInstance().networkHandler?.getPlayerListEntry(it)?.profile?.name }
            ?: playerId.take(8)
        val relationKey = when (prompt.triggerPlayerRelation) {
            DecisionPlayerRelationDto.LEFT -> "mahjongcraft.hud.relation.left"
            DecisionPlayerRelationDto.ACROSS -> "mahjongcraft.hud.relation.across"
            DecisionPlayerRelationDto.RIGHT -> "mahjongcraft.hud.relation.right"
            null -> return null
        }
        val action = controller.decisionTexts.actionLabel(prompt.ruleModuleId, prompt.triggerActionId ?: BuiltInGameActionIds.DISCARD)
        return Text.translatable("mahjongcraft.hud.trigger", playerName, Text.translatable(relationKey), action)
    }

    /** 觸發文字依畫面寬度換行後的每一行。 */
    private fun triggerTextLines() = triggerText()
        ?.let { textRenderer.wrapLines(it, DecisionCardLayout.triggerTextMaximumWidth(width)) }
        .orEmpty()

    /** 目前畫面尺寸、玩家設定與實際文字量測結果下的版面幾何。 */
    private fun layout(): DecisionCardLayout {
        val lines = triggerTextLines()
        return DecisionCardLayout(
            screenWidth = width,
            screenHeight = height,
            cards = visibleEntries.map(DecisionEntry::layoutCard),
            headerTextWidth = textRenderer.getWidth(title),
            triggerLineCount = lines.size,
            triggerTextWidth = lines.maxOfOrNull(textRenderer::getWidth) ?: 0,
            hasTriggerTile = !prompt.triggerTileAssetKey.isNullOrEmpty(),
            panelRatioY = controller.hudLayout().decisionPanelY,
        )
    }

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

    /**
     * 在 [centerX], [top] 位置畫一個寬扁的倒三角形指標，逐列縮減寬度來模擬三角形，
     * 不依賴字型字符，形狀比例可完全自訂。
     */
    private fun drawClaimedTileMarker(context: DrawContext, centerX: Int, top: Int) {
        ClaimedTileMarker.ROW_WIDTHS.forEachIndexed { row, width ->
            val left = centerX - width / 2
            context.fill(left, top + row, left + width, top + row + 1, ClaimedTileMarker.COLOR)
        }
    }

    /** 每幀同步因拖曳／滾輪移動後的原版按鈕座標。 */
    private fun updateCardButtonPositions(layout: DecisionCardLayout, placements: List<DecisionBounds>) {
        cardButtons.forEachIndexed { index, button ->
            val bounds = layout.cardButtonBounds(placements[index])
            button.x = bounds.x
            button.y = bounds.y
        }
    }

    /** 滿寬 track 與依可見比例縮放的 thumb。 */
    private fun renderScrollbar(context: DrawContext, layout: DecisionCardLayout) {
        val top = layout.scrollbarTop
        val bottom = top + DecisionCardLayout.SCROLLBAR_HEIGHT
        context.fill(layout.viewportLeft, top, layout.viewportRight, bottom, SCROLLBAR_TRACK_COLOR)
        val thumb = layout.scrollbarThumb(horizontalScroll)
        context.fill(thumb.left, top, thumb.right, bottom, SCROLLBAR_THUMB_COLOR)
    }

    private companion object {
        const val PANEL_BACKGROUND = 0xCC101820.toInt()
        const val HEADER_TEXT_COLOR = 0xFFD54F
        const val CARD_BACKGROUND = 0xCC2A3844.toInt()
        const val CARD_HOVER_BACKGROUND = 0xDD3A4B59.toInt()
        const val SCROLLBAR_TRACK_COLOR = 0xFF26333D.toInt()
        const val SCROLLBAR_THUMB_COLOR = 0xFF8796A3.toInt()
    }
}
