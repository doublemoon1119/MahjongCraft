package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WIN_AVAILABLE_ID
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WaitingTileAvailabilityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.SuitDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.AutomaticControlStatusHudRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.AutomaticControlStatusHudText
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.AutomaticControlStatusRow
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.automaticControlStatusHudLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.CompactDecisionHudContent
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.DecisionBounds
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.DecisionCardLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.DecisionPanelContent
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.HEADER_TIMER_SCALE
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.PlayerDecisionHudController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.decisionPanelContent
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.decisionPanelLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.decisionTimerParts
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.decisionTimerWidth
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.discardAnalysisContent
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.drawDecisionPanel
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.drawDecisionTimer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import org.koin.core.annotation.Single

/**
 * 以範例內容畫出 HUD 位置編輯器中的真實 HUD 預覽，並量測它們的實際尺寸。
 *
 * 每種 HUD 都使用遊戲中相同的繪製程式與版面計算，只把即時狀態換成固定的範例內容，因此預覽的外觀、尺寸與位置都與
 * 遊戲中一致。預覽只供觀看：按鈕沒有 hover 效果，也不顯示卡片說明。
 *
 * @property decisionHud 操作面板、精簡提示與手牌分析的繪製。
 * @property automaticControlHud 自動操作狀態的繪製。
 */
@Single
class MahjongHudPreviewRenderer(
    private val decisionHud: PlayerDecisionHudController,
    private val automaticControlHud: AutomaticControlStatusHudRenderer,
) {
    /**
     * [element] 以範例內容在 [screenWidth] × [screenHeight] 畫面上的實際尺寸。
     *
     * @param scenario 操作面板使用的預覽情境。
     * @param automaticControlLabels 自動操作狀態要列出的項目名稱；沒有項目時自動操作狀態沒有尺寸。
     */
    internal fun size(
        element: HudElement,
        scenario: HudPreviewScenario,
        automaticControlLabels: List<Text>,
        screenWidth: Int,
        screenHeight: Int,
    ): MahjongHudPreviewSize? = when (element) {
        HudElement.DECISION -> decisionLayout(scenario, screenWidth, screenHeight, panelRatioY = 0.0).let { layout ->
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
     * @param scenario 操作面板使用的預覽情境。
     * @param automaticControlLabels 自動操作狀態要列出的項目名稱。
     */
    internal fun render(
        context: DrawContext,
        element: HudElement,
        scenario: HudPreviewScenario,
        layout: MahjongHudLayoutConfig,
        automaticControlLabels: List<Text>,
        opacity: Float,
    ) {
        context.draw()
        RenderSystem.enableBlend()
        context.setShaderColor(1f, 1f, 1f, opacity)
        when (element) {
            HudElement.DECISION -> renderDecisionPanel(context, scenario, layout.decisionPanelY, opacity)
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
        context.draw()
        context.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }

    /** 畫出操作面板與它的按鈕及倒數；原版按鈕繪製時會重設著色，因此每顆按鈕畫完後重新套用 [opacity]。 */
    private fun renderDecisionPanel(context: DrawContext, scenario: HudPreviewScenario, panelRatioY: Double, opacity: Float) {
        val renderer = MinecraftClient.getInstance().textRenderer
        val content = decisionContent(scenario, context.scaledWindowWidth)
        val layout = decisionLayout(content, context.scaledWindowWidth, context.scaledWindowHeight, panelRatioY)
        val scroll = layout.maximumScroll
        val placements = layout.cardPlacements(scroll)
        fun renderButton(label: Text, bounds: DecisionBounds) {
            val button = ButtonWidget.builder(label) {}.dimensions(bounds.x, bounds.y, bounds.width, bounds.height).build()
            button.setAlpha(opacity)
            button.render(context, NO_POINTER, NO_POINTER, 0f)
            context.draw()
            context.setShaderColor(1f, 1f, 1f, opacity)
        }
        context.drawDecisionPanel(
            renderer = renderer,
            layout = layout,
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
        renderButton(Text.translatable("mahjongcraft.hud.action.skip"), layout.skipButtonBounds)
        context.drawDecisionTimer(
            renderer = renderer,
            parts = PREVIEW_TIMER_PARTS,
            y = layout.headerTextTop(renderer.fontHeight),
            centerX = layout.timerLeft + decisionTimerWidth(renderer, PREVIEW_TIMER_PARTS, HEADER_TIMER_SCALE) / 2,
            scale = HEADER_TIMER_SCALE,
        )
    }

    /** [scenario] 的操作面板內容。 */
    private fun decisionContent(scenario: HudPreviewScenario, screenWidth: Int): DecisionPanelContent = decisionPanelContent(
        prompt = scenario.prompt,
        texts = decisionHud.decisionTexts,
        resolvePlayerName = decisionHud::resolveTriggerPlayerName,
        renderer = MinecraftClient.getInstance().textRenderer,
        screenWidth = screenWidth,
    )

    /** [scenario] 的操作面板版面。 */
    private fun decisionLayout(
        scenario: HudPreviewScenario,
        screenWidth: Int,
        screenHeight: Int,
        panelRatioY: Double,
    ): DecisionCardLayout = decisionLayout(decisionContent(scenario, screenWidth), screenWidth, screenHeight, panelRatioY)

    /** [content] 的操作面板版面；倒數保留寬度依範例倒數量測。 */
    private fun decisionLayout(
        content: DecisionPanelContent,
        screenWidth: Int,
        screenHeight: Int,
        panelRatioY: Double,
    ): DecisionCardLayout {
        val renderer = MinecraftClient.getInstance().textRenderer
        return decisionPanelLayout(
            content = content,
            renderer = renderer,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            panelRatioY = panelRatioY,
            timerWidth = decisionTimerWidth(renderer, PREVIEW_TIMER_PARTS, HEADER_TIMER_SCALE),
        )
    }

    /** 手牌分析的範例內容：聽二、五、八萬三面，各自剩下不同張數。 */
    private fun analysisContent() = discardAnalysisContent(
        texts = decisionHud.decisionTexts,
        ruleModuleId = BuiltInRuleModuleIds.RIICHI,
        analysis = DiscardReadinessAnalysisDto(
            discardTileId = PREVIEW_DISCARD_TILE_ID,
            waitingTiles = listOf(2, 5, 8).mapIndexed { index, value ->
                WaitingTileAvailabilityDto(
                    tile = TileDto.Numeric(SuitDto.CHARACTER, value),
                    remainingCount = 3 - index,
                    winAvailability = WIN_AVAILABLE_ID,
                )
            },
            statusIndicatorId = null,
        ),
    )

    /** 自動操作狀態的範例：依序交替顯示開啟與關閉的 [labels]。 */
    private fun automaticControlRows(labels: List<Text>): List<AutomaticControlStatusRow> = labels.mapIndexed { index, label ->
        AutomaticControlStatusRow(controlId = "preview:$index", label = label, enabled = index % 2 == 0)
    }

    /** 自動操作狀態在指定畫面上的版面；沒有項目時為 null。 */
    private fun automaticControlLayout(labels: List<Text>, screenWidth: Int, screenHeight: Int) = labels.takeIf { it.isNotEmpty() }?.let {
        val renderer = MinecraftClient.getInstance().textRenderer
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

        /** 範例倒數：基本 5 秒加保留 20 秒。 */
        val PREVIEW_TIMER_PARTS = decisionTimerParts(baseSeconds = 5, reserveSeconds = 20)

        /** 精簡提示的範例：操作介面收起後的重新開啟提醒。 */
        val COMPACT_PREVIEW_CONTENT: CompactDecisionHudContent = CompactDecisionHudContent.ReopenReminder

        /** 手牌分析範例中假想打出的牌。 */
        const val PREVIEW_DISCARD_TILE_ID = "00000000-0000-0000-0000-000000000000"
    }
}
