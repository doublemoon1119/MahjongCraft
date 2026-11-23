package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.catalogue

import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.BuiltInRuleCatalogueCategoryIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.MinecraftRuleCatalogueScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowseContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowseState
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowser
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueConfigSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ControlTooltip
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.DropdownOption
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.DropdownOverlay
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.MahjongScreen
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.PixelScrollState
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.TabWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.applyTabScroll
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongTileFaceRenderer
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

/**
 * 可切換規則、分類與搜尋的唯讀規則一覽；上方控制區、分類頁籤與底部按鈕固定，只有中間的條目會捲動。
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
) : MahjongScreen(Component.translatable(MinecraftRuleCatalogueScreenKeys.TITLE)) {
    /** 返回時回到的畫面；直接關回遊戲時為 null。 */
    internal val returnScreen: Screen? get() = exit.returnScreen

    /** 目前的瀏覽快照。 */
    private var state: RuleCatalogueBrowseState = browser.snapshot(presenter::translation)

    /** 目前的畫面幾何。 */
    private var layout = RuleCatalogueScreenLayout.measure(
        width = 1,
        height = 1,
        clearWidth = 0,
        sourceLabelWidth = null,
    )

    /** 目前條目的 card；只在內容、語言或尺寸改變時重新測量。 */
    private var cards = emptyList<CatalogueCard>()

    /** 全部 card 與間距的總高度。 */
    private var contentHeight = 0

    /** 內容捲動與捲軸拖曳。 */
    private val scroll = PixelScrollState()

    /** 上次建立畫面時的語言，用於偵測語言切換。 */
    private var language: Language = Language.getInstance()

    /** 規則與設定來源選擇器共用的展開清單。 */
    private val dropdown = DropdownOverlay()

    /** 分類頁籤列向左捲動的像素數。 */
    private var tabScroll = 0

    /** 下次建立控制項時是否捲動頁籤列，讓選中的頁籤完整顯示。 */
    private var revealSelectedTab = true

    /** 搜尋輸入框。 */
    private lateinit var search: EditBox

    /** 清除搜尋按鈕。 */
    private lateinit var clear: Button

    /** 視窗大小改變後重建控制項，捲到選中的頁籤，並保留原本有鍵盤焦點的頁籤。 */
    override fun resize(client: Minecraft, width: Int, height: Int) {
        val focusedIndex = children().filterIsInstance<TabWidget>().indexOfFirst { it === focused }
        revealSelectedTab = true
        super.resize(client, width, height)
        children().filterIsInstance<TabWidget>().getOrNull(focusedIndex)?.let { tab ->
            setFocused(tab)
        }
    }

    /**
     * 依目前尺寸、語言與資源建立控制項並重新測量內容；保留規則、分類、搜尋與捲動位置。
     *
     * 資源重新載入後畫面會重新初始化，因此在這裡丟棄牌面圖案的快取，補上或移除的貼圖立即反映。
     */
    override fun init() {
        dropdown.close()
        presenter.clearResourceCaches()
        language = Language.getInstance()
        state = browser.snapshot(presenter::translation)
        layout = RuleCatalogueScreenLayout.measure(
            width = width,
            height = height,
            clearWidth = font.width(presenter.text(MinecraftRuleCatalogueScreenKeys.CLEAR)) + CLEAR_PADDING,
            sourceLabelWidth = sourceLabel().takeIf { openingContext.source != RuleCatalogueConfigSource.GENERAL }?.let(font::width),
        )
        addRuleSelector()
        addCategoryTabs()
        addSearchControls()
        layout.sourceSelector?.let(::addSourceSelector)
        button(
            label = presenter.text(if (exit.returnScreen == null) MinecraftRuleCatalogueScreenKeys.CLOSE else MinecraftRuleCatalogueScreenKeys.BACK),
            bounds = layout.close,
        ) { onClose() }
        remeasureContent()
        scroll.relayout(layout = layout, contentHeight = contentHeight)
    }

    /** 規則選擇器，點擊展開規則清單；只有一個規則時停用。依一般說明顯示時，提示會說明沒有套用對局設定。 */
    private fun addRuleSelector() {
        val options = state.ruleOptions
        val selected = options.firstOrNull { it.ruleModuleId == state.selectedRuleModuleId }
        val name = selected?.displayName ?: NO_SELECTION
        val description = listOfNotNull(
            MinecraftRuleCatalogueScreenKeys.RULE_TOOLTIP,
            MinecraftRuleCatalogueScreenKeys.GENERAL_NOTE.takeIf { state.configSource == RuleCatalogueConfigSource.GENERAL },
        )
        lateinit var selector: RestartableMarqueeButtonWidget
        selector = button(label = name, bounds = layout.rule) {
            dropdown.toggle(
                key = RULE_SELECTOR_KEY,
                anchor = selector,
                options = options.map { DropdownOption(label = Component.literal(it.displayName)) },
                selectedIndex = options.indexOf(selected).takeIf { it >= 0 },
            ) { index -> changed(browser.selectRule(options[index].ruleModuleId)) }
        }
        selector.active = options.size > 1
        selector.selectorExpanded = { dropdown.isOpenFor(RULE_SELECTOR_KEY) }
        selector.tooltip = Tooltip.create(optionTooltip(descriptionKeys = description, current = name, options = options.map { it.displayName }))
    }

    /** 分類頁籤，「全部分類」排第一；頁籤放不下時可左右捲動，選中的頁籤保持看得見。 */
    private fun addCategoryTabs() {
        val options = categoryOptions()
        val selectedIndex = options.indexOfFirst { it.first == state.categoryId }.coerceAtLeast(0)
        if (revealSelectedTab) {
            tabScroll = layout.tabStrip(tabCount = options.size, scroll = tabScroll).scrollRevealing(selectedIndex)
            revealSelectedTab = false
        }
        val strip = layout.tabStrip(tabCount = options.size, scroll = tabScroll)
        tabScroll = strip.effectiveScroll
        options.forEachIndexed { index, (categoryId, nameKey) ->
            val selected = index == selectedIndex
            addRenderableWidget(
                TabWidget(
                    x = strip.tabX(index),
                    y = strip.tabY(selected),
                    width = strip.tabWidth,
                    height = strip.tabHeight(selected),
                    message = Component.literal(presenter.text(nameKey)),
                    selected = selected,
                    clipLeft = strip.left,
                    clipRight = strip.right,
                    onFocus = {
                        tabScroll = layout.tabStrip(tabCount = options.size, scroll = tabScroll).scrollRevealing(index)
                        children().filterIsInstance<TabWidget>().applyTabScroll(layout.tabStrip(tabCount = options.size, scroll = tabScroll))
                    },
                    onSelect = {
                        revealSelectedTab = true
                        changed(browser.selectCategory(categoryId))
                    },
                ),
            )
        }
    }

    /**
     * 分類頁籤的選項：分類識別碼（全部分類為 null）與名稱翻譯鍵。
     *
     * 各規則共用的「基本玩法」分類排在「全部分類」之前，其他分類照目錄順序排在後面。
     */
    private fun categoryOptions(): List<Pair<String?, String>> {
        val (basics, others) = state.catalogue?.categories.orEmpty().partition { it.id == BuiltInRuleCatalogueCategoryIds.BASICS }
        return basics.map { it.id to it.nameTranslationKey } +
            listOf<Pair<String?, String>>(null to MinecraftRuleCatalogueScreenKeys.ALL_CATEGORIES) +
            others.map { it.id to it.nameTranslationKey }
    }

    /** 搜尋輸入框與清除按鈕。 */
    private fun addSearchControls() {
        val bounds = layout.search
        search = EditBox(
            font,
            bounds.x + TEXT_FIELD_BORDER,
            bounds.y + TEXT_FIELD_BORDER,
            (bounds.width - TEXT_FIELD_BORDER * 2).coerceAtLeast(1),
            bounds.height - TEXT_FIELD_BORDER * 2,
            Component.literal(presenter.text(MinecraftRuleCatalogueScreenKeys.SEARCH)),
        )
        search.setMaxLength(MAX_SEARCH_LENGTH)
        search.setValue(state.searchText)
        search.setHint(Component.literal(presenter.text(MinecraftRuleCatalogueScreenKeys.SEARCH_HINT)).withStyle(ChatFormatting.DARK_GRAY))
        search.tooltip = Tooltip.create(Component.literal(presenter.text(MinecraftRuleCatalogueScreenKeys.SEARCH_TOOLTIP)).withStyle(ChatFormatting.GRAY))
        search.setResponder(::searchChanged)
        addRenderableWidget(search)
        clear = button(label = presenter.text(MinecraftRuleCatalogueScreenKeys.CLEAR), bounds = layout.clear) { search.setValue("") }
        clear.active = state.searchText.isNotEmpty()
    }

    /**
     * 設定來源選擇器，點擊展開「開啟時的設定」與「一般說明」；只在從房間或歷史開啟時出現，切換到其他規則後停用。
     *
     * @param bounds 選擇器範圍。
     */
    private fun addSourceSelector(bounds: CatalogueRect) {
        val onOpeningRule = state.selectedRuleModuleId == openingContext.ruleModuleId
        val sources = listOf(openingContext.source, RuleCatalogueConfigSource.GENERAL)
        val names = sources.map { presenter.text(catalogueSourceKey(it)) }
        val current = presenter.text(catalogueSourceKey(state.configSource))
        val description = listOfNotNull(
            MinecraftRuleCatalogueScreenKeys.SOURCE_TOOLTIP,
            MinecraftRuleCatalogueScreenKeys.SOURCE_OTHER_RULE.takeUnless { onOpeningRule },
        )
        lateinit var selector: RestartableMarqueeButtonWidget
        selector = button(label = current, bounds = bounds) {
            dropdown.toggle(
                key = SOURCE_SELECTOR_KEY,
                anchor = selector,
                options = names.map { DropdownOption(label = Component.literal(it)) },
                selectedIndex = sources.indexOf(state.configSource).takeIf { it >= 0 },
            ) { index ->
                changed(if (sources[index] == RuleCatalogueConfigSource.GENERAL) browser.useGeneralExplanation() else browser.restoreOpeningConfig())
            }
        }
        selector.active = onOpeningRule
        selector.selectorExpanded = { dropdown.isOpenFor(SOURCE_SELECTOR_KEY) }
        selector.tooltip = Tooltip.create(optionTooltip(descriptionKeys = description, current = current, options = names))
    }

    /** 設定來源選擇器左側的標籤。 */
    private fun sourceLabel(): Component = Component.literal(presenter.text(MinecraftRuleCatalogueScreenKeys.SOURCE_LABEL))

    /**
     * 建立選項按鈕的提示：說明、全部選項與目前選項，以及可操作時的操作提示。
     *
     * @param descriptionKeys 說明文字的翻譯鍵，每個一行。
     * @param current 目前選項名稱。
     * @param options 所有選項名稱。
     * @param hints 操作提示；不需要提示時為空。
     * @return 提示文字。
     */
    private fun optionTooltip(
        descriptionKeys: List<String>,
        current: String,
        options: List<String>,
        hints: List<Component> = emptyList(),
    ): Component = ControlTooltip.options(
        description = Component.literal(descriptionKeys.joinToString("\n") { presenter.text(it) }),
        labels = options.map { Component.literal(it) },
        currentIndex = options.indexOf(current),
        hints = hints,
    )

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
    ): RestartableMarqueeButtonWidget = addRenderableWidget(
        RestartableMarqueeButtonWidget.builder(Component.literal(label)) { action() }
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
        rebuildWidgets()
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
    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        if (language !== Language.getInstance()) rebuildWidgets()
        renderScreenBackground(context, mouseX, mouseY, delta)
        ScreenRendering.title(context, font, title, width)
        val pointerX = dropdown.pointer(mouseX)
        val pointerY = dropdown.pointer(mouseY)
        val contentTooltip = renderContent(context = context, mouseX = pointerX, mouseY = pointerY)
        renderScrollbar(context)
        ScreenRendering.tabLine(context, layout.tabStrip(tabCount = categoryOptions().size, scroll = tabScroll))
        layout.sourceLabelY?.let { labelY ->
            context.drawString(font, sourceLabel(), layout.contentLeft, labelY, SOURCE_LABEL_COLOR)
        }
        renderWidgets(context, pointerX, pointerY, delta)
        if (contentTooltip.isNotEmpty() && !scroll.isDragging) context.renderComponentTooltip(font, contentTooltip, mouseX, mouseY)
        dropdown.render(context, font, height, mouseX, mouseY)
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
        context: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ): List<Component> {
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
                    context.drawString(font, line.text, rect.x + line.x, rect.y + line.y, line.color)
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
            font.split(Component.literal(presenter.text(key)), bounds.width).forEachIndexed { index, line ->
                context.drawString(font, line, bounds.x, bounds.y + RuleCatalogueScreenLayout.GAP + index * LINE_HIT_HEIGHT, STATUS_COLOR)
            }
        }
        context.disableScissor()
        return tooltip.map(Component::literal)
    }

    /**
     * 內容超出可見範圍時繪製捲軸。
     *
     * @param context 繪製內容。
     */
    private fun renderScrollbar(context: GuiGraphics) {
        val bar = layout.scrollbar(contentHeight = contentHeight, scroll = scroll.offset)
        if (bar.maximumScroll == 0) return
        val track = layout.scrollbarBounds
        VerticalScrollbar.renderWithin(context, track.x, track.right, bar)
    }

    /**
     * 游標位於內容區或捲軸時以滾輪捲動內容。
     *
     * @param mouseX 游標水平位置。
     * @param mouseY 游標垂直位置。
     * @param amount 滾輪方向與幅度。
     * @return 是否處理事件。
     */
    override fun mouseWheelScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (dropdown.mouseScrolled(font, height, amount)) return true
        val strip = layout.tabStrip(tabCount = categoryOptions().size, scroll = tabScroll)
        if (strip.contains(mouseX, mouseY) && strip.maximumScroll > 0) {
            val next = strip.scrollByWheel(amount)
            if (next != tabScroll) {
                tabScroll = next
                children().filterIsInstance<TabWidget>().applyTabScroll(strip.copy(scroll = next))
            }
            return true
        }
        if (layout.contentBounds.contains(mouseX, mouseY) || layout.scrollbarBounds.contains(mouseX, mouseY)) {
            scroll.scrollBy(amount = amount, layout = layout, contentHeight = contentHeight)
            return true
        }
        return super.mouseWheelScrolled(mouseX, mouseY, amount)
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
        if (dropdown.mouseClicked(font, height, mouseX, mouseY, button)) return true
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
        if (dropdown.isOpen) return true
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

    /** 下拉清單展開時不讓底層元件排入提示框。 */
    override fun setTooltipForNextRenderPass(lines: List<FormattedCharSequence>, positioner: ClientTooltipPositioner, focused: Boolean) {
        if (!dropdown.isOpen) super.setTooltipForNextRenderPass(lines, positioner, focused)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (dropdown.keyPressed(font, height, keyCode)) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean = dropdown.isOpen || super.charTyped(chr, modifiers)

    /** 依開啟來源返回原畫面或回到遊戲。 */
    override fun onClose() {
        minecraft?.let(exit::back)
    }

    /** 開啟規則一覽的來源失效時直接關回遊戲。 */
    override fun tick() {
        super.tick()
        val client = minecraft ?: return
        if (!exit.isValid(client)) client.setScreen(null)
    }

    /** 通知開啟來源規則一覽已被移除。 */
    override fun removed() {
        exit.removed(this)
        super.removed()
    }

    /** 規則一覽不暫停單人遊戲。 */
    override fun isPauseScreen(): Boolean = false

    /** 畫面配色與尺寸。 */
    private companion object {
        /** 沒有任何規則可選時按鈕上顯示的值。 */
        const val NO_SELECTION = "-"

        /** 規則選擇器展開清單的識別值。 */
        const val RULE_SELECTOR_KEY = "rule"

        /** 設定來源選擇器展開清單的識別值。 */
        const val SOURCE_SELECTOR_KEY = "source"

        /** 設定來源標籤的文字顏色。 */
        const val SOURCE_LABEL_COLOR = 0xCCCCCC

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

        /** 沒有條目時的說明。 */
        const val STATUS_COLOR = 0xaaaaaa

        /** card 背景。 */
        const val CARD_BACKGROUND = 0x70303030

        /** 游標指向 card 時的背景。 */
        const val CARD_HOVER_BACKGROUND = 0x90404040.toInt()
    }
}
