package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ClaimedTileMarker
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionPlayerRelationDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.OrderedText
import net.minecraft.text.Text

/**
 * 操作面板一次繪製所需的內容。
 *
 * @property title 面板標題。
 * @property triggerLines 觸發文字依畫面寬度換行後的每一行；沒有觸發文字時為空清單。
 * @property triggerTileAssetKey 觸發牌的牌面；沒有觸發牌時為 null。
 * @property entries 由左至右的操作卡。
 */
internal data class DecisionPanelContent(
    val title: Text,
    val triggerLines: List<OrderedText>,
    val triggerTileAssetKey: String?,
    val entries: List<DecisionEntry>,
)

/**
 * 由 [prompt] 組出操作面板的內容；操作畫面與 HUD 位置編輯器的預覽共用。
 *
 * @param resolvePlayerName 依玩家 UUID 字串取得顯示名稱，用於 prompt 沒有附上名稱的觸發玩家。
 */
internal fun decisionPanelContent(
    prompt: PlayerDecisionPromptDto,
    texts: DecisionTextResolver,
    resolvePlayerName: (String) -> String,
    renderer: TextRenderer,
    screenWidth: Int,
): DecisionPanelContent = DecisionPanelContent(
    title = Text.translatable("mahjongcraft.hud.action_title"),
    triggerLines = decisionTriggerText(prompt, texts, resolvePlayerName)
        ?.let { renderer.wrapLines(it, DecisionCardLayout.triggerTextMaximumWidth(screenWidth)) }
        .orEmpty(),
    triggerTileAssetKey = prompt.triggerTileAssetKey?.takeIf { it.isNotEmpty() },
    entries = decisionEntriesFrom(texts, prompt),
)

/** 使用共用玩家名稱來源與完整本地化句型；自己摸牌沒有來源玩家時使用專用句型。 */
private fun decisionTriggerText(
    prompt: PlayerDecisionPromptDto,
    texts: DecisionTextResolver,
    resolvePlayerName: (String) -> String,
): Text? {
    val playerId = prompt.triggerPlayerId
        ?: return if (prompt.triggerTileAssetKey != null) Text.translatable("mahjongcraft.hud.trigger.self_draw") else null
    val playerName = prompt.triggerPlayerName ?: resolvePlayerName(playerId)
    val relationKey = when (prompt.triggerPlayerRelation) {
        DecisionPlayerRelationDto.LEFT -> "mahjongcraft.hud.relation.left"
        DecisionPlayerRelationDto.ACROSS -> "mahjongcraft.hud.relation.across"
        DecisionPlayerRelationDto.RIGHT -> "mahjongcraft.hud.relation.right"
        null -> return null
    }
    val action = texts.actionLabel(prompt.ruleModuleId, prompt.triggerActionId ?: BuiltInGameActionIds.DISCARD)
    return Text.translatable("mahjongcraft.hud.trigger", playerName, Text.translatable(relationKey), action)
}

/** [content] 在指定畫面、玩家設定的位置比例與倒數保留寬度下的版面幾何。 */
internal fun decisionPanelLayout(
    content: DecisionPanelContent,
    renderer: TextRenderer,
    screenWidth: Int,
    screenHeight: Int,
    panelRatioY: Double,
    timerWidth: Int,
): DecisionCardLayout = DecisionCardLayout(
    screenWidth = screenWidth,
    screenHeight = screenHeight,
    cards = content.entries.map(DecisionEntry::layoutCard),
    headerTextWidth = renderer.getWidth(content.title),
    triggerLineCount = content.triggerLines.size,
    triggerTextWidth = content.triggerLines.maxOfOrNull(renderer::getWidth) ?: 0,
    hasTriggerTile = content.triggerTileAssetKey != null,
    panelRatioY = panelRatioY,
    timerWidth = timerWidth,
)

/**
 * 畫出操作面板的背景、標題、觸發牌、卡片與 scrollbar。
 *
 * 卡片按鈕由 [renderCardButtons] 在卡片列的裁切範圍內畫出；跳過按鈕、倒數與卡片說明由呼叫端另外畫。
 *
 * @param placements [layout] 依目前捲動量算出的卡片版位。
 * @param scroll 目前捲動量，決定 scrollbar thumb 的位置。
 * @param drawTile 在指定左上角畫出一張預覽尺寸的牌面。
 */
internal fun DrawContext.drawDecisionPanel(
    renderer: TextRenderer,
    layout: DecisionCardLayout,
    content: DecisionPanelContent,
    placements: List<DecisionBounds>,
    scroll: Double,
    mouseX: Int,
    mouseY: Int,
    drawTile: (assetKey: String, x: Int, y: Int) -> Unit,
    renderCardButtons: () -> Unit,
) {
    fill(layout.panelLeft, layout.panelTop, layout.panelRight, layout.panelBottom, DECISION_PANEL_BACKGROUND)
    drawCenteredTextWithShadow(renderer, content.title, layout.screenWidth / 2, layout.headerTextTop(renderer.fontHeight), HEADER_TEXT_COLOR)
    content.triggerTileAssetKey?.let { assetKey ->
        val panel = layout.triggerPanelBounds
        fill(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, DECISION_PANEL_BACKGROUND)
        content.triggerLines.forEachIndexed { index, line ->
            drawCenteredTextWithShadow(renderer, line, layout.screenWidth / 2, layout.triggerTextLineTop(index), TRIGGER_TEXT_COLOR)
        }
        val tile = layout.triggerTileBounds
        drawTile(assetKey, tile.x, tile.y)
    }
    enableScissor(layout.viewportLeft, layout.cardTop, layout.viewportRight, layout.cardBottom)
    content.entries.forEachIndexed { index, entry ->
        drawDecisionCard(layout, entry, placements[index], mouseX, mouseY, drawTile)
    }
    renderCardButtons()
    disableScissor()
    if (layout.hasOverflow) {
        val top = layout.scrollbarTop
        val bottom = top + DecisionCardLayout.SCROLLBAR_HEIGHT
        fill(layout.viewportLeft, top, layout.viewportRight, bottom, SCROLLBAR_TRACK_COLOR)
        val thumb = layout.scrollbarThumb(scroll)
        fill(thumb.left, top, thumb.right, bottom, SCROLLBAR_THUMB_COLOR)
    }
}

/** 繪製單一卡片的背景、預覽牌與鳴牌指標。 */
private fun DrawContext.drawDecisionCard(
    layout: DecisionCardLayout,
    entry: DecisionEntry,
    placement: DecisionBounds,
    mouseX: Int,
    mouseY: Int,
    drawTile: (assetKey: String, x: Int, y: Int) -> Unit,
) {
    val hovered = mouseX in placement.x until placement.x + placement.width &&
        mouseY in placement.y until placement.y + placement.height
    fill(
        placement.x,
        placement.y,
        placement.x + placement.width,
        placement.y + placement.height,
        if (hovered) CARD_HOVER_BACKGROUND else CARD_BACKGROUND,
    )
    val tiles = entry.previewTileAssetKeys
    layout.previewTilePlacements(placement, tiles.size).forEachIndexed { index, tile ->
        drawTile(tiles[index], tile.x, tile.y)
        if (index == entry.claimedTileIndex) {
            drawClaimedTileMarker(tile.x + tile.width / 2, layout.claimedTileMarkerTop(tile))
        }
    }
}

/**
 * 在 [centerX], [top] 位置畫一個寬扁的倒三角形指標，逐列縮減寬度來模擬三角形，
 * 不依賴字型字符，形狀比例可完全自訂。
 */
private fun DrawContext.drawClaimedTileMarker(centerX: Int, top: Int) {
    ClaimedTileMarker.ROW_WIDTHS.forEachIndexed { row, width ->
        val left = centerX - width / 2
        fill(left, top + row, left + width, top + row + 1, ClaimedTileMarker.COLOR)
    }
}

/** 標題列內的倒數與標題同尺寸，並與標題文字上緣對齊。 */
internal const val HEADER_TIMER_SCALE = 1f

private const val DECISION_PANEL_BACKGROUND = 0xCC101820.toInt()
private const val HEADER_TEXT_COLOR = 0xFFD54F
private const val TRIGGER_TEXT_COLOR = 0xFFFFFF
private const val CARD_BACKGROUND = 0xCC2A3844.toInt()
private const val CARD_HOVER_BACKGROUND = 0xDD3A4B59.toInt()
private const val SCROLLBAR_TRACK_COLOR = 0xFF26333D.toInt()
private const val SCROLLBAR_THUMB_COLOR = 0xFF8796A3.toInt()
