package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryAiFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryIntegrityFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryOutcomeFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryFiltersDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ControlTooltip
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.CycleButtonInput
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.DropdownOption
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.DropdownOverlay
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.VerticalScrollbar
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 歷史條件編輯子頁；輸入驗證失敗時保留最近一次有效查詢。
 *
 * @property session 此畫面所屬的共用瀏覽 session。
 */
internal class HistoryFilterScreen(
    private val session: HistoryBrowseSession,
) : Screen(Text.translatable(MinecraftHistoryScreenKeys.FILTERS)) {
    /** 日期及 ID 文字欄位。 */
    private val fields = mutableMapOf<HistoryBrowseFilterField, TextFieldWidget>()

    /** 目前畫面的響應式版面。 */
    private val layout: HistoryFilterLayout get() = HistoryFilterLayout.create(height, visibleRowCount())

    /** 內容區目前的捲動距離。 */
    private var contentScroll = 0

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

    override fun init() {
        dropdown.close()
        seedFromQueryIfNeeded()
        fields.clear()
        val all = session.controller.state.value.query.scope.coversAllMatches
        val fieldsToShow = listOf(
            HistoryBrowseFilterField.PLAYER_NAME,
            HistoryBrowseFilterField.MATCH_ID,
            HistoryBrowseFilterField.FROM_DATE,
            HistoryBrowseFilterField.THROUGH_DATE,
            HistoryBrowseFilterField.MIN_RANK,
            HistoryBrowseFilterField.MAX_RANK,
        ).filterNot { all && (it == HistoryBrowseFilterField.MIN_RANK || it == HistoryBrowseFilterField.MAX_RANK) }
        fieldsToShow.forEachIndexed { index, field ->
            addField(field, fieldLabelKey(field), rowTop(index + 1))
        }
        ruleButton = addDrawableChild(ruleSelector())
        val choiceRow = fieldsToShow.size + 1
        outcomeButton = addChoiceButton(MinecraftHistoryScreenKeys.FILTER_OUTCOME, rowTop(choiceRow)) { cycleOutcome() }
        integrityButton = addChoiceButton(MinecraftHistoryScreenKeys.FILTER_INTEGRITY, rowTop(choiceRow + 1)) { cycleIntegrity() }
        aiButton = addChoiceButton(MinecraftHistoryScreenKeys.FILTER_AI, rowTop(choiceRow + 2)) { cycleAi() }
        addDrawableChild(RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.BACK)) { back() }.dimensions(width / 2 - 80, layout.footerTop, 70, 20).build())
        addDrawableChild(RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.RESET)) { reset() }.dimensions(width / 2 + 10, layout.footerTop, 70, 20).build())
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
     * 建立並註冊一個文字輸入欄位。
     *
     * @param field 欄位種類。
     * @param labelKey 欄位提示的翻譯鍵。
     * @param y 欄位垂直座標。
     */
    private fun addField(field: HistoryBrowseFilterField, labelKey: String, y: Int) {
        addDrawableChild(
            // 原版文字框邊框向外延伸一個像素，內縮內容範圍後使可見外框與按鈕一致。
            TextFieldWidget(textRenderer, controlLeft() + TEXT_FIELD_BORDER, y + TEXT_FIELD_BORDER, (controlWidth() - TEXT_FIELD_BORDER * 2).coerceAtLeast(1), controlHeight(), Text.translatable(labelKey)).also {
                it.setMaxLength(field.maxLength())
                it.text = fieldValue(field)
                it.tooltip = Tooltip.of(inputTooltip(field, labelKey))
                it.setChangedListener { value -> updateField(field, value) }
                fields[field] = it
            },
        )
    }

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
     * 建立與文字輸入欄位同一排版語言的循環選項按鈕；左鍵選下一項，右鍵或 Shift＋左鍵選上一項。
     *
     * @param labelKey 按鈕標題翻譯鍵。
     * @param y 按鈕垂直座標。
     * @param action 點擊後執行的變更動作。
     * @return 建立的按鈕。
     */
    private fun addChoiceButton(labelKey: String, y: Int, action: () -> Unit): ButtonWidget = RestartableMarqueeButtonWidget.builder(Text.translatable(labelKey)) { action() }
        .pressesOnSecondaryClick()
        .dimensions(controlLeft(), y, controlWidth(), layout.rowHeight)
        .build()
        .also(::addDrawableChild)

    /**
     * 取得控制項高度。
     *
     * @return 控制項高度。
     */
    private fun controlHeight(): Int = (layout.rowHeight - TEXT_FIELD_BORDER * 2).coerceAtLeast(1)

    /**
     * 取得右側控制項可用寬度。
     *
     * @return 控制項寬度。
     */
    private fun controlWidth(): Int = (width - controlLeft() - 14).coerceAtLeast(1)

    /**
     * 依可用寬度配置標題與控制項分界。
     *
     * @return 控制項左界。
     */
    private fun controlLeft(): Int = (width * 0.4).toInt().coerceIn(70, 160)

    /**
     * 取得目前可見的文字欄位數量。
     *
     * @return 文字欄位數量。
     */
    private fun visibleFieldCount(): Int = if (session.controller.state.value.query.scope.coversAllMatches) 4 else 6

    /**
     * 取得包含規則及選項列的內容列數。
     *
     * @return 內容列數。
     */
    private fun visibleRowCount(): Int = visibleFieldCount() + 4

    /**
     * 取得內容列位置。
     *
     * @param index 從零開始的內容列索引。
     * @return 依目前捲動狀態計算的控制項上界。
     */
    private fun rowTop(index: Int): Int = layout.rowTop(index, contentScroll)

    /**
     * 取得篩選欄位標題翻譯鍵。
     *
     * @param field 欄位種類。
     * @return 欄位標題翻譯鍵。
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
     * 建立文字欄位的用途與格式範例提示。
     *
     * @param field 欄位種類。
     * @param labelKey 欄位標題翻譯鍵。
     * @return 包含用途及範例的提示文字。
     */
    private fun inputTooltip(field: HistoryBrowseFilterField, labelKey: String): Text = Text.empty()
        .append(Text.translatable(requireNotNull(field.inputDescriptionKey) { "Filter field has no text input: $field" }, Text.translatable(labelKey)).formatted(Formatting.GRAY))
        .append("\n")
        .append(Text.translatable(MinecraftHistoryScreenKeys.FILTER_INPUT_EXAMPLE_LABEL).formatted(Formatting.GOLD))
        .append(" ")
        .append(Text.translatable(requireNotNull(field.inputExampleKey) { "Filter field has no text input: $field" }).formatted(Formatting.WHITE))

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
     * 建立規則選擇器，點擊展開「不限」與所有已登記規則的清單。
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
        }.dimensions(controlLeft(), rowTop(0), controlWidth(), layout.rowHeight).build()
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
     * 將結果篩選值轉成顯示文字。
     *
     * @param value 篩選值；null 表示不限。
     * @return 本地化選項文字。
     */
    private fun outcomeLabel(value: HistoryOutcomeFilterDto?): Text = value?.let { Text.translatable(it.translationKey) }
        ?: Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)

    /**
     * 將完整性篩選值轉成顯示文字。
     *
     * @param value 篩選值；null 表示不限。
     * @return 本地化選項文字。
     */
    private fun integrityLabel(value: HistoryIntegrityFilterDto?): Text = value?.let { Text.translatable(it.translationKey) }
        ?: Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)

    /**
     * 將 AI 篩選值轉成顯示文字。
     *
     * @param value 篩選值；null 表示不限。
     * @return 本地化選項文字。
     */
    private fun aiLabel(value: HistoryAiFilterDto?): Text = value?.let { Text.translatable(it.translationKey) }
        ?: Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)

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

    /** 清單展開時在清單內捲動，否則捲動篩選欄位。 */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (dropdown.mouseScrolled(textRenderer, height, amount)) return true
        if (mouseY in layout.contentTop.toDouble()..layout.contentBottom.toDouble()) {
            contentScroll = (contentScroll - amount.toInt() * layout.rowSpacing).coerceIn(0, layout.scrollableHeight)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (dropdown.mouseClicked(textRenderer, height, mouseX, mouseY, button)) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        val labels = listOf(
            HistoryBrowseFilterField.PLAYER_NAME to MinecraftHistoryScreenKeys.FILTER_PLAYER_NAME,
            HistoryBrowseFilterField.MATCH_ID to MinecraftHistoryScreenKeys.FILTER_MATCH_ID,
            HistoryBrowseFilterField.FROM_DATE to MinecraftHistoryScreenKeys.FILTER_FROM,
            HistoryBrowseFilterField.THROUGH_DATE to MinecraftHistoryScreenKeys.FILTER_THROUGH,
            HistoryBrowseFilterField.MIN_RANK to MinecraftHistoryScreenKeys.FILTER_MIN_RANK,
            HistoryBrowseFilterField.MAX_RANK to MinecraftHistoryScreenKeys.FILTER_MAX_RANK,
        ).filterNot {
            session.controller.state.value.query.scope.coversAllMatches &&
                (it.first == HistoryBrowseFilterField.MIN_RANK || it.first == HistoryBrowseFilterField.MAX_RANK)
        }
        if (layout.isVisible(0, contentScroll)) drawLabel(context, MinecraftHistoryScreenKeys.FILTER_RULE, rowTop(0), 0xffffff)
        labels.forEachIndexed { index, (field, key) ->
            val y = rowTop(index + 1)
            val visible = layout.isVisible(index + 1, contentScroll)
            fields[field]?.y = y + TEXT_FIELD_BORDER
            fields[field]?.visible = visible
            if (!visible) return@forEachIndexed
            val error = session.filterDraft.errors[field]?.let {
                Text.translatable(it.translationKey).formatted(Formatting.RED)
            }
            drawLabel(context, key, y, if (error == null) 0xffffff else 0xff5555)
            fields[field]?.tooltip = Tooltip.of(
                Text.empty().append(inputTooltip(field, key)).also { text -> error?.let { text.append("\n").append(it) } },
            )
        }
        ruleButton?.y = rowTop(0)
        ruleButton?.visible = layout.isVisible(0, contentScroll)
        outcomeButton?.y = rowTop(visibleFieldCount() + 1)
        integrityButton?.y = rowTop(visibleFieldCount() + 2)
        aiButton?.y = rowTop(visibleFieldCount() + 3)
        outcomeButton?.visible = layout.isVisible(visibleFieldCount() + 1, contentScroll)
        integrityButton?.visible = layout.isVisible(visibleFieldCount() + 2, contentScroll)
        aiButton?.visible = layout.isVisible(visibleFieldCount() + 3, contentScroll)
        listOf(MinecraftHistoryScreenKeys.FILTER_OUTCOME, MinecraftHistoryScreenKeys.FILTER_INTEGRITY, MinecraftHistoryScreenKeys.FILTER_AI).forEachIndexed { index, key ->
            val row = visibleFieldCount() + 1 + index
            if (layout.isVisible(row, contentScroll)) drawLabel(context, key, rowTop(row), 0xffffff)
        }
        ScreenRendering.title(context, textRenderer, title, width)
        outcomeButton?.message = outcomeLabel(session.filterDraft.input.outcome)
        integrityButton?.message = integrityLabel(session.filterDraft.input.integrity)
        aiButton?.message = aiLabel(session.filterDraft.input.ai)
        val outcomeOptions = listOf(Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)) + HistoryOutcomeFilterDto.entries.map(::outcomeLabel)
        val integrityOptions = listOf(Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)) + HistoryIntegrityFilterDto.entries.map(::integrityLabel)
        val aiOptions = listOf(Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)) + HistoryAiFilterDto.entries.map(::aiLabel)
        outcomeButton?.tooltip = Tooltip.of(
            ControlTooltip.options(
                description = Text.translatable(MinecraftHistoryScreenKeys.FILTER_OUTCOME),
                labels = outcomeOptions,
                currentIndex = session.filterDraft.input.outcome?.ordinal?.plus(1) ?: 0,
                hints = CycleButtonInput.hintTexts(optionCount = outcomeOptions.size),
            ),
        )
        integrityButton?.tooltip = Tooltip.of(
            ControlTooltip.options(
                description = Text.translatable(MinecraftHistoryScreenKeys.FILTER_INTEGRITY),
                labels = integrityOptions,
                currentIndex = session.filterDraft.input.integrity?.ordinal?.plus(1) ?: 0,
                hints = CycleButtonInput.hintTexts(optionCount = integrityOptions.size),
            ),
        )
        aiButton?.tooltip = Tooltip.of(
            ControlTooltip.options(
                description = Text.translatable(MinecraftHistoryScreenKeys.FILTER_AI),
                labels = aiOptions,
                currentIndex = session.filterDraft.input.ai?.ordinal?.plus(1) ?: 0,
                hints = CycleButtonInput.hintTexts(optionCount = aiOptions.size),
            ),
        )
        ruleButton?.tooltip = Tooltip.of(ruleTooltip())
        ruleButton?.message = ruleDisplayLabel()
        val viewportHeight = layout.contentBottom - layout.contentTop
        VerticalScrollbar.render(
            context = context,
            left = width - CONTENT_SCROLLBAR_RIGHT_INSET,
            layout = ScrollbarLayout(
                trackTop = layout.contentTop,
                trackBottom = layout.contentBottom,
                itemCount = viewportHeight + layout.scrollableHeight,
                visibleItemCount = viewportHeight,
                scrollIndex = contentScroll,
                minimumThumbHeight = VerticalScrollbar.MINIMUM_THUMB_HEIGHT,
            ),
        )
        super.render(context, dropdown.pointer(mouseX), dropdown.pointer(mouseY), delta)
        dropdown.render(context, textRenderer, height, mouseX, mouseY)
    }

    /**
     * 在左欄繪製經字寬限制的標題，不侵入右側控制項。
     *
     * @param context 畫面繪製上下文。
     * @param key 標題翻譯鍵。
     * @param y 控制項列起點。
     * @param color 標題 RGB 顏色。
     */
    private fun drawLabel(context: DrawContext, key: String, y: Int, color: Int) {
        val value = HistoryScreenText.trim(Text.translatable(key).string, controlLeft() - 16, textRenderer::getWidth)
        context.drawTextWithShadow(textRenderer, Text.literal(value), 8, y + (layout.rowHeight - textRenderer.fontHeight) / 2, color)
    }

    /** 將目前規則篩選值轉成選擇器上的文字。 */
    private fun ruleDisplayLabel(): Text = ruleLabel(session.filterDraft.input.ruleId)

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (dropdown.keyPressed(textRenderer, height, keyCode)) return true
        if (keyCode == 256) {
            session.backToList()
            return true
        }
        if (keyCode == 257) validateAndApply()
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    /** 規則清單展開時不將文字輸入傳遞至背景欄位。 */
    override fun charTyped(chr: Char, modifiers: Int): Boolean = dropdown.isOpen || super.charTyped(chr, modifiers)

    /** 規則清單展開時不將拖曳傳遞至背景控制項。 */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = dropdown.isOpen || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)

    /** 規則清單展開時不將放開滑鼠事件傳遞至背景控制項。 */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = dropdown.isOpen || super.mouseReleased(mouseX, mouseY, button)

    override fun shouldPause(): Boolean = false

    override fun close() = session.backToList()

    override fun removed() {
        session.removed(this)
        super.removed()
    }

    /** 輸入框與捲軸的固定繪製尺寸。 */
    private companion object {
        /** 規則選擇器展開清單的識別值。 */
        const val RULE_SELECTOR_KEY = "rule"

        /** 內容捲軸左界與視窗右界的距離。 */
        const val CONTENT_SCROLLBAR_RIGHT_INSET = 5

        /** 原版文字輸入框向內容範圍外延伸的邊框厚度。 */
        const val TEXT_FIELD_BORDER = 1
    }
}
