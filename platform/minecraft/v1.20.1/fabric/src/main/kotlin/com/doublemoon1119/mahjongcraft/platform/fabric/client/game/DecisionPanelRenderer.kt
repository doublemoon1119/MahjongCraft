package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.CardBackground
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ClaimedTileMarker
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.HorizontalScrollbar
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.wrapLinesBalanced
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionPlayerRelationDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftHudKeys
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
 * @param prompt 目前權威決策提示。
 * @param texts 動作與狀態文字解析器。
 * @param resolvePlayerName 依玩家 UUID 字串取得顯示名稱，用於 prompt 沒有附上名稱的觸發玩家。
 * @param renderer Minecraft 文字量測器。
 * @param screenWidth 畫面寬度。
 * @return 可供版面計算與繪製使用的操作面板內容。
 */
internal fun decisionPanelContent(
    prompt: PlayerDecisionPromptDto,
    texts: DecisionTextResolver,
    resolvePlayerName: (String) -> String,
    renderer: TextRenderer,
    screenWidth: Int,
): DecisionPanelContent = DecisionPanelContent(
    title = Text.translatable(MinecraftHudKeys.ACTION_TITLE),
    triggerLines = decisionTriggerText(prompt, texts, resolvePlayerName)
        ?.let { renderer.wrapLines(it, DecisionCardLayout.triggerTextMaximumWidth(screenWidth)) }
        .orEmpty(),
    triggerTileAssetKey = prompt.triggerTileAssetKey?.takeIf { it.isNotEmpty() },
    entries = decisionEntriesFrom(texts, prompt),
)

/**
 * 依觸發玩家與動作建立本地化提示；沒有來源玩家的摸牌使用自摸牌句型。
 *
 * @param prompt 目前權威決策提示。
 * @param texts 動作文字解析器。
 * @param resolvePlayerName 提示未附名稱時使用的玩家名稱查詢。
 * @return 觸發動作說明；沒有可描述的觸發牌或玩家方位時為 null。
 */
private fun decisionTriggerText(
    prompt: PlayerDecisionPromptDto,
    texts: DecisionTextResolver,
    resolvePlayerName: (String) -> String,
): Text? {
    val playerId = prompt.triggerPlayerId
        ?: return if (prompt.triggerTileAssetKey != null) Text.translatable(MinecraftHudKeys.TRIGGER_SELF_DRAW) else null
    val playerName = prompt.triggerPlayerName ?: resolvePlayerName(playerId)
    val relationKey = when (prompt.triggerPlayerRelation) {
        DecisionPlayerRelationDto.LEFT -> MinecraftHudKeys.RELATION_LEFT
        DecisionPlayerRelationDto.ACROSS -> MinecraftHudKeys.RELATION_ACROSS
        DecisionPlayerRelationDto.RIGHT -> MinecraftHudKeys.RELATION_RIGHT
        null -> return null
    }
    val action = texts.actionLabel(prompt.ruleModuleId, prompt.triggerActionId ?: BuiltInGameActionIds.DISCARD)
    return Text.translatable(MinecraftHudKeys.TRIGGER, playerName, Text.translatable(relationKey), action)
}

/**
 * 同一次量測得出的幾何與可直接繪製的文字行。
 *
 * @property geometry 操作面板版面幾何。
 * @property cardTextLines 依卡片順序排列的正文與提示。
 */
internal data class MeasuredDecisionPanel(
    val geometry: DecisionCardLayout,
    val cardTextLines: List<DecisionCardTextLines>,
)

/**
 * 卡片量測與繪製共用的換行結果。
 *
 * @property body 正文各行。
 * @property hint 補充提示各行。
 */
internal data class DecisionCardTextLines(val body: List<OrderedText>, val hint: List<OrderedText>)

/**
 * [content] 在指定畫面、玩家設定的位置比例與倒數保留寬度下的版面幾何。
 *
 * @param content 操作面板繪製內容。
 * @param renderer Minecraft 文字量測器。
 * @param screenWidth 畫面寬度。
 * @param screenHeight 畫面高度。
 * @param panelRatioY 玩家設定的面板垂直位置比例。
 * @param timerWidth 標題列保留的倒數寬度。
 * @return 面板版面幾何與換行結果。
 */
internal fun decisionPanelLayout(
    content: DecisionPanelContent,
    renderer: TextRenderer,
    screenWidth: Int,
    screenHeight: Int,
    panelRatioY: Double,
    timerWidth: Int,
): MeasuredDecisionPanel {
    val base = DecisionCardLayout(
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
    val cardTextLines = content.entries.mapIndexed { index, entry ->
        val draft = base.cards[index].copy(
            bodyLineCount = if (entry.cardBody != null) 1 else 0,
            hintLineCount = if (entry.cardHint != null) 1 else 0,
        )
        val textWidth = (base.cardWidth(draft) - DecisionCardLayout.CARD_PADDING * 2).coerceAtLeast(1)
        DecisionCardTextLines(
            body = entry.cardBody?.let { renderer.wrapLinesBalanced(it, textWidth) }.orEmpty(),
            hint = entry.cardHint?.let { renderer.wrapLinesBalanced(it, textWidth) }.orEmpty(),
        )
    }
    return MeasuredDecisionPanel(
        geometry = base.copy(
            cards = base.cards.mapIndexed { index, card ->
                val lines = cardTextLines[index]
                card.copy(bodyLineCount = lines.body.size, hintLineCount = lines.hint.size)
            },
        ),
        cardTextLines = cardTextLines,
    )
}

/**
 * 畫出操作面板的背景、標題、觸發牌、卡片與 scrollbar。
 *
 * 卡片按鈕由 [renderCardButtons] 在卡片列的裁切範圍內畫出；跳過按鈕、倒數與卡片說明由呼叫端另外畫。
 *
 * @param renderer Minecraft 文字繪製器。
 * @param measured 本次版面幾何與已換行的卡片文字。
 * @param content 要繪製的面板內容。
 * @param placements 依目前捲動量算出的卡片版位。
 * @param scroll 目前捲動量，決定 scrollbar thumb 的位置。
 * @param mouseX 游標水平座標；傳入畫面外座標可停用 hover。
 * @param mouseY 游標垂直座標；傳入畫面外座標可停用 hover。
 * @param drawTile 在指定左上角畫出一張預覽尺寸的牌面。
 * @param renderCardButtons 在裁切區內繪製卡片按鈕的回呼。
 */
internal fun DrawContext.drawDecisionPanel(
    renderer: TextRenderer,
    measured: MeasuredDecisionPanel,
    content: DecisionPanelContent,
    placements: List<DecisionBounds>,
    scroll: Double,
    mouseX: Int,
    mouseY: Int,
    drawTile: (assetKey: String, x: Int, y: Int) -> Unit,
    renderCardButtons: () -> Unit,
) {
    val layout = measured.geometry
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
        drawDecisionCard(
            renderer = renderer,
            layout = layout,
            card = layout.cards[index],
            entry = entry,
            lines = measured.cardTextLines[index],
            placement = placements[index],
            mouseX = mouseX,
            mouseY = mouseY,
            drawTile = drawTile,
        )
    }
    renderCardButtons()
    disableScissor()
    if (layout.hasOverflow) {
        HorizontalScrollbar.render(
            context = this,
            left = layout.viewportLeft,
            right = layout.viewportRight,
            top = layout.scrollbarTop,
            height = DecisionCardLayout.SCROLLBAR_HEIGHT,
            thumb = layout.scrollbarThumb(scroll),
        )
    }
}

/**
 * 繪製單一卡片的背景、預覽牌、說明與補充提示。
 *
 * @param renderer 文字繪製器。
 * @param layout 操作面板版面幾何。
 * @param card 已量測文字行數的卡片特徵。
 * @param entry 卡片內容。
 * @param lines 已量測的正文與提示。
 * @param placement 卡片目前的畫面範圍。
 * @param mouseX 游標水平座標。
 * @param mouseY 游標垂直座標。
 * @param drawTile 在指定左上角繪製預覽牌面的回呼。
 */
private fun DrawContext.drawDecisionCard(
    renderer: TextRenderer,
    layout: DecisionCardLayout,
    card: DecisionCard,
    entry: DecisionEntry,
    lines: DecisionCardTextLines,
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
        if (hovered) CardBackground.HOVER_COLOR else CardBackground.COLOR,
    )
    val tiles = entry.previewTileAssetKeys
    layout.previewTilePlacements(placement, tiles.size, card).forEachIndexed { index, tile ->
        drawTile(tiles[index], tile.x, tile.y)
        if (index == entry.claimedTileIndex) {
            drawClaimedTileMarker(tile.x + tile.width / 2, layout.claimedTileMarkerTop(tile))
        }
    }
    lines.body.forEachIndexed { index, line ->
        drawCenteredTextWithShadow(
            renderer,
            line,
            placement.x + placement.width / 2,
            layout.bodyTextTop(placement, card) + index * DecisionCardLayout.TEXT_LINE_HEIGHT,
            CARD_BODY_COLOR,
        )
    }
    lines.hint.forEachIndexed { index, line ->
        drawCenteredTextWithShadow(
            renderer,
            line,
            placement.x + placement.width / 2,
            layout.hintTextTop(placement, card) + index * DecisionCardLayout.TEXT_LINE_HEIGHT,
            CARD_HINT_COLOR,
        )
    }
}

/**
 * 在 [centerX], [top] 位置畫一個寬扁的倒三角形指標，逐列縮減寬度來模擬三角形，
 * 不依賴字型字符，形狀比例可完全自訂。
 *
 * @param centerX 指標中心的水平座標。
 * @param top 指標最上方的垂直座標。
 */
private fun DrawContext.drawClaimedTileMarker(centerX: Int, top: Int) {
    ClaimedTileMarker.ROW_WIDTHS.forEachIndexed { row, width ->
        val left = centerX - width / 2
        fill(left, top + row, left + width, top + row + 1, ClaimedTileMarker.COLOR)
    }
}

/** 標題列內的倒數縮放，與標題同尺寸並對齊文字上緣。 */
internal const val HEADER_TIMER_SCALE = 1f

/** 操作面板底色 ARGB。 */
private const val DECISION_PANEL_BACKGROUND = 0xCC101820.toInt()

/** 標題文字顏色。 */
private const val HEADER_TEXT_COLOR = 0xFFD54F

/** 觸發玩家及觸發動作文字顏色。 */
private const val TRIGGER_TEXT_COLOR = 0xFFFFFF

/** 卡片中央說明顏色。 */
private const val CARD_BODY_COLOR = 0xDDDDDD

/** 按鈕旁補充提示的淡色文字。 */
private const val CARD_HINT_COLOR = 0x999999
