package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.config.GameConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDomain
import com.doublemoon1119.mahjongcraft.platform.fabric.text.gameConfigPresentationText
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.ResolvedGameConfigPresentation
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/** 顯示歷史對局採用的規則設定；所有欄位均為唯讀。
 *
 * @property session 共用歷史瀏覽 session。
 */
internal class HistoryRuleSettingsScreen(
    private val session: HistoryBrowseSession,
) : Screen(Text.translatable(MinecraftHistoryScreenKeys.RULE_SETTINGS_TITLE)) {
    /** 目前規則設定頁的內容版面。 */
    private var layout = HistorySummaryLayout.measure(0, 0)

    /** 目前內容捲動偏移。 */
    private var scroll = 0.0

    /** 捲軸是否正在拖曳。 */
    private var dragging = false

    /** 捲軸拖曳時游標相對滑塊上界的偏移。 */
    private var grabOffset = 0.0

    /** 上一次解析的 DTO，避免每個 render tick 重建規則設定。 */
    private var resolvedDto: GameConfigDto? = null

    /** 上一次成功解析的呈現資料。 */
    private var resolved: ResolvedGameConfigPresentation? = null

    /** 失敗時可重新查詢的固定底列按鈕。 */
    private var retryButton: ButtonWidget? = null

    /** 初始化固定 footer。 */
    override fun init() {
        layout = HistorySummaryLayout.measure(width, height)
        dragging = false
        clearChildren()
        val buttonWidth = ((width - 28) / 2).coerceAtLeast(1)
        val buttonLeft = ((width - buttonWidth * 2 - 4) / 2).coerceAtLeast(8)
        addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.BACK)) { session.backToSummary() }
                .dimensions(buttonLeft, layout.footerTop, buttonWidth, 20).build(),
        )
        retryButton = ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.RETRY)) { session.controller.retry() }
            .dimensions(buttonLeft + buttonWidth + 4, layout.footerTop, buttonWidth, 20).build().also(::addDrawableChild)
    }

    /** 歷史畫面不暫停整合伺服器。 */
    override fun shouldPause(): Boolean = false

    /** Escape 返回摘要。 */
    override fun close() {
        session.backToSummary()
    }

    /** Minecraft 移除畫面時通知瀏覽 session。 */
    override fun removed() {
        session.removed(this)
        super.removed()
    }

    /** 處理規則設定內容區滾輪。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param amount 滾輪增量。
     * @return 是否處理此輸入。
     */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (mouseY < layout.contentTop || mouseY >= layout.contentBottom) return super.mouseScrolled(mouseX, mouseY, amount)
        scroll = layout.clampScroll(scroll - amount * 18.0, contentHeight())
        return true
    }

    /** 開始捲軸拖曳。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param button 滑鼠按鍵。
     * @return 是否處理此輸入。
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val bar = layout.scrollbar(contentHeight(), scroll)
        val bounds = layout.scrollbarBounds()
        if (button == 0 &&
            bar.maximumScroll > 0 &&
            mouseX >= bounds.x &&
            mouseX < bounds.x + bounds.width &&
            mouseY >= bounds.y &&
            mouseY < bounds.y + bounds.height
        ) {
            dragging = true
            grabOffset = layout.grabOffset(contentHeight(), scroll, mouseY)
            updateDrag(mouseY)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /** 持續處理捲軸拖曳。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param button 滑鼠按鍵。
     * @param deltaX 水平位移。
     * @param deltaY 垂直位移。
     * @return 是否處理此輸入。
     */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = if (dragging && button == 0) {
        updateDrag(mouseY)
        true
    } else {
        super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    /** 結束捲軸拖曳。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param button 滑鼠按鍵。
     * @return 是否處理此輸入。
     */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = dragging && button == 0
        dragging = false
        return handled || super.mouseReleased(mouseX, mouseY, button)
    }

    /** 繪製唯讀規則設定。 */
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 2, 0xffffff)
        val status = session.controller.state.value.ruleSettings?.status ?: HistoryBrowseStatus.Loading
        retryButton?.active = session.controller.canRetry()
        retryButton?.visible = status is HistoryBrowseStatus.Failed
        retryButton?.tooltip = if (session.controller.isRefreshCoolingDown()) Tooltip.of(Text.translatable(MinecraftHistoryScreenKeys.QUERY_COOLDOWN_TOOLTIP)) else null
        scroll = layout.clampScroll(scroll, contentHeight())
        context.enableScissor(8, layout.contentTop, width - 8, layout.contentBottom)
        var hoveredTooltip: List<Text>? = null
        when (status) {
            HistoryBrowseStatus.Idle, HistoryBrowseStatus.Loading -> centered(context, Text.translatable(MinecraftHistoryScreenKeys.LOADING), layout.contentTop + 12, 0xffffff)
            is HistoryBrowseStatus.Failed -> centered(context, Text.translatable("${MinecraftHistoryScreenKeys.FAILURE_PREFIX}${status.reason.name.lowercase()}"), layout.contentTop + 12, 0xff6666)
            HistoryBrowseStatus.Ready -> hoveredTooltip = renderConfig(context, mouseX, mouseY)
        }
        context.disableScissor()
        renderScrollbar(context)
        super.render(context, mouseX, mouseY, delta)
        hoveredTooltip?.let { context.drawTooltip(textRenderer, it, mouseX, mouseY) }
    }

    /** 更新捲軸拖曳位置。
     *
     * @param mouseY 游標垂直座標。
     */
    private fun updateDrag(mouseY: Double) {
        scroll = layout.scrollbar(contentHeight(), scroll).scrollIndexFor(mouseY, grabOffset).toDouble()
    }

    /** 解析規則設定並快取結果，避免每個 render tick 重建規則設定。
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

    /** 繪製成功解析的規則設定。
     *
     * @param context 繪製上下文。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 目前游標所指欄位的 tooltip。
     */
    private fun renderConfig(context: DrawContext, mouseX: Int, mouseY: Int): List<Text>? {
        var tooltip: List<Text>? = null
        buildLines().forEach { line ->
            val rowTop = line.y - scroll.toInt()
            if (rowTop + 11 > layout.contentTop && rowTop < layout.contentBottom) {
                context.drawTextWithShadow(textRenderer, line.text, line.x, rowTop, line.color)
                if (layout.containsContentRow(mouseX.toDouble(), mouseY.toDouble(), line.x, rowTop, 11)) {
                    tooltip = line.tooltip
                }
            }
        }
        return tooltip
    }

    /**
     * 依實際字寬建立可裁切的唯讀資訊行；標題、欄位與值一併換行，避免彼此重疊。
     *
     * @return 具有絕對內容座標與欄位說明的資訊行。
     */
    private fun buildLines(): List<Line> {
        val lines = mutableListOf<Line>()
        var y = layout.contentTop + 4

        /** 將一項文字依目前可用寬度換行。
         * @param text 欲顯示的完整文字。
         * @param color 預設文字顏色。
         * @param indent 相對內容左界的縮排。
         * @param tooltip 此資訊的說明行。
         */
        fun add(text: Text, color: Int = 0xffffff, indent: Int = 8, tooltip: List<Text>? = null) {
            val x = layout.left + indent
            textRenderer.wrapLines(text, (layout.right - x - 14).coerceAtLeast(1)).forEach {
                lines += Line(x, y, it, color, tooltip)
                y += 11
            }
        }

        val presentation = resolvedConfig(session.controller.state.value.ruleSettings?.config)
        val ruleId = session.controller.state.value.summary?.detail?.summary?.ruleId ?: presentation?.ruleModuleId
        add(HistoryScreenText.rule(ruleId, session.ruleNames), 0x8ed5df, indent = 0)
        ruleId?.let { add(Text.literal(it), 0xaaaaaa) }
        add(Text.translatable(MinecraftHistoryScreenKeys.RULE_SETTINGS_READ_ONLY), 0xcccccc)
        val definition = presentation?.definition
        if (definition == null) {
            y += 8
            add(Text.translatable(MinecraftHistoryScreenKeys.RULE_SETTINGS_UNAVAILABLE), 0xff6666)
            return lines
        }
        definition.categories.forEach { category ->
            y += 8
            add(Text.translatable(category.nameTranslationKey), 0x8ed5df, indent = 0)
            y += 4
            definition.fields.filter { it.categoryId == category.id }.forEach { field ->
                val valueText = presentation.valuesByFieldId[field.id]?.let(::gameConfigPresentationText)
                    ?: Text.translatable(MinecraftHistoryScreenKeys.RULE_SETTINGS_UNAVAILABLE)
                add(
                    Text.translatable(field.nameTranslationKey).append(": ").append(valueText.copy().formatted(Formatting.GRAY)),
                    tooltip = fieldTooltip(field.editor, field.descriptionTranslationKey),
                )
                y += 3
            }
        }
        return lines
    }

    /** 建立欄位說明與可用選項 tooltip。
     *
     * @param editor 欄位編輯器描述。
     * @param descriptionKey 欄位說明翻譯鍵。
     * @return 不含內嵌換行字元的 tooltip 行。
     */
    private fun fieldTooltip(editor: GameConfigEditorSpec, descriptionKey: String): List<Text> = buildList {
        add(Text.translatable(descriptionKey).formatted(Formatting.GRAY))
        if (editor is GameConfigEditorSpec.SingleChoice) {
            add(Text.translatable(MinecraftHistoryScreenKeys.TOOLTIP_OPTIONS).formatted(Formatting.GOLD))
            editor.optionIds.forEach { optionId ->
                add(Text.literal("• ").formatted(Formatting.DARK_GRAY).append(Text.translatable(MinecraftRoomScreenKeys.configOption(optionId))))
            }
        }
    }

    /** 計算設定內容總高度。 */
    private fun contentHeight(): Int {
        if (session.controller.state.value.ruleSettings?.status != HistoryBrowseStatus.Ready) return layout.viewportHeight
        return ((buildLines().lastOrNull()?.y ?: layout.contentTop) + 15 - layout.contentTop).coerceAtLeast(layout.viewportHeight)
    }

    /** 繪製與摘要頁共用幾何的捲軸。 */
    private fun renderScrollbar(context: DrawContext) {
        val bar = layout.scrollbar(contentHeight(), scroll)
        if (bar.maximumScroll <= 0 || layout.contentBottom <= layout.contentTop) return
        val bounds = layout.scrollbarBounds()
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, 0x80505050.toInt())
        context.fill(bounds.x, bar.thumbTop, bounds.x + bounds.width, bar.thumbTop + bar.thumbHeight, 0xFFD0D0D0.toInt())
    }

    /**
     * 單一已換行的規則資訊。
     *
     * @property x 資訊行左界。
     * @property y 尚未套用捲動的資訊行上界。
     * @property text 已依內容寬度換行的文字。
     * @property color 預設文字顏色。
     * @property tooltip 同一欄位共用的說明與可用選項。
     */
    private data class Line(val x: Int, val y: Int, val text: OrderedText, val color: Int, val tooltip: List<Text>?)

    /** 在內容區置中繪製狀態文字。
     * @param context 繪製上下文。
     * @param text 狀態文字。
     * @param y 垂直座標。
     * @param color 文字顏色。
     */
    private fun centered(context: DrawContext, text: Text, y: Int, color: Int) = context.drawCenteredTextWithShadow(textRenderer, text, width / 2, y, color)
}
