package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryAiFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryIntegrityFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryOutcomeFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryFiltersDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.CycleButtonInput
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

    /** 規則選項清單是否展開。 */
    private var rulePickerOpen = false

    /** 規則選項清單的垂直捲動索引。 */
    private var rulePickerScroll = 0

    /** 結果篩選按鈕。 */
    private var outcomeButton: ButtonWidget? = null

    /** 完整性篩選按鈕。 */
    private var integrityButton: ButtonWidget? = null

    /** AI 篩選按鈕。 */
    private var aiButton: ButtonWidget? = null

    /** 規則篩選按鈕。 */
    private var ruleButton: ButtonWidget? = null

    override fun init() {
        seedFromQueryIfNeeded()
        fields.clear()
        val all = session.controller.state.value.query.scope == HistoryQueryScopeDto.ALL
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
        ruleButton = ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.FILTER_RULE_SELECT)) { rulePickerOpen = !rulePickerOpen }
            .dimensions(controlLeft(), rowTop(0), controlWidth(), layout.rowHeight).build().also(::addDrawableChild)
        val choiceRow = fieldsToShow.size + 1
        outcomeButton = addChoiceButton(MinecraftHistoryScreenKeys.FILTER_OUTCOME, rowTop(choiceRow)) { cycleOutcome() }
        integrityButton = addChoiceButton(MinecraftHistoryScreenKeys.FILTER_INTEGRITY, rowTop(choiceRow + 1)) { cycleIntegrity() }
        aiButton = addChoiceButton(MinecraftHistoryScreenKeys.FILTER_AI, rowTop(choiceRow + 2)) { cycleAi() }
        addDrawableChild(ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.BACK)) { back() }.dimensions(width / 2 - 80, layout.footerTop, 70, 20).build())
        addDrawableChild(ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.RESET)) { reset() }.dimensions(width / 2 + 10, layout.footerTop, 70, 20).build())
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

    /** 建立並註冊一個文字輸入欄位。
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

    /** 取得欄位輸入長度上限。
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

    /** 建立與文字輸入欄位同一排版語言的選項按鈕。
     *
     * @param labelKey 按鈕標題翻譯鍵。
     * @param y 按鈕垂直座標。
     * @param action 點擊後執行的變更動作。
     * @return 建立的按鈕。
     */
    private fun addChoiceButton(labelKey: String, y: Int, action: () -> Unit): ButtonWidget = ButtonWidget.builder(Text.translatable(labelKey)) { action() }
        .dimensions(controlLeft(), y, controlWidth(), layout.rowHeight).build().also(::addDrawableChild)

    /** 取得控制項高度。
     *
     * @return 控制項高度。
     */
    private fun controlHeight(): Int = (layout.rowHeight - TEXT_FIELD_BORDER * 2).coerceAtLeast(1)

    /** 取得右側控制項可用寬度。
     *
     * @return 控制項寬度。
     */
    private fun controlWidth(): Int = (width - controlLeft() - 14).coerceAtLeast(1)

    /** 依可用寬度配置標題與控制項分界。
     *
     * @return 控制項左界。
     */
    private fun controlLeft(): Int = (width * 0.4).toInt().coerceIn(70, 160)

    /** 取得目前可見的文字欄位數量。
     *
     * @return 文字欄位數量。
     */
    private fun visibleFieldCount(): Int = if (session.controller.state.value.query.scope == HistoryQueryScopeDto.ALL) 4 else 6

    /** 取得包含規則及選項列的內容列數。
     *
     * @return 內容列數。
     */
    private fun visibleRowCount(): Int = visibleFieldCount() + 4

    /** 取得內容列位置。
     *
     * @param index 從零開始的內容列索引。
     * @return 依目前捲動狀態計算的控制項上界。
     */
    private fun rowTop(index: Int): Int = layout.rowTop(index, contentScroll)

    /** 取得篩選欄位標題翻譯鍵。
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

    /** 建立文字欄位的用途與格式範例提示。
     *
     * @param field 欄位種類。
     * @param labelKey 欄位標題翻譯鍵。
     * @return 包含用途及範例的提示文字。
     */
    private fun inputTooltip(field: HistoryBrowseFilterField, labelKey: String): Text = Text.empty()
        .append(Text.translatable("${MinecraftHistoryScreenKeys.FILTER_INPUT_DESCRIPTION_PREFIX}${field.name.lowercase()}", Text.translatable(labelKey)).formatted(Formatting.GRAY))
        .append("\n")
        .append(Text.translatable(MinecraftHistoryScreenKeys.FILTER_INPUT_EXAMPLE_LABEL).formatted(Formatting.GOLD))
        .append(" ")
        .append(Text.translatable("${MinecraftHistoryScreenKeys.FILTER_INPUT_EXAMPLE_PREFIX}${field.name.lowercase()}").formatted(Formatting.WHITE))

    /** 更新欄位草稿並嘗試套用有效查詢。
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

    /** 驗證整份草稿；只有有效結果才送出查詢。
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
        session.controller.updateQuery(current.copy(filters = HistoryQueryFiltersDto()))
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

    /** 選取已註冊規則，最後一項回到不限。
     *
     * @param step 選取方向及步數。
     */
    private fun cycleRule(step: Int = 1) {
        val options = ruleOptions()
        val current = session.filterDraft.input.ruleId
        val index = options.indexOf(current).coerceAtLeast(0)
        val nextIndex = (index + step).mod(options.size)
        session.filterDraft.input = session.filterDraft.input.copy(
            ruleId = options[nextIndex],
        )
        validateAndApply()
    }

    /** 建立包含不限值在內的排序規則選項。 */
    private fun ruleOptions(): List<String> = listOf("") + session.ruleNames.registrationKeys.sorted()

    /** 展開選項清單最多可顯示的列數。 */
    private fun rulePickerVisibleRows(): Int = minOf(ruleOptions().size, ((layout.contentBottom - rowTop(0) - layout.rowHeight - 2) / RULE_OPTION_HEIGHT).coerceAtLeast(1))

    /** 展開選項清單的實際高度。 */
    private fun rulePickerHeight(): Int = rulePickerVisibleRows() * RULE_OPTION_HEIGHT

    /** 展開選項清單的最大捲動索引。 */
    private fun rulePickerMaximumScroll(): Int = (ruleOptions().size - rulePickerVisibleRows()).coerceAtLeast(0)

    /** 展開清單在目前視窗中的下界。 */
    private fun rulePickerBottom(): Double = (rowTop(0) + layout.rowHeight + 2 + rulePickerHeight()).toDouble()
        .coerceAtMost(layout.contentBottom.toDouble())
        .coerceAtLeast((rowTop(0) + layout.rowHeight + 2 + RULE_OPTION_HEIGHT).toDouble())

    /** 判斷游標是否位於展開的規則選項清單。 */
    private fun isInsideRulePicker(mouseX: Double, mouseY: Double): Boolean = mouseX >= controlLeft() &&
        mouseX < controlLeft() + controlWidth() &&
        mouseY >= rowTop(0) + layout.rowHeight + 2 &&
        mouseY < rulePickerBottom()

    /** 建立列出目前值與可選值的 tooltip。
     *
     * @param title 選項名稱。
     * @param current 目前選值。
     * @param options 可選值。
     * @return 格式化後的 tooltip 文字。
     */
    private fun optionTooltip(title: Text, current: Text, options: List<Text>): Text = Text.empty()
        .append(Text.translatable(MinecraftHistoryScreenKeys.TOOLTIP_DESCRIPTION, title).formatted(Formatting.GRAY))
        .append("\n")
        .append(Text.translatable(MinecraftHistoryScreenKeys.TOOLTIP_CURRENT, current).formatted(Formatting.GREEN))
        .append("\n")
        .append(Text.translatable(MinecraftHistoryScreenKeys.TOOLTIP_OPTIONS).formatted(Formatting.GOLD))
        .also { result -> options.forEach { option -> result.append("\n• ").append(option.copy().formatted(if (option.string == current.string) Formatting.GREEN else Formatting.WHITE)) } }

    /** 建立規則選擇器的 tooltip。
     *
     * @return 顯示目前規則與可選規則的 tooltip。
     */
    private fun ruleTooltip(): Text {
        val current = session.filterDraft.input.ruleId.takeUnless(String::isEmpty)?.let { Text.literal(it) }
            ?: Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)
        return optionTooltip(
            Text.translatable(MinecraftHistoryScreenKeys.FILTER_RULE),
            current,
            listOf(Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)) + session.ruleNames.registrationKeys.sorted().map { id ->
                session.ruleNames.find(id)?.let(Text::translatable)?.let { Text.empty().append(it).append(" (").append(id).append(")") } ?: Text.literal(id)
            },
        )
    }

    /** 將結果篩選值轉成顯示文字。 */
    private fun outcomeLabel(value: HistoryOutcomeFilterDto?): Text = value?.let { Text.translatable("${MinecraftHistoryScreenKeys.FILTER_OUTCOME}.${it.name.lowercase()}") }
        ?: Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)

    /** 將完整性篩選值轉成顯示文字。 */
    private fun integrityLabel(value: HistoryIntegrityFilterDto?): Text = value?.let { Text.translatable("${MinecraftHistoryScreenKeys.FILTER_INTEGRITY}.${it.name.lowercase()}") }
        ?: Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)

    /** 將 AI 篩選值轉成顯示文字。 */
    private fun aiLabel(value: HistoryAiFilterDto?): Text = value?.let { Text.translatable("${MinecraftHistoryScreenKeys.FILTER_AI}.${it.name.lowercase()}") }
        ?: Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)

    /** 取得循環選項中的下一個值；「不限」排在第一項，按住 Shift 時往回選。
     *
     * @param value 目前選值；null 表示不限。
     * @param values 不含「不限」的可循環值清單。
     * @return 下一個值；null 表示不限。
     */
    private fun <T> next(value: T?, values: List<T>): T? = CycleButtonInput.next(options = listOf<T?>(null) + values, current = value)

    /** 取得指定欄位目前保留的原始文字。
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

    /** 滾輪在規則選擇按鈕上時，依方向移動已註冊規則清單。 */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (rulePickerOpen && isInsideRulePicker(mouseX, mouseY)) {
            rulePickerScroll = (rulePickerScroll - amount.toInt()).coerceIn(0, rulePickerMaximumScroll())
            return true
        }
        if (rulePickerOpen) return true
        if (mouseX >= controlLeft().toDouble() && mouseY in rowTop(0).toDouble()..(rowTop(0) + layout.rowHeight).toDouble()) {
            cycleRule(if (amount < 0.0) 1 else -1)
            return true
        }
        if (mouseY in layout.contentTop.toDouble()..layout.contentBottom.toDouble()) {
            rulePickerOpen = false
            contentScroll = (contentScroll - amount.toInt() * layout.rowSpacing).coerceIn(0, layout.scrollableHeight)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    /** 選取展開清單中的規則，或點擊外部關閉清單。 */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (rulePickerOpen) {
            if (button != 0) return true
            val left = controlLeft().toDouble()
            val top = (rowTop(0) + layout.rowHeight + 2).toDouble()
            val right = (controlLeft() + controlWidth()).toDouble()
            val bottom = rulePickerBottom()
            if (mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom) {
                val index = rulePickerScroll + ((mouseY - top) / RULE_OPTION_HEIGHT).toInt()
                val options = ruleOptions()
                if (index in options.indices) {
                    session.filterDraft.input = session.filterDraft.input.copy(ruleId = options[index])
                    validateAndApply()
                    rulePickerOpen = false
                }
                return true
            }
            rulePickerOpen = false
            return true
        }
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
            session.controller.state.value.query.scope == HistoryQueryScopeDto.ALL &&
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
                Text.translatable("${MinecraftHistoryScreenKeys.FILTER_ERROR_PREFIX}${it.name.lowercase()}").formatted(Formatting.RED)
            }
            drawLabel(context, key, y, if (error == null) 0xffffff else 0xff5555)
            fields[field]?.tooltip = if (rulePickerOpen) {
                null
            } else {
                Tooltip.of(
                    Text.empty().append(inputTooltip(field, key)).also { text -> error?.let { text.append("\n").append(it) } },
                )
            }
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
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xffffff)
        outcomeButton?.message = outcomeLabel(session.filterDraft.input.outcome)
        integrityButton?.message = integrityLabel(session.filterDraft.input.integrity)
        aiButton?.message = aiLabel(session.filterDraft.input.ai)
        outcomeButton?.tooltip = Tooltip.of(CycleButtonInput.withHint(tooltip = optionTooltip(Text.translatable(MinecraftHistoryScreenKeys.FILTER_OUTCOME), outcomeLabel(session.filterDraft.input.outcome), listOf(Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)) + HistoryOutcomeFilterDto.entries.map(::outcomeLabel)), optionCount = HistoryOutcomeFilterDto.entries.size + 1))
        integrityButton?.tooltip = Tooltip.of(CycleButtonInput.withHint(tooltip = optionTooltip(Text.translatable(MinecraftHistoryScreenKeys.FILTER_INTEGRITY), integrityLabel(session.filterDraft.input.integrity), listOf(Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)) + HistoryIntegrityFilterDto.entries.map(::integrityLabel)), optionCount = HistoryIntegrityFilterDto.entries.size + 1))
        aiButton?.tooltip = Tooltip.of(CycleButtonInput.withHint(tooltip = optionTooltip(Text.translatable(MinecraftHistoryScreenKeys.FILTER_AI), aiLabel(session.filterDraft.input.ai), listOf(Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)) + HistoryAiFilterDto.entries.map(::aiLabel)), optionCount = HistoryAiFilterDto.entries.size + 1))
        ruleButton?.tooltip = Tooltip.of(ruleTooltip())
        ruleButton?.message = ruleDisplayLabel()
        if (layout.scrollableHeight > 0) {
            val trackLeft = width - 5
            context.fill(trackLeft, layout.contentTop, width - 2, layout.contentBottom, 0xff303030.toInt())
            val trackHeight = layout.contentBottom - layout.contentTop
            val thumbHeight = (trackHeight.toDouble() * trackHeight / (trackHeight + layout.scrollableHeight)).toInt().coerceAtLeast(8)
            val thumbTop = layout.contentTop + ((trackHeight - thumbHeight) * contentScroll / layout.scrollableHeight)
            context.fill(trackLeft, thumbTop, width - 2, thumbTop + thumbHeight, 0xffaaaaaa.toInt())
        }
        if (rulePickerOpen) {
            listOf(ruleButton, outcomeButton, integrityButton, aiButton).forEach { it?.tooltip = null }
        }
        super.render(context, if (rulePickerOpen) -1 else mouseX, if (rulePickerOpen) -1 else mouseY, delta)
        if (rulePickerOpen) renderRulePicker(context, mouseX, mouseY)
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

    /** 繪製可捲動的規則選項清單。
     *
     * @param context 畫面繪製上下文。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     */
    private fun renderRulePicker(context: DrawContext, mouseX: Int, mouseY: Int) {
        val left = controlLeft()
        val top = rowTop(0) + layout.rowHeight + 2
        val right = left + controlWidth()
        val bottom = rulePickerBottom().toInt()
        val options = ruleOptions()
        context.matrices.push()
        context.matrices.translate(0.0, 0.0, RULE_PICKER_Z)
        context.fill(left, top, right, bottom, 0xff101010.toInt())
        context.enableScissor(left, top, right, bottom)
        options.drop(rulePickerScroll).take(rulePickerVisibleRows()).forEachIndexed { row, option ->
            val y = top + row * RULE_OPTION_HEIGHT
            val selected = option == session.filterDraft.input.ruleId
            if (mouseX in left until right && mouseY in y until y + RULE_OPTION_HEIGHT) {
                context.fill(left, y, right, y + RULE_OPTION_HEIGHT, 0xff505050.toInt())
            }
            val label = option.takeUnless(String::isEmpty)?.let { id ->
                session.ruleNames.find(id)?.let(Text::translatable)?.let { Text.empty().append(it).append(" (").append(id).append(")") }
                    ?: Text.literal(id)
            } ?: Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)
            context.drawTextWithShadow(textRenderer, label, left + 4, y + 4, if (selected) 0x55ff55 else 0xffffff)
        }
        if (rulePickerMaximumScroll() > 0) {
            val trackLeft = right - 3
            context.fill(trackLeft, top, right, bottom, 0xff303030.toInt())
            val thumbHeight = (bottom - top).toDouble() * rulePickerVisibleRows() / options.size
            val thumbTop = top + ((bottom - top - thumbHeight) * rulePickerScroll / rulePickerMaximumScroll()).toInt()
            context.fill(trackLeft, thumbTop, right, thumbTop + thumbHeight.toInt().coerceAtLeast(4), 0xffaaaaaa.toInt())
        }
        context.disableScissor()
        context.matrices.pop()
    }

    /** 將目前規則篩選值轉成按鈕文字。 */
    private fun ruleDisplayLabel(): Text = session.filterDraft.input.ruleId.takeUnless(String::isEmpty)?.let { id ->
        session.ruleNames.find(id)?.let(Text::translatable)?.let { Text.empty().append(it).append(" (").append(id).append(")") }
            ?: Text.literal(id)
    } ?: Text.translatable(MinecraftHistoryScreenKeys.FILTER_ANY)

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (rulePickerOpen) {
            if (keyCode == 256) rulePickerOpen = false
            return true
        }
        if (keyCode == 256) {
            session.backToList()
            return true
        }
        if (keyCode == 257) validateAndApply()
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    /** 規則選單展開時不將文字輸入傳遞至背景欄位。 */
    override fun charTyped(chr: Char, modifiers: Int): Boolean = rulePickerOpen || super.charTyped(chr, modifiers)

    /** 規則選單展開時不將拖曳傳遞至背景控制項。 */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = rulePickerOpen || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)

    /** 規則選單展開時不將放開滑鼠事件傳遞至背景控制項。 */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = rulePickerOpen || super.mouseReleased(mouseX, mouseY, button)

    override fun shouldPause(): Boolean = false

    override fun close() = session.backToList()

    override fun removed() {
        session.removed(this)
        super.removed()
    }

    private companion object {
        /** 高於背景控制項與其文字的選單繪製深度。 */
        const val RULE_PICKER_Z = 400.0

        /** 原版文字輸入框向內容範圍外延伸的邊框厚度。 */
        const val TEXT_FIELD_BORDER = 1

        /** 選項列的固定高度。 */
        const val RULE_OPTION_HEIGHT = 18
    }
}
