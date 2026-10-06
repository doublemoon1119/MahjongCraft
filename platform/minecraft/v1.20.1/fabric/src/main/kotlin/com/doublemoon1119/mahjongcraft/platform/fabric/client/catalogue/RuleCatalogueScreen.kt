package com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.CycleButtonInput
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.MinecraftRuleCatalogueScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowseContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowseState
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowser
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueConfigSource
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Language

/**
 * 可切換規則、分類與搜尋的唯讀規則一覽；上方控制區與底部按鈕固定，只有中間的條目會捲動。
 *
 * @property browser 規則、分類、搜尋與設定來源的瀏覽狀態。
 * @property presenter 條目 card 的文字與牌例測量。
 * @property tileFaces 牌面繪製。
 * @property openingContext 開啟時的設定來源。
 * @property exit 關閉後回到哪裡，以及開啟它的畫面是否仍然有效。
 */
internal class RuleCatalogueScreen(
    private val browser: RuleCatalogueBrowser,
    private val presenter: RuleCataloguePresenter,
    private val tileFaces: MahjongTileFaceRenderer,
    private val openingContext: RuleCatalogueBrowseContext,
    private val exit: RuleCatalogueExit,
) : Screen(Text.translatable(MinecraftRuleCatalogueScreenKeys.TITLE)) {
    /** 返回時回到的畫面；直接關回遊戲時為 null。 */
    internal val returnScreen: Screen? get() = exit.returnScreen

    /** 目前的瀏覽快照。 */
    private var state: RuleCatalogueBrowseState = browser.snapshot(presenter::translation)

    /** 目前的畫面幾何。 */
    private var layout = RuleCatalogueScreenLayout.measure(
        width = 1,
        height = 1,
        clearWidth = 0,
        sourceToggle = false,
    )

    /** 目前條目的 card；只在內容、語言或尺寸改變時重新測量。 */
    private var cards = emptyList<CatalogueCard>()

    /** 全部 card 與間距的總高度。 */
    private var contentHeight = 0

    /** 內容捲動與捲軸拖曳。 */
    private val scroll = RuleCatalogueScrollState()

    /** 上次建立畫面時的語言，用於偵測語言切換。 */
    private var language: Language = Language.getInstance()

    /** 搜尋輸入框。 */
    private lateinit var search: TextFieldWidget

    /** 清除搜尋按鈕。 */
    private lateinit var clear: ButtonWidget

    /** 依目前尺寸與語言建立控制項並重新測量內容；保留規則、分類、搜尋與捲動位置。 */
    override fun init() {
        language = Language.getInstance()
        state = browser.snapshot(presenter::translation)
        layout = RuleCatalogueScreenLayout.measure(
            width = width,
            height = height,
            clearWidth = textRenderer.getWidth(presenter.text(MinecraftRuleCatalogueScreenKeys.CLEAR)) + CLEAR_PADDING,
            sourceToggle = openingContext.source != RuleCatalogueConfigSource.GENERAL,
        )
        addRuleButton()
        addCategoryButton()
        addSearchControls()
        layout.sourceToggle?.let(::addSourceToggle)
        button(
            label = presenter.text(if (exit.returnScreen == null) MinecraftRuleCatalogueScreenKeys.CLOSE else MinecraftRuleCatalogueScreenKeys.BACK),
            bounds = layout.close,
        ) { close() }
        remeasureContent()
        scroll.relayout(layout = layout, contentHeight = contentHeight)
    }

    /** 規則切換按鈕；只有一個規則時停用。依一般說明顯示時，提示會說明沒有套用對局設定。 */
    private fun addRuleButton() {
        val options = state.ruleOptions
        val selected = options.firstOrNull { it.ruleModuleId == state.selectedRuleModuleId }
        val name = selected?.displayName ?: NO_SELECTION
        val description = listOfNotNull(
            MinecraftRuleCatalogueScreenKeys.RULE_TOOLTIP,
            MinecraftRuleCatalogueScreenKeys.GENERAL_NOTE.takeIf { state.configSource == RuleCatalogueConfigSource.GENERAL },
        )
        button(label = presenter.format(MinecraftRuleCatalogueScreenKeys.RULE_BUTTON, name), bounds = layout.rule) {
            val next = options[CycleButtonInput.nextIndex(currentIndex = options.indexOf(selected), size = options.size, step = CycleButtonInput.step())]
            changed(browser.selectRule(next.ruleModuleId))
        }.apply {
            active = options.size > 1
            tooltip = Tooltip.of(
                CycleButtonInput.withHint(
                    tooltip = optionTooltip(
                        descriptionKeys = description,
                        current = name,
                        options = options.map { it.displayName },
                    ),
                    optionCount = options.size,
                    active = active,
                ),
            )
        }
    }

    /** 分類切換按鈕；沒有分類時停用。 */
    private fun addCategoryButton() {
        val categories = state.catalogue?.categories.orEmpty()
        val options = listOf(null to MinecraftRuleCatalogueScreenKeys.ALL_CATEGORIES) + categories.map { it.id to it.nameTranslationKey }
        val current = options.firstOrNull { it.first == state.categoryId } ?: options.first()
        val name = presenter.text(current.second)
        button(label = presenter.format(MinecraftRuleCatalogueScreenKeys.CATEGORY_BUTTON, name), bounds = layout.category) {
            changed(browser.selectCategory(CycleButtonInput.next(options = options, current = current).first))
        }.apply {
            active = categories.isNotEmpty()
            tooltip = Tooltip.of(
                CycleButtonInput.withHint(
                    tooltip = optionTooltip(
                        descriptionKeys = listOf(MinecraftRuleCatalogueScreenKeys.CATEGORY_TOOLTIP),
                        current = name,
                        options = options.map { presenter.text(it.second) },
                    ),
                    optionCount = options.size,
                    active = active,
                ),
            )
        }
    }

    /** 搜尋輸入框與清除按鈕。 */
    private fun addSearchControls() {
        val bounds = layout.search
        search = TextFieldWidget(
            textRenderer,
            bounds.x + TEXT_FIELD_BORDER,
            bounds.y + TEXT_FIELD_BORDER,
            (bounds.width - TEXT_FIELD_BORDER * 2).coerceAtLeast(1),
            bounds.height - TEXT_FIELD_BORDER * 2,
            Text.literal(presenter.text(MinecraftRuleCatalogueScreenKeys.SEARCH)),
        )
        search.setMaxLength(MAX_SEARCH_LENGTH)
        search.text = state.searchText
        search.setPlaceholder(Text.literal(presenter.text(MinecraftRuleCatalogueScreenKeys.SEARCH_HINT)).formatted(Formatting.DARK_GRAY))
        search.tooltip = Tooltip.of(Text.literal(presenter.text(MinecraftRuleCatalogueScreenKeys.SEARCH_TOOLTIP)).formatted(Formatting.GRAY))
        search.setChangedListener(::searchChanged)
        addDrawableChild(search)
        clear = button(label = presenter.text(MinecraftRuleCatalogueScreenKeys.CLEAR), bounds = layout.clear) { search.text = "" }
        clear.active = state.searchText.isNotEmpty()
    }

    /**
     * 在開啟時的設定與一般說明之間切換的按鈕，只在從房間或歷史開啟時出現；切換到其他規則後停用。
     *
     * @param bounds 按鈕範圍。
     */
    private fun addSourceToggle(bounds: CatalogueRect) {
        val onOpeningRule = state.selectedRuleModuleId == openingContext.ruleModuleId
        val current = presenter.text(catalogueSourceKey(state.configSource))
        val description = listOfNotNull(
            MinecraftRuleCatalogueScreenKeys.SOURCE_TOOLTIP,
            MinecraftRuleCatalogueScreenKeys.SOURCE_OTHER_RULE.takeUnless { onOpeningRule },
        )
        button(label = presenter.format(MinecraftRuleCatalogueScreenKeys.SOURCE_BUTTON, current), bounds = bounds) {
            changed(if (state.configSource == RuleCatalogueConfigSource.GENERAL) browser.restoreOpeningConfig() else browser.useGeneralExplanation())
        }.apply {
            active = onOpeningRule
            tooltip = Tooltip.of(
                optionTooltip(
                    descriptionKeys = description,
                    current = current,
                    options = listOf(openingContext.source, RuleCatalogueConfigSource.GENERAL).map { presenter.text(catalogueSourceKey(it)) },
                ),
            )
        }
    }

    /**
     * 建立選項按鈕的提示：灰色說明、綠色目前選項、金色選項標題，以及逐項列出的選項（目前選項為綠色）。
     *
     * @param descriptionKeys 說明文字的翻譯鍵，每個一行。
     * @param current 目前選項名稱。
     * @param options 所有選項名稱。
     * @return 提示文字。
     */
    private fun optionTooltip(
        descriptionKeys: List<String>,
        current: String,
        options: List<String>,
    ): Text {
        val result = Text.empty()
        descriptionKeys.forEach { key -> result.append(Text.literal(presenter.text(key)).formatted(Formatting.GRAY)).append("\n") }
        result.append(Text.literal(presenter.format(MinecraftRuleCatalogueScreenKeys.TOOLTIP_CURRENT, current)).formatted(Formatting.GREEN))
        result.append("\n").append(Text.literal(presenter.text(MinecraftRuleCatalogueScreenKeys.TOOLTIP_OPTIONS)).formatted(Formatting.GOLD))
        options.forEach { option ->
            result.append("\n• ").append(Text.literal(option).formatted(if (option == current) Formatting.GREEN else Formatting.WHITE))
        }
        return result
    }

    /**
     * 加入沿用跑馬燈行為的按鈕。
     *
     * @param label 按鈕文字。
     * @param bounds 按鈕範圍。
     * @param action 按下後的行為。
     * @return 已加入畫面的按鈕。
     */
    private fun button(
        label: String,
        bounds: CatalogueRect,
        action: () -> Unit,
    ): ButtonWidget = addDrawableChild(
        RestartableMarqueeButtonWidget.builder(Text.literal(label)) { action() }
            .dimensions(bounds.x, bounds.y, bounds.width, bounds.height)
            .build(),
    )

    /**
     * 規則、分類或設定來源確實改變時回到頂端並重建控制項。
     *
     * @param effective 瀏覽條件是否確實改變。
     */
    private fun changed(effective: Boolean) {
        if (!effective) return
        scroll.reset()
        clearAndInit()
    }

    /**
     * 搜尋文字改變時重新篩選並回到頂端；不重建輸入框，保留輸入焦點。
     *
     * @param text 新的搜尋文字。
     */
    private fun searchChanged(text: String) {
        if (!browser.setSearch(text)) return
        clear.active = text.isNotEmpty()
        remeasureContent()
        scroll.reset()
    }

    /** 依目前瀏覽快照重新測量所有 card。 */
    private fun remeasureContent() {
        state = browser.snapshot(presenter::translation)
        cards = state.entries.map { presenter.measure(entry = it, width = layout.cardWidth) }
        contentHeight = (cards.sumOf { it.height + CARD_GAP } - CARD_GAP).coerceAtLeast(0)
    }

    /**
     * 繪製標題、控制項與可捲動條目；條目的提示在內容裁切結束後才繪製，控制項的提示由原版提示框處理。
     *
     * @param context 繪製內容。
     * @param mouseX 游標水平位置。
     * @param mouseY 游標垂直位置。
     * @param delta 畫面更新比例。
     */
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        if (language !== Language.getInstance()) clearAndInit()
        renderBackground(context)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, layout.titleY, TITLE_COLOR)
        val contentTooltip = renderContent(context = context, mouseX = mouseX, mouseY = mouseY)
        renderScrollbar(context)
        super.render(context, mouseX, mouseY, delta)
        if (contentTooltip.isNotEmpty() && !scroll.isDragging) context.drawTooltip(textRenderer, contentTooltip, mouseX, mouseY)
    }

    /**
     * 在內容區內繪製與可見範圍相交的 card，或沒有條目時的說明。
     *
     * @param context 繪製內容。
     * @param mouseX 游標水平位置。
     * @param mouseY 游標垂直位置。
     * @return 游標指向的文字或牌面提示；沒有時為空集合。
     */
    private fun renderContent(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
    ): List<Text> {
        val bounds = layout.contentBounds
        if (bounds.height <= 0) return emptyList()
        val pointerInside = bounds.contains(mouseX.toDouble(), mouseY.toDouble())
        var tooltip = emptyList<String>()
        context.enableScissor(bounds.x, bounds.y, bounds.right, bounds.bottom)
        var top = bounds.y - scroll.offset.toInt()
        cards.forEach { card ->
            val rect = CatalogueRect(x = bounds.x, y = top, width = bounds.width, height = card.height)
            if (rect.intersects(bounds.y, bounds.bottom)) {
                val hovered = pointerInside && rect.contains(mouseX.toDouble(), mouseY.toDouble())
                context.fill(rect.x, rect.y, rect.right, rect.bottom, if (hovered) CARD_HOVER_BACKGROUND else CARD_BACKGROUND)
                card.lines.forEach { line ->
                    context.drawTextWithShadow(textRenderer, line.text, rect.x + line.x, rect.y + line.y, line.color)
                    val area = CatalogueRect(x = rect.x + line.x, y = rect.y + line.y, width = line.width, height = LINE_HIT_HEIGHT)
                    if (hovered && line.tooltip.isNotEmpty() && area.contains(mouseX.toDouble(), mouseY.toDouble())) tooltip = line.tooltip
                }
                card.tiles.forEach { tile ->
                    val placement = tile.placement
                    tileFaces.renderGui(
                        context = context,
                        assetKey = tile.assetKey,
                        x = rect.x + placement.x,
                        y = rect.y + placement.y,
                        width = placement.width,
                        height = placement.height,
                    )
                    val area = CatalogueRect(x = rect.x + placement.x, y = rect.y + placement.y, width = placement.width, height = placement.height)
                    if (hovered && tile.tooltip.isNotEmpty() && area.contains(mouseX.toDouble(), mouseY.toDouble())) tooltip = tile.tooltip
                }
            }
            top += card.height + CARD_GAP
        }
        catalogueStatusKey(state.status)?.let { key ->
            textRenderer.wrapLines(Text.literal(presenter.text(key)), bounds.width).forEachIndexed { index, line ->
                context.drawTextWithShadow(textRenderer, line, bounds.x, bounds.y + RuleCatalogueScreenLayout.GAP + index * LINE_HIT_HEIGHT, STATUS_COLOR)
            }
        }
        context.disableScissor()
        return tooltip.map(Text::literal)
    }

    /**
     * 內容超出可見範圍時繪製捲軸。
     *
     * @param context 繪製內容。
     */
    private fun renderScrollbar(context: DrawContext) {
        val bar = layout.scrollbar(contentHeight = contentHeight, scroll = scroll.offset)
        if (bar.maximumScroll == 0) return
        val track = layout.scrollbarBounds
        context.fill(track.x, track.y, track.right, track.bottom, TRACK_COLOR)
        context.fill(track.x, bar.thumbTop, track.right, bar.thumbTop + bar.thumbHeight, THUMB_COLOR)
    }

    /**
     * 游標位於內容區或捲軸時以滾輪捲動內容。
     *
     * @param mouseX 游標水平位置。
     * @param mouseY 游標垂直位置。
     * @param amount 滾輪方向與幅度。
     * @return 是否處理事件。
     */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (layout.contentBounds.contains(mouseX, mouseY) || layout.scrollbarBounds.contains(mouseX, mouseY)) {
            scroll.scrollBy(amount = amount, layout = layout, contentHeight = contentHeight)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    /**
     * 在捲軸上按下左鍵時開始拖曳；其他位置交給控制項處理。
     *
     * @param mouseX 游標水平位置。
     * @param mouseY 游標垂直位置。
     * @param button 滑鼠按鈕。
     * @return 是否處理事件。
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && layout.scrollbarBounds.contains(mouseX, mouseY) && scroll.press(mouseY = mouseY, layout = layout, contentHeight = contentHeight)) {
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /**
     * 拖曳捲軸時持續更新內容位置，游標離開捲軸也一樣。
     *
     * @param mouseX 游標水平位置。
     * @param mouseY 游標垂直位置。
     * @param button 滑鼠按鈕。
     * @param deltaX 水平移動量。
     * @param deltaY 垂直移動量。
     * @return 是否處理事件。
     */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (button == 0 && scroll.drag(mouseY = mouseY, layout = layout, contentHeight = contentHeight)) return true
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    /**
     * 放開左鍵時結束拖曳。
     *
     * @param mouseX 游標水平位置。
     * @param mouseY 游標垂直位置。
     * @param button 滑鼠按鈕。
     * @return 是否處理事件。
     */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && scroll.release()) return true
        return super.mouseReleased(mouseX, mouseY, button)
    }

    /** 依開啟來源返回原畫面或回到遊戲。 */
    override fun close() {
        client?.let(exit::back)
    }

    /** 開啟規則一覽的來源失效時直接關回遊戲。 */
    override fun tick() {
        super.tick()
        val client = client ?: return
        if (!exit.isValid(client)) client.setScreen(null)
    }

    /** 通知開啟來源規則一覽已被移除。 */
    override fun removed() {
        exit.removed(this)
        super.removed()
    }

    /** 規則一覽不暫停單人遊戲。 */
    override fun shouldPause(): Boolean = false

    /** 畫面配色與尺寸。 */
    private companion object {
        /** 沒有任何規則可選時按鈕上顯示的值。 */
        const val NO_SELECTION = "-"

        /** 搜尋字數上限。 */
        const val MAX_SEARCH_LENGTH = 256

        /** 輸入框外框寬度。 */
        const val TEXT_FIELD_BORDER = 1

        /** 清除按鈕在文字左右的留白合計。 */
        const val CLEAR_PADDING = 12

        /** card 之間的間距。 */
        const val CARD_GAP = 4

        /** 一行文字的命中高度。 */
        const val LINE_HIT_HEIGHT = 10

        /** 標題。 */
        const val TITLE_COLOR = 0xffffff

        /** 沒有條目時的說明。 */
        const val STATUS_COLOR = 0xaaaaaa

        /** card 背景。 */
        const val CARD_BACKGROUND = 0x70303030

        /** 游標指向 card 時的背景。 */
        const val CARD_HOVER_BACKGROUND = 0x90404040.toInt()

        /** 捲軸軌道。 */
        const val TRACK_COLOR = 0x80505050.toInt()

        /** 捲軸滑塊。 */
        const val THUMB_COLOR = 0xffd0d0d0.toInt()
    }
}
