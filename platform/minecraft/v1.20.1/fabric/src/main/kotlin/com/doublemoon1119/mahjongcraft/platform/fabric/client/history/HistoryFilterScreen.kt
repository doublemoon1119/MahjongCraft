package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryAiFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryIntegrityFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryOutcomeFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryFiltersDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ControlTooltip
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.CycleButtonInput
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.DropdownOption
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.DropdownOverlay
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.VerticalScrollbar
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.lwjgl.glfw.GLFW
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 牌譜篩選子頁：一列一個條件，標籤在左、控制項在右，日期與名次的起訖放在同一列；輸入驗證失敗時保留最近一次
 * 有效查詢。
 *
 * @property session 此畫面所屬的共用瀏覽 session。
 */
internal class HistoryFilterScreen(
    private val session: HistoryBrowseSession,
) : Screen(Text.translatable(MinecraftHistoryScreenKeys.FILTERS)) {
    /** 文字輸入欄位。 */
    private val fields = mutableMapOf<HistoryBrowseFilterField, TextFieldWidget>()

    /** 每一列的控制項，依列的順序排列。 */
    private val rowWidgets = mutableMapOf<FilterRow, List<ClickableWidget>>()

    /** 第一個可見列的索引。 */
    private var scrollIndex = 0

    /** 是否正在拖曳捲軸。 */
    private var scrollDragging = false

    /** 拖曳時游標在捲軸滑塊內的抓取偏移。 */
    private var scrollGrabOffset = 0.0

    /** 規則選擇器的展開清單。 */
    private val dropdown = DropdownOverlay()

    /** 結果篩選按鈕。 */
    private var outcomeButton: ButtonWidget? = null

    /** 完整性篩選按鈕。 */
    private var integrityButton: ButtonWidget? = null

    /** AI 篩選按鈕。 */
    private var aiButton: ButtonWidget? = null

    /** 規則篩選按鈕。 */
    private var ruleButton: ButtonWidget? = null

    /** 重設按鈕。 */
    private var resetButton: ButtonWidget? = null

    /** 目前畫面的版面。 */
    private val layout: FormScreenLayout
        get() = FormScreenLayout(windowWidth = width, windowHeight = height, hasPageSwitch = false, hasRuleRow = false, hasTabs = false)

    override fun init() {
        dropdown.close()
        seedFromQueryIfNeeded()
        fields.clear()
        rowWidgets.clear()
        scrollDragging = false
        rows().forEach { row ->
            rowWidgets[row] = when (row) {
                FilterRow.RULE -> listOf(addDrawableChild(ruleSelector()).also { ruleButton = it })
                FilterRow.PLAYER_NAME -> listOf(addField(HistoryBrowseFilterField.PLAYER_NAME, MinecraftHistoryScreenKeys.FILTER_PLACEHOLDER_PLAYER_NAME))
                FilterRow.MATCH_ID -> listOf(addField(HistoryBrowseFilterField.MATCH_ID, MinecraftHistoryScreenKeys.FILTER_PLACEHOLDER_MATCH_ID))
                FilterRow.DATE -> listOf(
                    addField(HistoryBrowseFilterField.FROM_DATE, MinecraftHistoryScreenKeys.FILTER_PLACEHOLDER_DATE),
                    addField(HistoryBrowseFilterField.THROUGH_DATE, MinecraftHistoryScreenKeys.FILTER_PLACEHOLDER_DATE),
                )
                FilterRow.OWN_RANK -> listOf(
                    addField(HistoryBrowseFilterField.MIN_RANK, MinecraftHistoryScreenKeys.FILTER_ANY),
                    addField(HistoryBrowseFilterField.MAX_RANK, MinecraftHistoryScreenKeys.FILTER_ANY),
                )
                FilterRow.OUTCOME -> listOf(addChoiceButton { cycleOutcome() }.also { outcomeButton = it })
                FilterRow.INTEGRITY -> listOf(addChoiceButton { cycleIntegrity() }.also { integrityButton = it })
                FilterRow.AI -> listOf(addChoiceButton { cycleAi() }.also { aiButton = it })
            }
        }
        scrollIndex = scrollIndex.coerceIn(0, maximumScroll())
        val footer = layout.footer(buttonCount = 2)
        resetButton = addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.RESET)) { reset() }
                .dimensions(footer.buttonX(0), layout.footerY, footer.buttonWidth(0), FormScreenLayout.BUTTON_HEIGHT)
                .build(),
        )
        addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.BACK)) { back() }
                .dimensions(footer.buttonX(1), layout.footerY, footer.buttonWidth(1), FormScreenLayout.BUTTON_HEIGHT)
                .build(),
        )
        placeRows()
    }

    /** 目前範圍顯示的列；全部紀錄沒有自己的名次，不顯示名次列。 */
    private fun rows(): List<FilterRow> = FilterRow.entries.filterNot {
        it == FilterRow.OWN_RANK && session.controller.state.value.query.scope.coversAllMatches
    }

    /** 最多可捲動的列數。 */
    private fun maximumScroll(): Int = (rows().size - layout.visibleFieldCount).coerceAtLeast(0)

    /** 依捲動位置擺放每一列的控制項，捲出可見範圍的列隱藏。 */
    private fun placeRows() {
        val layout = layout
        rows().forEachIndexed { index, row ->
            val visibleIndex = index - scrollIndex
            val visible = visibleIndex in 0 until layout.visibleFieldCount
            val controlY = layout.fieldControlY(layout.fieldRowTop(visibleIndex.coerceAtLeast(0)))
            val widgets = rowWidgets[row].orEmpty()
            val rangeWidth = ((layout.controlWidth - RANGE_GAP) / 2).coerceAtLeast(1)
            widgets.forEachIndexed { widgetIndex, widget ->
                val left = layout.controlLeft + if (widgets.size == 2 && widgetIndex == 1) layout.controlWidth - rangeWidth else 0
                val controlWidth = if (widgets.size == 2) rangeWidth else layout.controlWidth
                widget.visible = visible
                if (widget is TextFieldWidget) {
                    // 原版文字框的邊框畫在內容範圍外一個像素，內縮後可見外框與按鈕對齊。
                    widget.x = left + TEXT_FIELD_BORDER
                    widget.y = controlY + TEXT_FIELD_BORDER
                    widget.width = (controlWidth - TEXT_FIELD_BORDER * 2).coerceAtLeast(1)
                } else {
                    widget.x = left
                    widget.y = controlY
                    widget.width = controlWidth
                }
            }
        }
    }

    /** 以目前已套用的查詢條件初始化草稿輸入。 */
    private fun seedFromQueryIfNeeded() {
        val draft = session.filterDraft
        if (draft.input != HistoryBrowseFilterInput()) return
        val filters = session.controller.state.value.query.filters
        draft.input = HistoryBrowseFilterInput(
            ruleId = filters.ruleId.orEmpty(),
            playerName = filters.playerName.orEmpty(),
            matchId = filters.matchId.orEmpty(),
            outcome = filters.outcome,
            integrity = filters.integrity,
            ai = filters.ai,
            fromDate = filters.endedAtFromEpochMillis?.let { DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }.orEmpty(),
            throughDate = filters.endedAtBeforeEpochMillis?.let { DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(it - 1).atZone(ZoneId.systemDefault())) }.orEmpty(),
            ownRankMin = filters.ownRankMin?.toString().orEmpty(),
            ownRankMax = filters.ownRankMax?.toString().orEmpty(),
        )
    }

    /**
     * 建立並註冊一個文字輸入欄位；位置由 [placeRows] 決定。
     *
     * @param field 欄位種類。
     * @param placeholderKey 欄位沒有內容時灰色提示的翻譯鍵。
     * @return 已加入畫面的輸入欄位。
     */
    private fun addField(field: HistoryBrowseFilterField, placeholderKey: String): TextFieldWidget = addDrawableChild(
        TextFieldWidget(textRenderer, 0, 0, 1, FormScreenLayout.BUTTON_HEIGHT - TEXT_FIELD_BORDER * 2, Text.translatable(fieldLabelKey(field))).also {
            it.setMaxLength(field.maxLength())
            it.text = fieldValue(field)
            it.setPlaceholder(Text.translatable(placeholderKey).formatted(Formatting.DARK_GRAY))
            it.setChangedListener { value -> updateField(field, value) }
            fields[field] = it
        },
    )

    /**
     * 取得欄位輸入長度上限。
     *
     * @return 文字長度上限。
     */
    private fun HistoryBrowseFilterField.maxLength(): Int = when (this) {
        HistoryBrowseFilterField.PLAYER_NAME -> 16
        HistoryBrowseFilterField.MATCH_ID -> 36
        HistoryBrowseFilterField.RULE -> 128
        HistoryBrowseFilterField.FROM_DATE, HistoryBrowseFilterField.THROUGH_DATE -> 10
        HistoryBrowseFilterField.MIN_RANK, HistoryBrowseFilterField.MAX_RANK -> 9
    }

    /**
     * 建立循環選項按鈕；左鍵選下一項，右鍵或 Shift＋左鍵選上一項。位置由 [placeRows] 決定。
     *
     * @param action 點擊後執行的變更動作。
     * @return 已加入畫面的按鈕。
     */
    private fun addChoiceButton(action: () -> Unit): ButtonWidget = addDrawableChild(
        RestartableMarqueeButtonWidget.builder(Text.empty()) { action() }
            .pressesOnSecondaryClick()
            .build(),
    )

    /**
     * 取得篩選欄位名稱的翻譯鍵。
     *
     * @param field 欄位種類。
     * @return 欄位名稱翻譯鍵。
     */
    private fun fieldLabelKey(field: HistoryBrowseFilterField): String = when (field) {
        HistoryBrowseFilterField.PLAYER_NAME -> MinecraftHistoryScreenKeys.FILTER_PLAYER_NAME
        HistoryBrowseFilterField.MATCH_ID -> MinecraftHistoryScreenKeys.FILTER_MATCH_ID
        HistoryBrowseFilterField.FROM_DATE -> MinecraftHistoryScreenKeys.FILTER_FROM
        HistoryBrowseFilterField.THROUGH_DATE -> MinecraftHistoryScreenKeys.FILTER_THROUGH
        HistoryBrowseFilterField.MIN_RANK -> MinecraftHistoryScreenKeys.FILTER_MIN_RANK
        HistoryBrowseFilterField.MAX_RANK -> MinecraftHistoryScreenKeys.FILTER_MAX_RANK
        HistoryBrowseFilterField.RULE -> MinecraftHistoryScreenKeys.FILTER_RULE
    }

    /**
     * 建立文字欄位的提示：用途、格式範例，輸入有誤時再加上錯誤原因。
     *
     * @param field 欄位種類。
     * @return 提示文字。
     */
    private fun inputTooltip(field: HistoryBrowseFilterField): Text {
        val labelKey = fieldLabelKey(field)
        val text = Text.empty()
            .append(Text.translatable(requireNotNull(field.inputDescriptionKey) { "Filter field has no text input: $field" }, Text.translatable(labelKey)).formatted(Formatting.GRAY))
            .append("\n")
            .append(Text.translatable(MinecraftHistoryScreenKeys.FILTER_INPUT_EXAMPLE_LABEL).formatted(Formatting.GOLD))
            .append(" ")
            .append(Text.translatable(requireNotNull(field.inputExampleKey) { "Filter field has no text input: $field" }).formatted(Formatting.WHITE))
        fieldError(field)?.let { text.append("\n\n").append(it) }
        return text
    }

    /**
     * 取得欄位目前的錯誤原因。
     *
     * @param field 欄位種類。
     * @return 紅色的錯誤原因；沒有錯誤時為 null。
     */
    private fun fieldError(field: HistoryBrowseFilterField): Text? = session.filterDraft.errors[field]?.let {
        Text.translatable(it.translationKey).formatted(Formatting.RED)
    }

    /**
     * 更新欄位草稿並嘗試套用有效查詢。
     *
     * @param field 被修改的欄位。
     * @param value 欄位新文字。
     */
    private fun updateField(field: HistoryBrowseFilterField, value: String) {
        val input = session.filterDraft.input
        session.filterDraft.input = when (field) {
            HistoryBrowseFilterField.RULE -> input.copy(ruleId = value)
            HistoryBrowseFilterField.PLAYER_NAME -> input.copy(playerName = value)
            HistoryBrowseFilterField.MATCH_ID -> input.copy(matchId = value)
            HistoryBrowseFilterField.FROM_DATE -> input.copy(fromDate = value)
            HistoryBrowseFilterField.THROUGH_DATE -> input.copy(throughDate = value)
            HistoryBrowseFilterField.MIN_RANK -> input.copy(ownRankMin = value)
            HistoryBrowseFilterField.MAX_RANK -> input.copy(ownRankMax = value)
        }
        validateAndApply()
    }

    /**
     * 驗證整份草稿；只有有效結果才送出查詢。
     *
     * @return 是否已套用有效查詢。
     */
    private fun validateAndApply(): Boolean {
        val result = HistoryBrowseFilterValidation.parse(session.filterDraft.input, ZoneId.systemDefault(), session.controller.state.value.query.scope)
        when (result) {
            is HistoryBrowseFilterResult.Valid -> {
                session.filterDraft.errors = emptyMap()
                val query = session.controller.state.value.query.copy(filters = result.filters)
                session.controller.updateQuery(query)
                return true
            }
            is HistoryBrowseFilterResult.Invalid -> session.filterDraft.errors = result.errors
        }
        return false
    }

    /** 返回列表並套用有效輸入。 */
    private fun back() {
        validateAndApply()
        session.backToList()
    }

    /** 清除篩選文字及錯誤，不改變排序及查閱範圍。 */
    private fun reset() {
        session.filterDraft.input = HistoryBrowseFilterInput()
        session.filterDraft.errors = emptyMap()
        fields.values.forEach { it.text = "" }
        val current = session.controller.state.value.query
        session.controller.updateQuery(current.copy(filters = HistoryQueryFiltersDto.NONE))
    }

    /** 循環改變結果篩選。 */
    private fun cycleOutcome() {
        session.filterDraft.input = session.filterDraft.input.copy(outcome = next(session.filterDraft.input.outcome, HistoryOutcomeFilterDto.entries))
        validateAndApply()
    }

    /** 循環改變完整性篩選。 */
    private fun cycleIntegrity() {
        session.filterDraft.input = session.filterDraft.input.copy(integrity = next(session.filterDraft.input.integrity, HistoryIntegrityFilterDto.entries))
        validateAndApply()
    }

    /** 循環改變 AI 篩選。 */
    private fun cycleAi() {
        session.filterDraft.input = session.filterDraft.input.copy(ai = next(session.filterDraft.input.ai, HistoryAiFilterDto.entries))
        validateAndApply()
    }

    /**
     * 建立規則選擇器，點擊展開「不限」與所有已登記規則的清單；位置由 [placeRows] 決定。
     *
     * @return 尚未加入畫面的選擇器按鈕。
     */
    private fun ruleSelector(): RestartableMarqueeButtonWidget {
        lateinit var selector: RestartableMarqueeButtonWidget
        selector = RestartableMarqueeButtonWidget.builder(ruleDisplayLabel()) {
            val options = ruleOptions()
            dropdown.toggle(
                key = RULE_SELECTOR_KEY,
                anchor = selector,
                options = options.map { DropdownOption(label = ruleLabel(it)) },
                selectedIndex = options.indexOf(session.filterDraft.input.ruleId).takeIf { it >= 0 },
            ) { index ->
                session.filterDraft.input = session.filterDraft.input.copy(ruleId = options[index])
                validateAndApply()
            }
        }.build()
        selector.selectorExpanded = { dropdown.isOpenFor(RULE_SELECTOR_KEY) }
        return selector
    }

    /** 建立包含不限值在內的排序規則選項；空字串代表不限。 */
    private fun ruleOptions(): List<String> = listOf("") + session.ruleNames.registrationKeys.sorted()

    /**
     * 將規則篩選值轉成顯示文字。
     *
     * @param ruleId 規則識別碼；空字串代表不限。
     * @return 「不限」、規則名稱加識別碼，或沒有名稱時的識別碼。
     */
    private fun ruleLabel(ruleId: String): Text = ruleId.takeUnless(String::isEmpty)?.let { id ->
        session.ruleNames.find(id)?.let(Text::translatable)?.let { Text.empty().append(it).append(" (").append(id).append(")") }
            ?: Text.literal(id)
    } ?: Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)

    /**
     * 建立規則選擇器的 tooltip。
     *
     * @return 顯示目前規則與可選規則的 tooltip。
     */
    private fun ruleTooltip(): Text {
        val options = ruleOptions()
        return ControlTooltip.options(
            description = Text.translatable(MinecraftHistoryScreenKeys.FILTER_RULE),
            labels = options.map(::ruleLabel),
            currentIndex = options.indexOf(session.filterDraft.input.ruleId),
        )
    }

    /**
     * 將篩選值轉成顯示文字。
     *
     * @param translationKey 篩選值的翻譯鍵；null 表示不限。
     * @return 本地化選項文字。
     */
    private fun choiceLabel(translationKey: String?): Text = Text.translatable(translationKey ?: MinecraftHistoryScreenKeys.FILTER_ANY)

    /**
     * 建立循環選項按鈕的 tooltip，「不限」排在第一項。
     *
     * @param descriptionKey 欄位說明的翻譯鍵。
     * @param optionKeys 不含「不限」的選項翻譯鍵。
     * @param currentIndex 目前選項在不含「不限」清單中的索引；不限時為 null。
     * @return 提示文字。
     */
    private fun choiceTooltip(descriptionKey: String, optionKeys: List<String>, currentIndex: Int?): Tooltip {
        val labels = listOf(choiceLabel(null)) + optionKeys.map(::choiceLabel)
        return Tooltip.of(
            ControlTooltip.options(
                description = Text.translatable(descriptionKey),
                labels = labels,
                currentIndex = currentIndex?.plus(1) ?: 0,
                hints = CycleButtonInput.hintTexts(optionCount = labels.size),
            ),
        )
    }

    /**
     * 取得循環選項中的下一個值；「不限」排在第一項，按住 Shift 時往回選。
     *
     * @param T 選項型別。
     * @param value 目前選值；null 表示不限。
     * @param values 不含「不限」的可循環值清單。
     * @return 下一個值；null 表示不限。
     */
    private fun <T> next(value: T?, values: List<T>): T? = CycleButtonInput.next(options = listOf<T?>(null) + values, current = value)

    /**
     * 取得指定欄位目前保留的原始文字。
     *
     * @param field 欄位種類。
     * @return 欄位文字。
     */
    private fun fieldValue(field: HistoryBrowseFilterField): String = when (field) {
        HistoryBrowseFilterField.RULE -> session.filterDraft.input.ruleId
        HistoryBrowseFilterField.PLAYER_NAME -> session.filterDraft.input.playerName
        HistoryBrowseFilterField.MATCH_ID -> session.filterDraft.input.matchId
        HistoryBrowseFilterField.FROM_DATE -> session.filterDraft.input.fromDate
        HistoryBrowseFilterField.THROUGH_DATE -> session.filterDraft.input.throughDate
        HistoryBrowseFilterField.MIN_RANK -> session.filterDraft.input.ownRankMin
        HistoryBrowseFilterField.MAX_RANK -> session.filterDraft.input.ownRankMax
    }

    /**
     * 取得一列的名稱翻譯鍵。
     *
     * @param row 篩選列。
     * @return 名稱翻譯鍵。
     */
    private fun rowLabelKey(row: FilterRow): String = when (row) {
        FilterRow.RULE -> MinecraftHistoryScreenKeys.FILTER_RULE
        FilterRow.PLAYER_NAME -> MinecraftHistoryScreenKeys.FILTER_PLAYER_NAME
        FilterRow.MATCH_ID -> MinecraftHistoryScreenKeys.FILTER_MATCH_ID
        FilterRow.DATE -> MinecraftHistoryScreenKeys.FILTER_DATE
        FilterRow.OWN_RANK -> MinecraftHistoryScreenKeys.FILTER_OWN_RANK
        FilterRow.OUTCOME -> MinecraftHistoryScreenKeys.FILTER_OUTCOME
        FilterRow.INTEGRITY -> MinecraftHistoryScreenKeys.FILTER_INTEGRITY
        FilterRow.AI -> MinecraftHistoryScreenKeys.FILTER_AI
    }

    /**
     * 判斷一列是否有輸入錯誤。
     *
     * @param row 篩選列。
     * @return 這一列的任一個輸入欄位有錯誤時為 true。
     */
    private fun rowHasError(row: FilterRow): Boolean = rowWidgets[row].orEmpty().any { widget ->
        fields.entries.any { (field, input) -> input === widget && session.filterDraft.errors.containsKey(field) }
    }

    /** 清單展開時在清單內捲動，否則逐列捲動篩選欄位。 */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (dropdown.mouseScrolled(textRenderer, height, amount)) return true
        val layout = layout
        if (mouseY >= layout.fieldsTop && mouseY < layout.fieldsBottom && amount != 0.0) {
            scrollTo(scrollIndex + if (amount < 0) 1 else -1)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    /**
     * 捲動到指定的第一個可見列。
     *
     * @param index 第一個可見列的索引。
     */
    private fun scrollTo(index: Int) {
        val next = index.coerceIn(0, maximumScroll())
        if (next == scrollIndex) return
        scrollIndex = next
        dropdown.close()
        placeRows()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (dropdown.mouseClicked(textRenderer, height, mouseX, mouseY, button)) return true
        val layout = layout
        val scrollbar = layout.scrollbar(fieldCount = rows().size, scrollIndex = scrollIndex)
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && scrollbar.maximumScroll > 0 && layout.isOverScrollbar(mouseX, mouseY)) {
            scrollDragging = true
            scrollGrabOffset = scrollbar.grabOffset(mouseY)
            scrollTo(scrollbar.scrollIndexFor(mouseY, scrollGrabOffset))
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /** 規則清單展開時不將拖曳傳遞至背景控制項；拖曳捲軸時逐列捲動。 */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (dropdown.isOpen) return true
        if (scrollDragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            scrollTo(layout.scrollbar(fieldCount = rows().size, scrollIndex = scrollIndex).scrollIndexFor(mouseY, scrollGrabOffset))
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    /** 規則清單展開時不將放開滑鼠事件傳遞至背景控制項；放開左鍵時結束拖曳。 */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (dropdown.isOpen) return true
        if (scrollDragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            scrollDragging = false
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        ScreenRendering.title(context, textRenderer, title, width)
        val layout = layout
        val input = session.filterDraft.input
        rows().forEachIndexed { index, row ->
            val visibleIndex = index - scrollIndex
            if (visibleIndex !in 0 until layout.visibleFieldCount) return@forEachIndexed
            val rowTop = layout.fieldRowTop(visibleIndex)
            layout.fieldStripe(visibleIndex).renderIfStriped(context, index)
            val label = HistoryScreenText.trim(Text.translatable(rowLabelKey(row)).string, layout.labelWidth, textRenderer::getWidth)
            context.drawTextWithShadow(textRenderer, Text.literal(label), layout.contentLeft, layout.fieldLabelY(rowTop), if (rowHasError(row)) ERROR_COLOR else LABEL_COLOR)
            val widgets = rowWidgets[row].orEmpty()
            if (widgets.size == 2) {
                val dashX = layout.controlLeft + layout.controlWidth / 2 - textRenderer.getWidth(RANGE_DASH) / 2
                val controlY = layout.fieldControlY(rowTop)
                context.drawTextWithShadow(textRenderer, RANGE_DASH, dashX, controlY + (FormScreenLayout.BUTTON_HEIGHT - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2, LABEL_COLOR)
            }
        }
        fields.forEach { (field, widget) -> widget.tooltip = Tooltip.of(inputTooltip(field)) }
        outcomeButton?.message = choiceLabel(input.outcome?.translationKey)
        integrityButton?.message = choiceLabel(input.integrity?.translationKey)
        aiButton?.message = choiceLabel(input.ai?.translationKey)
        outcomeButton?.tooltip = choiceTooltip(MinecraftHistoryScreenKeys.FILTER_OUTCOME, HistoryOutcomeFilterDto.entries.map { it.translationKey }, input.outcome?.ordinal)
        integrityButton?.tooltip = choiceTooltip(MinecraftHistoryScreenKeys.FILTER_INTEGRITY, HistoryIntegrityFilterDto.entries.map { it.translationKey }, input.integrity?.ordinal)
        aiButton?.tooltip = choiceTooltip(MinecraftHistoryScreenKeys.FILTER_AI, HistoryAiFilterDto.entries.map { it.translationKey }, input.ai?.ordinal)
        ruleButton?.tooltip = Tooltip.of(ruleTooltip())
        ruleButton?.message = ruleDisplayLabel()
        resetButton?.active = input != HistoryBrowseFilterInput()
        VerticalScrollbar.render(context, layout.scrollbarLeft, layout.scrollbar(fieldCount = rows().size, scrollIndex = scrollIndex))
        super.render(context, dropdown.pointer(mouseX), dropdown.pointer(mouseY), delta)
        dropdown.render(context, textRenderer, height, mouseX, mouseY)
    }

    /** 將目前規則篩選值轉成選擇器上的文字。 */
    private fun ruleDisplayLabel(): Text = ruleLabel(session.filterDraft.input.ruleId)

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (dropdown.keyPressed(textRenderer, height, keyCode)) return true
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            session.backToList()
            return true
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) validateAndApply()
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    /** 規則清單展開時不將文字輸入傳遞至背景欄位。 */
    override fun charTyped(chr: Char, modifiers: Int): Boolean = dropdown.isOpen || super.charTyped(chr, modifiers)

    override fun shouldPause(): Boolean = false

    override fun close() = session.backToList()

    override fun removed() {
        session.removed(this)
        super.removed()
    }

    /** 篩選頁的列，依畫面由上到下的順序排列。 */
    private enum class FilterRow {
        /** 規則選擇器。 */
        RULE,

        /** 玩家名稱輸入框。 */
        PLAYER_NAME,

        /** 對局 UUID 輸入框。 */
        MATCH_ID,

        /** 結束日期起訖兩個輸入框。 */
        DATE,

        /** 自己的名次上下限兩個輸入框；只在自己的紀錄出現。 */
        OWN_RANK,

        /** 結果循環按鈕。 */
        OUTCOME,

        /** 完整性循環按鈕。 */
        INTEGRITY,

        /** AI 對局循環按鈕。 */
        AI,
    }

    /** 輸入框與標籤的固定繪製尺寸與顏色。 */
    private companion object {
        /** 規則選擇器展開清單的識別值。 */
        const val RULE_SELECTOR_KEY = "rule"

        /** 原版文字輸入框向內容範圍外延伸的邊框厚度。 */
        const val TEXT_FIELD_BORDER = 1

        /** 起訖兩個輸入框之間的距離，中間畫連接號。 */
        const val RANGE_GAP = 12

        /** 起訖兩個輸入框之間的連接號。 */
        const val RANGE_DASH = "–"

        /** 標籤的文字顏色。 */
        const val LABEL_COLOR = 0xFFFFFF

        /** 有輸入錯誤的標籤文字顏色。 */
        const val ERROR_COLOR = 0xFF5555
    }
}
