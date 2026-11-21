package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ControlTooltip
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.TabStripLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.TabWidget
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.VerticalScrollbar
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.applyTabScroll
import com.doublemoon1119.mahjongcraft.platform.fabric.text.gameConfigPresentationText
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowseContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigFieldDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigVariantResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.ResolvedGameConfigPresentation
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * 顯示牌譜對局開局時的規則設定；版面與房間設定頁相同，以分類頁籤切換，每一列左邊是欄位名稱、右邊是設定值，
 * 所有欄位都不能修改。
 *
 * @property session 共用歷史瀏覽 session。
 */
internal class HistoryRuleSettingsScreen(
    private val session: HistoryBrowseSession,
) : Screen(Component.translatable(MinecraftHistoryScreenKeys.RULE_SETTINGS_TITLE)) {
    /** 第一個可見欄位的索引。 */
    private var scrollIndex = 0

    /** 捲軸是否正在拖曳。 */
    private var dragging = false

    /** 捲軸拖曳時游標相對滑塊上界的偏移。 */
    private var grabOffset = 0.0

    /** 目前選中的分類；尚未選擇時為 null，改用第一個分類。 */
    private var selectedCategoryId: String? = null

    /** 分類頁籤列向左捲動的像素數。 */
    private var tabScroll = 0

    /** 下次建立頁籤時是否捲動頁籤列，讓選中的頁籤完整顯示。 */
    private var revealSelectedTab = true

    /** 上一次解析的 DTO，避免每個 render tick 重建規則設定。 */
    private var resolvedDto: GameConfigDto? = null

    /** 上一次成功解析的呈現資料。 */
    private var resolved: ResolvedGameConfigPresentation? = null

    /** 建立頁籤時使用的呈現資料；設定載入或改變後重新建立頁籤。 */
    private var builtFor: ResolvedGameConfigPresentation? = null

    /** 失敗時可重新查詢的按鈕。 */
    private var retryButton: Button? = null

    /** 設定載入後開啟規則一覽的按鈕，與重試按鈕共用位置。 */
    private var catalogueButton: Button? = null

    /** 上一次建立規則一覽脈絡時的開局設定與摘要規則，避免每個 render tick 重新解碼。 */
    private var catalogueContextKey: Pair<GameConfigDto?, String?>? = null

    /** 依保存設定建立的規則一覽脈絡；沒有可用規則時為 null。 */
    private var catalogueContext: RuleCatalogueBrowseContext? = null

    /** 目前畫面的版面。 */
    private val layout: FormScreenLayout
        get() = FormScreenLayout(windowWidth = width, windowHeight = height, hasPageSwitch = false, hasRuleRow = false, headerHeight = HEADER_HEIGHT)

    override fun init() {
        dragging = false
        clearWidgets()
        val layout = layout
        val footer = layout.footer(buttonCount = 2)
        retryButton = addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.RETRY)) { session.controller.retry() }
                .dimensions(footer.buttonX(0), layout.footerY, footer.buttonWidth(0), FormScreenLayout.BUTTON_HEIGHT)
                .build(),
        )
        catalogueButton = addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.RULE_CATALOGUE)) {
                catalogueContext?.let { session.openRuleCatalogue(settings = this, context = it) }
            }
                .dimensions(footer.buttonX(0), layout.footerY, footer.buttonWidth(0), FormScreenLayout.BUTTON_HEIGHT)
                .build()
                .also { it.tooltip = Tooltip.create(Component.translatable(MinecraftHistoryScreenKeys.RULE_CATALOGUE_TOOLTIP).withStyle(ChatFormatting.GRAY)) },
        )
        addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.BACK)) { session.backToSummary() }
                .dimensions(footer.buttonX(1), layout.footerY, footer.buttonWidth(1), FormScreenLayout.BUTTON_HEIGHT)
                .build(),
        )
        builtFor = presentation()
        addTabs(layout)
    }

    /**
     * 建立分類頁籤；設定尚未載入或沒有呈現定義時不建立。
     *
     * @param layout 目前版面。
     */
    private fun addTabs(layout: FormScreenLayout) {
        val categories = builtFor?.definition?.categories.orEmpty()
        if (categories.isEmpty()) return
        val selectedIndex = categories.indexOfFirst { it.id == selectedCategoryId }.coerceAtLeast(0)
        if (revealSelectedTab) {
            tabScroll = tabStrip(layout, categories.size).scrollRevealing(selectedIndex)
            revealSelectedTab = false
        }
        val strip = tabStrip(layout, categories.size)
        tabScroll = strip.effectiveScroll
        categories.forEachIndexed { index, category ->
            val selected = index == selectedIndex
            addRenderableWidget(
                TabWidget(
                    x = strip.tabX(index),
                    y = strip.tabY(selected),
                    width = strip.tabWidth,
                    height = strip.tabHeight(selected),
                    message = Component.translatable(category.nameTranslationKey),
                    selected = selected,
                    clipLeft = strip.left,
                    clipRight = strip.right,
                    onFocus = {
                        tabScroll = tabStrip(layout, categories.size).scrollRevealing(index)
                        children().filterIsInstance<TabWidget>().applyTabScroll(tabStrip(layout, categories.size))
                    },
                    onSelect = {
                        selectedCategoryId = category.id
                        revealSelectedTab = true
                        scrollIndex = 0
                        rebuildWidgets()
                    },
                ),
            )
        }
    }

    /**
     * 依目前捲動位置建立分類頁籤列版面。
     *
     * @param layout 目前版面。
     * @param categoryCount 分類數量。
     * @return 頁籤列版面。
     */
    private fun tabStrip(layout: FormScreenLayout, categoryCount: Int): TabStripLayout = TabStripLayout(
        left = layout.contentLeft,
        width = layout.contentWidth,
        top = layout.tabsTop,
        tabCount = categoryCount,
        scroll = tabScroll,
    )

    /** 目前分類的欄位；設定尚未載入或沒有呈現定義時為空。 */
    private fun currentFields(): List<GameConfigFieldDefinition> {
        val definition = builtFor?.definition ?: return emptyList()
        val categoryId = definition.categories.firstOrNull { it.id == selectedCategoryId }?.id ?: definition.categories.firstOrNull()?.id
        return GameConfigVariantResolver.visibleFields(definition).filter { it.categoryId == categoryId }
    }

    /** 最多可捲動的欄位數。 */
    private fun maximumScroll(): Int = (currentFields().size - layout.visibleFieldCount).coerceAtLeast(0)

    /** 已載入的設定解析後的呈現資料；尚未載入或無法解析時為 null。 */
    private fun presentation(): ResolvedGameConfigPresentation? {
        val ruleSettings = session.controller.state.value.ruleSettings
        if (ruleSettings?.status != HistoryBrowseStatus.Ready) return null
        return resolvedConfig(ruleSettings.config)
    }

    /**
     * 依保存的開局設定建立規則一覽脈絡並快取；設定解不出時改用摘要記錄的規則。
     *
     * @param configDto 歷史伺服器回傳的開局設定，尚未取得時為 null。
     * @return 規則一覽脈絡；沒有可用規則時為 null。
     */
    private fun ruleCatalogueContext(configDto: GameConfigDto?): RuleCatalogueBrowseContext? {
        val summaryRuleModuleId = session.controller.state.value.summary?.detail?.summary?.ruleId
        val key = configDto to summaryRuleModuleId
        if (key != catalogueContextKey) {
            catalogueContextKey = key
            val config = configDto?.let { runCatching { it.toDomain(session.networkRegistries) }.getOrNull() }
            catalogueContext = historyRuleCatalogueContext(
                decodedRuleModuleId = config?.let { runCatching { session.configResolver.resolve(it).ruleModuleId }.getOrNull() },
                decodedRuleConfig = config?.ruleConfig,
                summaryRuleModuleId = summaryRuleModuleId,
            )
        }
        return catalogueContext
    }

    /**
     * 解析規則設定並快取結果，避免每個 render tick 重建規則設定。
     *
     * @param configDto 歷史伺服器回傳的開局設定，尚未取得時為 null。
     * @return 本地可解析的規則呈現；缺少 codec 或呈現註冊時為 null。
     */
    private fun resolvedConfig(configDto: GameConfigDto?): ResolvedGameConfigPresentation? {
        if (configDto !== resolvedDto) {
            resolvedDto = configDto
            resolved = configDto?.let { runCatching { session.configResolver.resolve(it.toDomain(session.networkRegistries)) }.getOrNull() }
        }
        return resolved
    }

    /** 歷史畫面不暫停整合伺服器。 */
    override fun isPauseScreen(): Boolean = false

    /** Escape 返回摘要。 */
    override fun onClose() {
        session.backToSummary()
    }

    /** Minecraft 移除畫面時通知瀏覽 session。 */
    override fun removed() {
        session.removed(this)
        super.removed()
    }

    /**
     * 游標在頁籤列上時左右捲動頁籤，在欄位區時逐列捲動欄位。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param amount 滾輪增量。
     * @return 是否處理此輸入。
     */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val layout = layout
        val strip = tabStrip(layout, builtFor?.definition?.categories?.size ?: 0)
        if (strip.contains(mouseX, mouseY) && strip.maximumScroll > 0) {
            val next = strip.scrollByWheel(amount)
            if (next != tabScroll) {
                tabScroll = next
                children().filterIsInstance<TabWidget>().applyTabScroll(strip.copy(scroll = next))
            }
            return true
        }
        if (mouseY >= layout.fieldsTop && mouseY < layout.fieldsBottom && amount != 0.0) {
            scrollIndex = (scrollIndex + if (amount < 0) 1 else -1).coerceIn(0, maximumScroll())
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    /**
     * 在捲軸上按下左鍵時開始拖曳。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param button 滑鼠按鍵。
     * @return 是否處理此輸入。
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val layout = layout
        val bar = layout.scrollbar(fieldCount = currentFields().size, scrollIndex = scrollIndex)
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && bar.maximumScroll > 0 && layout.isOverScrollbar(mouseX, mouseY)) {
            dragging = true
            grabOffset = bar.grabOffset(mouseY)
            scrollIndex = bar.scrollIndexFor(mouseY, grabOffset)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /**
     * 拖曳捲軸時逐列捲動欄位。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param button 滑鼠按鍵。
     * @param deltaX 水平位移。
     * @param deltaY 垂直位移。
     * @return 是否處理此輸入。
     */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            scrollIndex = layout.scrollbar(fieldCount = currentFields().size, scrollIndex = scrollIndex).scrollIndexFor(mouseY, grabOffset)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    /**
     * 放開左鍵時結束拖曳。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param button 滑鼠按鍵。
     * @return 是否處理此輸入。
     */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
        dragging = false
        return handled || super.mouseReleased(mouseX, mouseY, button)
    }

    /** 繪製規則名稱、唯讀說明、分類頁籤與目前分類的欄位。 */
    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        if (presentation() !== builtFor) rebuildWidgets()
        renderBackground(context)
        ScreenRendering.title(context, font, title, width)
        val layout = layout
        val state = session.controller.state.value
        val status = state.ruleSettings?.status ?: HistoryBrowseStatus.Loading
        retryButton?.active = session.controller.canRetry()
        retryButton?.visible = status is HistoryBrowseStatus.Failed
        catalogueButton?.visible = status == HistoryBrowseStatus.Ready && ruleCatalogueContext(state.ruleSettings?.config) != null
        retryButton?.tooltip = if (session.controller.isRefreshCoolingDown()) Tooltip.create(Component.translatable(MinecraftHistoryScreenKeys.QUERY_COOLDOWN_TOOLTIP)) else null
        renderHeader(context, layout)
        var hoveredTooltip: List<Component>? = null
        when (status) {
            HistoryBrowseStatus.Idle, HistoryBrowseStatus.Loading -> statusText(context, layout, Component.translatable(MinecraftHistoryScreenKeys.LOADING), STATUS_COLOR)
            is HistoryBrowseStatus.Failed -> statusText(context, layout, Component.translatable(status.reason.translationKey), ERROR_COLOR)
            HistoryBrowseStatus.Ready -> if (builtFor?.definition == null) {
                statusText(context, layout, Component.translatable(MinecraftHistoryScreenKeys.RULE_SETTINGS_UNAVAILABLE), ERROR_COLOR)
            } else {
                ScreenRendering.tabLine(context, tabStrip(layout, builtFor?.definition?.categories?.size ?: 0))
                hoveredTooltip = renderFields(context, layout, mouseX, mouseY)
            }
        }
        super.render(context, mouseX, mouseY, delta)
        hoveredTooltip?.let { context.renderComponentTooltip(font, it, mouseX, mouseY) }
    }

    /**
     * 繪製頁籤上方的規則名稱、玩法、規則識別碼與唯讀說明；規則沒有登記玩法時不顯示玩法。
     *
     * @param context 繪製上下文。
     * @param layout 目前版面。
     */
    private fun renderHeader(context: GuiGraphics, layout: FormScreenLayout) {
        val ruleId = session.controller.state.value.summary?.detail?.summary?.ruleId ?: resolved?.ruleModuleId
        val ruleName = HistoryScreenText.rule(ruleId, session.ruleNames)
        val variant = resolved?.takeIf { it.definition?.variants?.isNotEmpty() == true }?.variant
        val name = variant?.let { ruleName.copy().append(VARIANT_SEPARATOR).append(Component.translatable(it.nameTranslationKey)) } ?: ruleName
        val nameText = HistoryScreenText.trim(name.string, layout.contentWidth, font::width)
        context.drawString(font, Component.literal(nameText), layout.contentLeft, layout.headerTop, RULE_NAME_COLOR)
        val idLeft = layout.contentLeft + font.width(nameText) + ID_GAP
        if (ruleId != null && ruleId != name.string && idLeft < layout.contentRight) {
            val idText = HistoryScreenText.trim(ruleId, layout.contentRight - idLeft, font::width)
            context.drawString(font, Component.literal(idText), idLeft, layout.headerTop, SECONDARY_COLOR)
        }
        val note = HistoryScreenText.trim(Component.translatable(MinecraftHistoryScreenKeys.RULE_SETTINGS_READ_ONLY).string, layout.contentWidth, font::width)
        context.drawString(font, Component.literal(note), layout.contentLeft, layout.headerTop + HEADER_LINE_HEIGHT, NOTE_COLOR)
    }

    /**
     * 在欄位區上方繪製載入中、失敗或無法顯示的說明。
     *
     * @param context 繪製上下文。
     * @param layout 目前版面。
     * @param text 說明文字。
     * @param color 文字顏色。
     */
    private fun statusText(context: GuiGraphics, layout: FormScreenLayout, text: Component, color: Int) {
        context.drawCenteredString(font, text, width / 2, layout.fieldsTop + STATUS_OFFSET, color)
    }

    /**
     * 繪製目前分類的可見欄位：名稱靠左、設定值靠右，隔列加上底色。
     *
     * @param context 繪製上下文。
     * @param layout 目前版面。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 游標所指欄位的提示；沒有時為 null。
     */
    private fun renderFields(context: GuiGraphics, layout: FormScreenLayout, mouseX: Int, mouseY: Int): List<Component>? {
        val presentation = builtFor ?: return null
        val fields = currentFields()
        scrollIndex = scrollIndex.coerceIn(0, maximumScroll())
        var tooltip: List<Component>? = null
        repeat(layout.visibleFieldCount) { visibleIndex ->
            val index = scrollIndex + visibleIndex
            val field = fields.getOrNull(index) ?: return@repeat
            val rowTop = layout.fieldRowTop(visibleIndex)
            val stripe = layout.fieldStripe(visibleIndex)
            stripe.renderIfStriped(context, index)
            val label = HistoryScreenText.trim(Component.translatable(field.nameTranslationKey).string, layout.labelWidth, font::width)
            context.drawString(font, Component.literal(label), layout.contentLeft, layout.fieldLabelY(rowTop), LABEL_COLOR)
            val value = presentation.valuesByFieldId[field.id]
            val valueText = value?.let { gameConfigPresentationText(it, field.editor) } ?: Component.translatable(MinecraftHistoryScreenKeys.RULE_SETTINGS_UNAVAILABLE)
            val shown = HistoryScreenText.trim(valueText.string, layout.controlWidth, font::width)
            val valueY = layout.fieldControlY(rowTop) + (FormScreenLayout.BUTTON_HEIGHT - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2
            context.drawString(font, Component.literal(shown), layout.contentRight - font.width(shown), valueY, VALUE_COLOR)
            if (mouseX >= layout.contentLeft && mouseX < layout.contentRight && mouseY >= stripe.top && mouseY < stripe.bottom) {
                tooltip = fieldTooltip(field, value)
            }
        }
        VerticalScrollbar.render(context, layout.scrollbarLeft, layout.scrollbar(fieldCount = fields.size, scrollIndex = scrollIndex))
        return tooltip
    }

    /**
     * 建立欄位提示：說明，選項欄位再列出全部選項並標出開局時的設定。
     *
     * @param field 欄位定義。
     * @param value 開局時的設定值；無法取得時為 null。
     * @return 提示的每一行。
     */
    private fun fieldTooltip(field: GameConfigFieldDefinition, value: GameConfigPresentationValue?): List<Component> {
        val description = Component.translatable(field.descriptionTranslationKey)
        val editor = field.editor as? GameConfigEditorSpec.ChoiceEditor ?: return listOf(description.withStyle(ChatFormatting.GRAY))
        val selected = when (value) {
            is GameConfigPresentationValue.ChoiceValue -> setOf(value.optionId)
            is GameConfigPresentationValue.MultiChoiceValue -> value.optionIds
            else -> emptySet()
        }
        return ControlTooltip.optionLines(
            description = description,
            options = editor.optionIds.map { optionId ->
                ControlTooltip.Option(
                    label = editor.translationKeyFor(optionId)?.let(Component::translatable) ?: Component.literal(optionId),
                    current = optionId in selected,
                )
            },
        )
    }

    /** 文字顏色與頁首尺寸。 */
    private companion object {
        /** 頁籤上方規則名稱與唯讀說明所佔的高度。 */
        const val HEADER_HEIGHT = 26

        /** 頁首兩行文字的行距。 */
        const val HEADER_LINE_HEIGHT = 12

        /** 規則名稱與規則識別碼之間的距離。 */
        const val ID_GAP = 6

        /** 規則名稱與玩法名稱之間的分隔。 */
        const val VARIANT_SEPARATOR = " · "

        /** 狀態說明與欄位區上界的距離。 */
        const val STATUS_OFFSET = 12

        /** 規則名稱的文字顏色。 */
        const val RULE_NAME_COLOR = 0x8ED5DF

        /** 規則識別碼的文字顏色。 */
        const val SECONDARY_COLOR = 0xAAAAAA

        /** 唯讀說明的文字顏色。 */
        const val NOTE_COLOR = 0xCCCCCC

        /** 欄位名稱的文字顏色。 */
        const val LABEL_COLOR = 0xFFFFFF

        /** 設定值的文字顏色。 */
        const val VALUE_COLOR = 0xFFFFFF

        /** 載入中說明的文字顏色。 */
        const val STATUS_COLOR = 0xFFFFFF

        /** 失敗與無法顯示說明的文字顏色。 */
        const val ERROR_COLOR = 0xFF6666
    }
}
