package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WaitingTileAvailabilityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.SuitDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailabilityIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.AutomaticControlStatusHudRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.AutomaticControlStatusHudText
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.AutomaticControlStatusRow
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.automaticControlStatusHudLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.CompactDecisionHudContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.DecisionBounds
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.DecisionCardLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.DecisionPanelContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.HEADER_TIMER_SCALE
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.MeasuredDecisionPanel
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.PlayerDecisionHudController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.decisionPanelContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.decisionPanelLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.decisionTimerParts
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.decisionTimerWidth
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.discardAnalysisContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.drawDecisionPanel
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.drawDecisionTimer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.RestartableMarqueeButtonWidget
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import kotlin.time.Duration.Companion.seconds

/**
 * 以範例內容畫出 HUD 位置編輯器中的真實 HUD 預覽，並量測它們的實際尺寸。
 *
 * 每種 HUD 都使用遊戲中相同的繪製程式與版面計算，只把即時狀態換成固定的範例內容，因此預覽的外觀、尺寸與位置都與
 * 相同範例內容在遊戲中的結果一致。預覽只供觀看：按鈕沒有 hover 效果，也不顯示卡片說明。
 *
 * @property decisionHud 操作面板、精簡提示與手牌分析的繪製。
 * @property automaticControlHud 自動操作狀態的繪製。
 */
class MahjongHudPreviewRenderer(
    private val decisionHud: PlayerDecisionHudController,
    private val automaticControlHud: AutomaticControlStatusHudRenderer,
) {
    /**
     * [element] 以範例內容在 [screenWidth] × [screenHeight] 畫面上的實際尺寸。
     *
     * @param element 要量測的 HUD 類型。
     * @param scenario 操作面板使用的預覽情境。
     * @param automaticControlLabels 自動操作狀態要列出的項目名稱；沒有項目時自動操作狀態沒有尺寸。
     * @param screenWidth 畫面寬度。
     * @param screenHeight 畫面高度。
     * @return HUD 的實際尺寸；自動操作狀態沒有項目時為 `null`。
     */
    internal fun size(
        element: HudElement,
        scenario: HudPreviewScenario,
        automaticControlLabels: List<Component>,
        screenWidth: Int,
        screenHeight: Int,
    ): MahjongHudPreviewSize? = when (element) {
        HudElement.DECISION -> decisionLayout(scenario.prompt, screenWidth, screenHeight, panelRatioY = 0.0).let { layout ->
            MahjongHudPreviewSize(width = layout.panelWidth, height = layout.groupHeight)
        }
        HudElement.COMPACT -> decisionHud.compactHudLayout(screenWidth, screenHeight, COMPACT_PREVIEW_CONTENT, ratioX = 0.0, ratioY = 0.0).let { layout ->
            MahjongHudPreviewSize(width = layout.groupWidth, height = layout.groupHeight)
        }
        HudElement.ANALYSIS -> decisionHud.analysisLayout(screenWidth, screenHeight, analysisContent(), ratioY = 0.0).let { layout ->
            MahjongHudPreviewSize(width = layout.panelWidth, height = layout.panelHeight)
        }
        HudElement.AUTOMATIC_CONTROL -> automaticControlLayout(automaticControlLabels, screenWidth, screenHeight)?.let { layout ->
            MahjongHudPreviewSize(width = layout.bounds.width, height = layout.bounds.height)
        }
    }

    /**
     * 以 [opacity] 不透明度畫出 [element] 的範例內容，位置依 [layout] 草稿決定。
     *
     * @param context 遊戲 GUI 繪製環境。
     * @param element 要繪製的 HUD 類型。
     * @param scenario 操作面板使用的預覽情境。
     * @param layout 預覽使用的 HUD 位置草稿。
     * @param automaticControlLabels 自動操作狀態要列出的項目名稱。
     * @param opacity 預覽的不透明度。
     */
    internal fun render(
        context: GuiGraphics,
        element: HudElement,
        scenario: HudPreviewScenario,
        layout: MahjongHudLayoutConfig,
        automaticControlLabels: List<Component>,
        opacity: Float,
    ) {
        context.flush()
        RenderSystem.enableBlend()
        context.setColor(1f, 1f, 1f, opacity)
        when (element) {
            HudElement.DECISION -> renderDecisionPanel(
                context = context,
                prompt = scenario.prompt,
                screenWidth = context.guiWidth(),
                screenHeight = context.guiHeight(),
                panelRatioY = layout.decisionPanelY,
                opacity = opacity,
            )
            HudElement.COMPACT -> decisionHud.renderCompactHud(
                context = context,
                content = COMPACT_PREVIEW_CONTENT,
                timerParts = PREVIEW_TIMER_PARTS,
                ratioX = layout.compactPromptX,
                ratioY = layout.compactPromptY,
            )
            HudElement.ANALYSIS -> decisionHud.renderAnalysisPanel(context, analysisContent(), layout.discardAnalysisY)
            HudElement.AUTOMATIC_CONTROL -> automaticControlHud.renderRows(
                context = context,
                rows = automaticControlRows(automaticControlLabels),
                ratioX = layout.automaticControlStatusX,
                ratioY = layout.automaticControlStatusY,
            )
        }
        context.flush()
        context.setColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }

    /**
     * 範例操作面板在 [width] 寬的範圍內所需的高度，包含上方的觸發框。
     *
     * @param prompt 範例決策提示。
     * @param width 可用寬度。
     * @return 操作面板連同觸發框的高度。
     */
    internal fun decisionPanelHeight(prompt: PlayerDecisionPromptDto, width: Int): Int = decisionLayout(prompt, width, MEASURE_HEIGHT, panelRatioY = 0.0).groupHeight

    /**
     * 在從 ([left], [top]) 起、[width] 寬的範圍內畫出範例操作面板，面板連同觸發框貼齊範圍上緣並水平置中。
     * 繪製方式與遊戲中的操作面板相同，例如「如何開始」畫面用來說明操作面板。
     *
     * @param context 畫面繪製環境。
     * @param prompt 範例決策提示。
     * @param left 範圍左界。
     * @param top 範圍上界。
     * @param width 範圍寬度。
     */
    internal fun renderDecisionPanelAt(context: GuiGraphics, prompt: PlayerDecisionPromptDto, left: Int, top: Int, width: Int) {
        val layout = decisionLayout(prompt, width, MEASURE_HEIGHT, panelRatioY = 0.0)
        context.pose().pushPose()
        context.pose().translate(left.toDouble(), (top - layout.groupTop).toDouble(), 0.0)
        renderDecisionPanel(context, prompt, width, MEASURE_HEIGHT, panelRatioY = 0.0, opacity = 1f)
        context.pose().popPose()
    }

    /**
     * 畫出操作面板與它的按鈕及倒數；原版按鈕繪製時會重設著色，因此每顆按鈕畫完後重新套用 [opacity]。
     *
     * @param context 畫面繪製環境。
     * @param prompt 範例決策提示。
     * @param screenWidth 面板所在範圍的寬度。
     * @param screenHeight 面板所在範圍的高度。
     * @param panelRatioY 面板垂直位置比例。
     * @param opacity 預覽不透明度。
     */
    private fun renderDecisionPanel(
        context: GuiGraphics,
        prompt: PlayerDecisionPromptDto,
        screenWidth: Int,
        screenHeight: Int,
        panelRatioY: Double,
        opacity: Float,
    ) {
        val renderer = Minecraft.getInstance().font
        val content = decisionContent(prompt, screenWidth)
        val measured = measureDecisionPanel(content, screenWidth, screenHeight, panelRatioY)
        val layout = measured.geometry
        val scroll = layout.maximumScroll
        val placements = layout.cardPlacements(scroll)

        /** 以預覽不透明度繪製按鈕，並恢復後續內容使用的著色。 */
        fun renderButton(label: Component, bounds: DecisionBounds) {
            val button = RestartableMarqueeButtonWidget.builder(label) {}.dimensions(bounds.x, bounds.y, bounds.width, bounds.height).build()
            button.setAlpha(opacity)
            button.render(context, NO_POINTER, NO_POINTER, 0f)
            context.flush()
            context.setColor(1f, 1f, 1f, opacity)
        }
        context.drawDecisionPanel(
            renderer = renderer,
            measured = measured,
            content = content,
            placements = placements,
            scroll = scroll,
            mouseX = NO_POINTER,
            mouseY = NO_POINTER,
            drawTile = { assetKey, x, y ->
                decisionHud.renderTileFace(context, assetKey, x, y, DecisionCardLayout.PREVIEW_TILE_WIDTH, DecisionCardLayout.PREVIEW_TILE_HEIGHT)
            },
        ) {
            content.entries.forEachIndexed { index, entry -> renderButton(entry.label, layout.cardButtonBounds(placements[index])) }
        }
        renderButton(decisionHud.decisionTexts.skipLabel(ruleModuleId = null), layout.skipButtonBounds)
        context.drawDecisionTimer(
            renderer = renderer,
            parts = PREVIEW_TIMER_PARTS,
            y = layout.headerTextTop(renderer.lineHeight),
            centerX = layout.timerLeft + decisionTimerWidth(renderer, PREVIEW_TIMER_PARTS, HEADER_TIMER_SCALE) / 2,
            scale = HEADER_TIMER_SCALE,
        )
    }

    /**
     * 建立 [prompt] 的操作面板內容。
     *
     * @param prompt 範例決策提示。
     * @param screenWidth 畫面寬度。
     * @return 可供版面計算與繪製使用的面板內容。
     */
    private fun decisionContent(prompt: PlayerDecisionPromptDto, screenWidth: Int): DecisionPanelContent = decisionPanelContent(
        prompt = prompt,
        texts = decisionHud.decisionTexts,
        resolvePlayerName = decisionHud::resolveTriggerPlayerName,
        renderer = Minecraft.getInstance().font,
        screenWidth = screenWidth,
    )

    /**
     * 建立 [prompt] 的操作面板版面。
     *
     * @param prompt 範例決策提示。
     * @param screenWidth 畫面寬度。
     * @param screenHeight 畫面高度。
     * @param panelRatioY 面板垂直位置比例。
     * @return 對應的操作面板版面幾何。
     */
    private fun decisionLayout(
        prompt: PlayerDecisionPromptDto,
        screenWidth: Int,
        screenHeight: Int,
        panelRatioY: Double,
    ): DecisionCardLayout = measureDecisionPanel(decisionContent(prompt, screenWidth), screenWidth, screenHeight, panelRatioY).geometry

    /**
     * 建立 [content] 的操作面板版面；倒數保留寬度依範例倒數量測。
     *
     * @param content 面板內容。
     * @param screenWidth 畫面寬度。
     * @param screenHeight 畫面高度。
     * @param panelRatioY 面板垂直位置比例。
     * @return 對應的操作面板版面幾何與已換行文字。
     */
    private fun measureDecisionPanel(
        content: DecisionPanelContent,
        screenWidth: Int,
        screenHeight: Int,
        panelRatioY: Double,
    ): MeasuredDecisionPanel {
        val renderer = Minecraft.getInstance().font
        return decisionPanelLayout(
            content = content,
            renderer = renderer,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            panelRatioY = panelRatioY,
            timerWidth = decisionTimerWidth(renderer, PREVIEW_TIMER_PARTS, HEADER_TIMER_SCALE),
        )
    }

    /**
     * 建立手牌分析範例內容：聽二、五、八萬三面，各自剩下不同張數。
     *
     * @return 供預覽繪製使用的分析內容。
     */
    private fun analysisContent() = discardAnalysisContent(
        texts = decisionHud.decisionTexts,
        ruleModuleId = BuiltInRuleModuleIds.RIICHI,
        analysis = DiscardReadinessAnalysisDto(
            discardTileId = PREVIEW_DISCARD_TILE_ID,
            waitingTiles = listOf(2, 5, 8).mapIndexed { index, value ->
                WaitingTileAvailabilityDto(
                    tile = TileDto.Numeric(SuitDto.CHARACTER, value),
                    remainingCount = 3 - index,
                    winAvailability = WaitingTileAvailabilityIds.WIN_AVAILABLE,
                )
            },
            statusIndicatorId = null,
        ),
    )

    /**
     * 建立自動操作狀態範例：依序交替顯示開啟與關閉的 [labels]。
     *
     * @param labels 要呈現的項目名稱。
     * @return 與名稱順序相同的範例狀態列。
     */
    private fun automaticControlRows(labels: List<Component>): List<AutomaticControlStatusRow> = labels.mapIndexed { index, label ->
        AutomaticControlStatusRow(controlId = "preview:$index", label = label, enabled = index % 2 == 0)
    }

    /**
     * 計算自動操作狀態在指定畫面上的版面；沒有項目時為 null。
     *
     * @param labels 要呈現的項目名稱。
     * @param screenWidth 畫面寬度。
     * @param screenHeight 畫面高度。
     * @return 自動操作狀態版面；沒有項目時為 `null`。
     */
    private fun automaticControlLayout(labels: List<Component>, screenWidth: Int, screenHeight: Int) = labels.takeIf { it.isNotEmpty() }?.let {
        val renderer = Minecraft.getInstance().font
        automaticControlStatusHudLayout(
            rowWidths = AutomaticControlStatusHudText.rowWidths(renderer, labels),
            textHeight = AutomaticControlStatusHudText.textHeight(renderer),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            ratioX = 0.0,
            ratioY = 0.0,
            summaryWidth = { AutomaticControlStatusHudText.summaryWidth(renderer, it) },
        )
    }

    private companion object {
        /** 預覽不跟隨游標，以畫面外的座標畫出沒有 hover 效果的按鈕與卡片。 */
        const val NO_POINTER = -1

        /** 在指定範圍內繪製時計算版面所用的高度；面板貼齊上緣，高度只需足夠容納面板。 */
        const val MEASURE_HEIGHT = 1000

        /** 範例倒數：基本 5 秒加保留 20 秒。 */
        val PREVIEW_TIMER_PARTS = decisionTimerParts(base = 5.seconds, reserve = 20.seconds)

        /** 精簡提示的範例：操作介面收起後的重新開啟提醒。 */
        val COMPACT_PREVIEW_CONTENT: CompactDecisionHudContent = CompactDecisionHudContent.ReopenReminder

        /** 手牌分析範例中假想打出的牌。 */
        const val PREVIEW_DISCARD_TILE_ID = "00000000-0000-0000-0000-000000000000"
    }
}
