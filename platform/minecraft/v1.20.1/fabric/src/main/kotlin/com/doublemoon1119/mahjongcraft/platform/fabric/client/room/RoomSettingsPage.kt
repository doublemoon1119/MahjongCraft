package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ControlTooltip
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.CycleButtonInput
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.UnsavedChangesConfirmationScreen
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowseContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.DropdownOption
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.DropdownOverlay
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.MarqueeState
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScrollState
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.SettingsFooterButtons
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.TabStripLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.TabWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.CenteredIntegerEditBox
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.RoomConfigIntegerControl
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.RoomSettingsDraft
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.formatRoomInteger
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigCategoryDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigFieldDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigLockedField
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigVariantResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.TableOccupancyDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftControlTooltipKeys
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.uuid.Uuid

/**
 * 房間設定頁借用所屬畫面的能力：加入元件、要求重建、切換頁面與開啟其他畫面。
 */
internal interface RoomSettingsPageHost {
    /** 所屬畫面，作為子畫面返回的目標。 */
    val screen: Screen

    /** 所屬畫面的文字繪製器。 */
    val textRenderer: Font

    /**
     * 將元件加入所屬畫面。
     *
     * @param T 元件型別。
     * @param widget 要加入的元件。
     * @return 傳入的元件。
     */
    fun <T> addWidget(widget: T): T where T : GuiEventListener, T : Renderable, T : NarratableEntry

    /** 在目前輸入事件結束後重建所屬畫面的元件。 */
    fun requestRebuild()

    /** 切換到成員頁並重建所屬畫面的元件。 */
    fun showRoomPage()

    /**
     * 顯示另一個畫面。
     *
     * @param screen 要顯示的畫面。
     */
    fun openScreen(screen: Screen)

    /**
     * 開啟規則一覽，返回時回到所屬畫面。
     *
     * @param context 規則一覽的開啟脈絡。
     */
    fun openRuleCatalogue(context: RuleCatalogueBrowseContext)

    /** 取得房間設定，沒有房間時改用進行中對局的設定；兩者皆無時為 null。 */
    fun currentConfig(): GameConfig?

    /** 所屬畫面的展開清單；清單的繪製與輸入由所屬畫面處理。 */
    val dropdown: DropdownOverlay
}

/**
 * 房間畫面的設定頁：規則與玩法選擇器、分類頁籤、該分類的設定欄位與底部操作列。
 *
 * 房主修改的是本機草稿，按下套用或完成才送出；非房主與對局進行中只能檢視。
 *
 * @property host 所屬畫面提供的能力。
 * @property stateStore 目前客戶端持有的房間、對局與牌桌快照。
 * @property tableId 本頁對應的牌桌識別碼。
 * @property configPresentations 規則設定的呈現定義登錄表。
 * @property configResolver 將設定解析成欄位定義與值的解析器。
 * @property ruleNames 規則名稱登錄表。
 * @property json 傳送房間操作使用的 JSON 格式。
 * @property networkRegistries 設定與網路 DTO 的轉換登錄表。
 */
internal class RoomSettingsPage(
    private val host: RoomSettingsPageHost,
    private val stateStore: ClientMahjongStateStore,
    private val tableId: Uuid,
    private val configPresentations: GameConfigPresentationRegistry,
    private val configResolver: GameConfigPresentationResolver,
    private val ruleNames: RuleModuleDisplayNameRegistry,
    private val json: NetworkJson,
    private val networkRegistries: NetworkDtoRegistries,
) {
    /** 選中的分類；尚未選定時為 null。 */
    private var selectedCategoryId: String? = null

    /** 設定欄位清單的捲動與拖曳狀態。 */
    private val fieldScroll = ScrollState()

    /** 分類頁籤列向左捲動的像素數。 */
    private var tabScroll = 0

    /** 下次建立元件時是否捲動頁籤列，讓選中的頁籤完整顯示。 */
    private var revealSelectedTab = true

    /** 尚未送出或等待權威確認的設定草稿。 */
    private val draft = RoomSettingsDraft()

    /** 規則與玩法選擇器使用的展開清單，由所屬畫面提供。 */
    private val dropdown: DropdownOverlay
        get() = host.dropdown

    /** 底部狀態文字的跑馬燈進度。 */
    private val statusMarquee = MarqueeState()

    /** 套用按鈕；唯讀時為 null。 */
    private var applyButton: Button? = null

    /** 復原按鈕；唯讀時為 null。 */
    private var undoButton: Button? = null

    /** 重設為預設按鈕；唯讀時為 null。 */
    private var resetButton: Button? = null

    /** 完成按鈕；唯讀時為 null。 */
    private var doneButton: Button? = null

    /** 依目前草稿建立規則列、分類頁籤、欄位與底部操作列；非房主只提供唯讀控制項。 */
    fun init() {
        applyButton = null
        undoButton = null
        resetButton = null
        doneButton = null
        val authoritative = host.currentConfig() ?: return
        draft.beginIfAbsent(authoritative)
        val config = draft.config ?: authoritative
        val resolved = configResolver.resolve(config)
        val definition = resolved.definition ?: return
        val canEditRoom = stateStore.tableOccupancy(tableId)?.occupancy == TableOccupancyDto.ROOM && stateStore.roomSnapshot(tableId)?.isHost == true
        val editable = canEditRoom && definition.selectable
        val categories = definition.categories
        if (selectedCategoryId !in categories.map { it.id }) {
            selectedCategoryId = categories.firstOrNull()?.id
            revealSelectedTab = true
        }
        val layout = layout()
        addRuleRow(layout, config, resolved.ruleModuleId, definition, canEditRoom, editable)
        addTabs(layout, categories)
        val fields = GameConfigVariantResolver.visibleFields(definition).filter { it.categoryId == selectedCategoryId }
        fieldScroll.clamp(layout.scrollbar(fields.size, fieldScroll.index).maximumScroll)
        fields.drop(fieldScroll.index).take(layout.visibleFieldCount).forEachIndexed { index, field ->
            val locked = GameConfigVariantResolver.lockedField(definition, config, field.id)
            addFieldControls(
                layout = layout,
                field = field,
                config = config,
                y = layout.fieldControlY(layout.fieldRowTop(index)),
                editable = editable && field.isEditable && field.isEnabled(config) && locked == null,
                locked = locked,
            )
        }
        addFooter(layout, definition, config, authoritative, editable)
    }

    /**
     * 繪製元件底下的內容：規則列標籤與分隔線、頁籤底線、欄位標籤、捲軸與狀態文字。
     *
     * @param context 畫面繪製情境。
     * @param mouseX 游標的水平座標。
     * @param mouseY 游標的垂直座標。
     * @return 被截斷且正被游標指向的標籤全文；沒有時為 null。
     */
    fun renderContent(context: GuiGraphics, mouseX: Int, mouseY: Int): Component? {
        val textRenderer = host.textRenderer
        val layout = layout()
        val config = draft.config ?: host.currentConfig() ?: return null
        val definition = configResolver.resolve(config).definition
        if (definition == null) {
            context.drawCenteredString(
                textRenderer,
                Component.translatable(MinecraftRoomScreenKeys.READ_ONLY),
                host.screen.width / 2,
                layout.fieldsTop,
                ScreenRendering.MUTED_TEXT_COLOR,
            )
            return null
        }
        layout.ruleRow?.let { row ->
            context.drawString(textRenderer, ruleLabel(), row.ruleLabelX, row.labelY, ScreenRendering.TEXT_COLOR)
            context.drawString(textRenderer, variantLabel(), row.variantLabelX, row.labelY, ScreenRendering.TEXT_COLOR)
            ScreenRendering.divider(context, layout, row.dividerY)
        }
        ScreenRendering.tabLine(context, tabStrip(layout, definition.categories.size))
        var hoveredLabel: Component? = null
        val fields = GameConfigVariantResolver.visibleFields(definition).filter { it.categoryId == selectedCategoryId }
        fields.drop(fieldScroll.index).take(layout.visibleFieldCount).forEachIndexed { index, field ->
            layout.fieldStripe(index).renderIfStriped(context, fieldScroll.index + index)
            ScreenRendering.label(
                context = context,
                textRenderer = textRenderer,
                label = Component.translatable(field.nameTranslationKey),
                x = layout.contentLeft,
                y = layout.fieldLabelY(layout.fieldRowTop(index)),
                maximumWidth = layout.labelWidth,
                mouseX = mouseX,
                mouseY = mouseY,
            )?.let { hoveredLabel = it }
        }
        VerticalScrollbar.render(context, layout.scrollbarLeft, layout.scrollbar(fields.size, fieldScroll.index))
        val roomPlayerCount = roomPlayerCount()
        val status = when {
            !definition.selectable -> Component.translatable(definition.unavailableReasonTranslationKey!!)
            draft.isStale -> Component.translatable(MinecraftRoomScreenKeys.DRAFT_STALE)
            draft.hasInvalidFields -> Component.translatable(MinecraftRoomScreenKeys.VALIDATION_FAILED)
            draft.exceedsPlayerLimit(roomPlayerCount) -> playerLimitExceededText(config, roomPlayerCount)
            else -> null
        }
        status?.let { message ->
            ScreenRendering.status(
                context = context,
                textRenderer = textRenderer,
                layout = layout,
                message = message,
                color = ScreenRendering.ERROR_TEXT_COLOR,
                marquee = statusMarquee,
                mouseX = mouseX,
                mouseY = mouseY,
            )
        }
        return hoveredLabel
    }

    /**
     * 處理滾輪：游標在頁籤列上時左右捲動頁籤，其餘捲動欄位。
     *
     * @param mouseX 游標的水平座標。
     * @param mouseY 游標的垂直座標。
     * @param amount 滾輪位移。
     * @return 一律為 true；設定頁不把滾輪交給其他元件。
     */
    fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val layout = layout()
        val strip = tabStrip(layout, resolvedDefinition()?.categories?.size ?: 0)
        if (strip.contains(mouseX, mouseY) && strip.maximumScroll > 0) {
            val next = strip.scrollByWheel(amount)
            if (next != tabScroll) {
                tabScroll = next
                host.requestRebuild()
            }
            return true
        }
        val maximumScroll = layout.scrollbar(currentFieldCount(), fieldScroll.index).maximumScroll
        if (fieldScroll.scrollBy(amount, maximumScroll)) host.requestRebuild()
        return true
    }

    /**
     * 處理元件之前的滑鼠點擊：欄位捲軸的拖曳起點。
     *
     * @param mouseX 游標的水平座標。
     * @param mouseY 游標的垂直座標。
     * @param button 滑鼠按鍵代碼。
     * @return 點擊已由設定頁處理時為 true。
     */
    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val layout = layout()
        val scrollbar = layout.scrollbar(currentFieldCount(), fieldScroll.index)
        if (button == 0 && scrollbar.maximumScroll > 0 && layout.isOverScrollbar(mouseX, mouseY)) {
            if (fieldScroll.beginDrag(mouseY, scrollbar)) host.requestRebuild()
            return true
        }
        return false
    }

    /**
     * 處理欄位捲軸的拖曳。
     *
     * @param mouseY 游標的垂直座標。
     * @param button 滑鼠按鍵代碼。
     * @return 正在拖曳欄位捲軸時為 true。
     */
    fun mouseDragged(mouseY: Double, button: Int): Boolean {
        if (!fieldScroll.dragging || button != 0) return false
        if (fieldScroll.dragTo(mouseY, layout().scrollbar(currentFieldCount(), fieldScroll.index))) host.requestRebuild()
        return true
    }

    /** 結束欄位捲軸的拖曳。 */
    fun mouseReleased() {
        fieldScroll.endDrag()
    }

    /**
     * 房間或牌桌狀態改變後，確認等待中的套用是否已經生效。
     *
     * @return 套用已生效、應返回成員頁時為 true。
     */
    fun finishPendingApply(): Boolean {
        val authoritative = host.currentConfig()
        if (!draft.isReturningToRoomAfterApply || authoritative != draft.config) return false
        draft.adoptAuthoritative(authoritative)
        return true
    }

    /** 房間不存在時清除草稿。 */
    fun clearDraft() {
        draft.clear()
    }

    /**
     * 比對權威設定與草稿，回報所屬畫面需要做的事。
     *
     * @param authoritative 目前權威設定。
     * @return 草稿與權威設定的比對結果；草稿過期時底部按鈕已同步更新。
     */
    fun syncAuthoritative(authoritative: GameConfig): RoomSettingsDraft.Outcome {
        val outcome = draft.onAuthoritativeChanged(authoritative)
        if (outcome == RoomSettingsDraft.Outcome.BecameStale) refreshDraftButtons()
        return outcome
    }

    /**
     * 離開設定頁：沒有可套用的草稿時直接返回成員頁，否則先顯示「套用並返回／放棄變更／繼續編輯」確認畫面。
     *
     * 房間規則是所有玩家共用的狀態，因此不無聲丟棄尚未套用的修改。
     */
    fun close() {
        val authoritative = host.currentConfig()
        val current = draft.config
        if (authoritative != null && current != null && hasUnsavedDraft()) {
            host.openScreen(
                screen = UnsavedChangesConfirmationScreen(
                    previousScreen = host.screen,
                    onApply = {
                        finishSettings()
                        host.openScreen(host.screen)
                    },
                    onDiscard = {
                        host.currentConfig()?.let(draft::restoreAuthoritative)
                        host.showRoomPage()
                        host.openScreen(host.screen)
                    },
                    changesPreview = gameConfigDifferenceText(configResolver, ruleNames, authoritative, current),
                ),
            )
        } else {
            authoritative?.let(draft::restoreAuthoritative)
            host.showRoomPage()
        }
    }

    /** 目前是否有尚未套用、且可以直接套用的草稿；草稿過期或驗證失敗時視為沒有。 */
    private fun hasUnsavedDraft(): Boolean = draft.canDone(roomPlayerCount()) && draft.hasUnsavedChanges(host.currentConfig())

    /**
     * 建立規則選擇器、規則一覽按鈕與玩法選擇器。
     *
     * @param layout 設定頁版面。
     * @param config 目前顯示的設定。
     * @param moduleId 目前規則模組識別碼。
     * @param definition 目前規則的設定呈現定義。
     * @param canEditRoom 是否有編輯房間的權限。
     * @param editable 是否可以修改目前規則的設定。
     */
    private fun addRuleRow(
        layout: FormScreenLayout,
        config: GameConfig,
        moduleId: String,
        definition: GameConfigPresentationDefinition,
        canEditRoom: Boolean,
        editable: Boolean,
    ) {
        val row = layout.ruleRow ?: return
        val candidates = configPresentations.ruleModuleIds.mapNotNull(configPresentations::find)
        val ruleOptions = candidates.map { candidate ->
            DropdownOption(
                label = ruleName(candidate.ruleModuleId),
                description = Component.translatable(candidate.descriptionTranslationKey),
                unavailableReason = candidate.unavailableReasonTranslationKey?.takeUnless { candidate.selectable }?.let(Component::translatable),
            )
        }
        val ruleIndex = candidates.indexOfFirst { it.ruleModuleId == moduleId }.takeIf { it >= 0 }
        lateinit var ruleSelector: RestartableMarqueeButtonWidget
        ruleSelector = RestartableMarqueeButtonWidget.builder(ruleName(moduleId)) {
            dropdown.toggle(key = RULE_SELECTOR_KEY, anchor = ruleSelector, options = ruleOptions, selectedIndex = ruleIndex) { index ->
                selectRule(config, candidates[index])
            }
        }.dimensions(row.ruleSelectorX, row.selectorY, row.selectorWidth, FormScreenLayout.BUTTON_HEIGHT).build().also { button ->
            button.active = canEditRoom
            button.selectorExpanded = { dropdown.isOpenFor(RULE_SELECTOR_KEY) }
            button.tooltip = Tooltip.create(
                ControlTooltip.options(
                    description = Component.translatable(MinecraftRoomScreenKeys.RULE_SELECTOR_DESCRIPTION),
                    options = candidates.zip(ruleOptions).map { (candidate, option) ->
                        ControlTooltip.Option(
                            label = option.label,
                            current = candidate.ruleModuleId == moduleId,
                            unavailableReason = option.unavailableReason,
                        )
                    },
                ),
            )
        }
        host.addWidget(ruleSelector)
        host.addWidget(
            widget = RestartableMarqueeButtonWidget.builder(Component.literal(RULE_CATALOGUE_BUTTON_LABEL)) { openRuleCatalogue() }
                .dimensions(row.catalogueX, row.catalogueY, row.catalogueSize, row.catalogueSize)
                .build()
                .also {
                    it.tooltip = Tooltip.create(
                        Component.empty()
                            .append(Component.translatable(MinecraftRoomScreenKeys.RULE_CATALOGUE_BUTTON).withStyle(ChatFormatting.GOLD))
                            .append("\n")
                            .append(Component.translatable(MinecraftRoomScreenKeys.RULE_CATALOGUE_BUTTON_DESCRIPTION).withStyle(ChatFormatting.GRAY)),
                    )
                },
        )
        val variants = GameConfigVariantResolver.options(definition)
        val currentVariant = GameConfigVariantResolver.current(definition, config)
        val variantOptions = variants.map { variant ->
            DropdownOption(
                label = Component.translatable(variant.nameTranslationKey),
                description = Component.translatable(variant.descriptionTranslationKey),
            )
        }
        lateinit var variantSelector: RestartableMarqueeButtonWidget
        variantSelector = RestartableMarqueeButtonWidget.builder(Component.translatable(currentVariant.nameTranslationKey)) {
            dropdown.toggle(
                key = VARIANT_SELECTOR_KEY,
                anchor = variantSelector,
                options = variantOptions,
                selectedIndex = variants.indexOfFirst { it.id == currentVariant.id },
            ) { index ->
                draft.applyVariant(
                    updated = GameConfigVariantResolver.apply(definition, config, variants[index].id),
                    variantFieldIds = GameConfigVariantResolver.variantFieldIds(definition),
                )
                normalizeDraftFlowConfig()
                host.requestRebuild()
            }
        }.dimensions(row.variantSelectorX, row.selectorY, row.selectorWidth, FormScreenLayout.BUTTON_HEIGHT).build().also { button ->
            button.active = editable
            button.selectorExpanded = { dropdown.isOpenFor(VARIANT_SELECTOR_KEY) }
            button.tooltip = Tooltip.create(
                ControlTooltip.options(
                    description = Component.translatable(currentVariant.descriptionTranslationKey),
                    options = variants.zip(variantOptions).map { (variant, option) ->
                        ControlTooltip.Option(label = option.label, current = variant.id == currentVariant.id)
                    },
                ),
            )
        }
        host.addWidget(variantSelector)
    }

    /**
     * 將草稿改為另一個規則的預設設定，保留一般流程設定。
     *
     * @param config 切換前的設定。
     * @param next 要切換到的規則。
     */
    private fun selectRule(config: GameConfig, next: GameConfigPresentationDefinition) {
        draft.resetTo(
            config = config.copy(
                ruleConfig = next.defaultRuleConfig(),
                flowConfig = configPresentations.defaultFlowConfig(
                    ruleModuleId = next.ruleModuleId,
                    ruleConfig = next.defaultRuleConfig(),
                    current = config.flowConfig,
                ),
            ),
        )
        selectedCategoryId = null
        fieldScroll.reset()
        host.requestRebuild()
    }

    /** 依目前顯示的規則與設定開啟規則一覽，含尚未套用的修改。 */
    private fun openRuleCatalogue() {
        val authoritative = host.currentConfig() ?: return
        val displayed = draft.config ?: authoritative
        host.openRuleCatalogue(
            context = roomRuleCatalogueContext(
                ruleModuleId = configResolver.resolve(displayed).ruleModuleId,
                displayed = displayed,
                authoritative = authoritative,
            ),
        )
    }

    /**
     * 建立分類頁籤。
     *
     * @param layout 設定頁版面。
     * @param categories 依顯示順序排列的分類。
     */
    private fun addTabs(layout: FormScreenLayout, categories: List<GameConfigCategoryDefinition>) {
        val selectedIndex = categories.indexOfFirst { it.id == selectedCategoryId }
        if (revealSelectedTab) {
            tabScroll = tabStrip(layout, categories.size).scrollRevealing(selectedIndex)
            revealSelectedTab = false
        }
        val strip = tabStrip(layout, categories.size)
        tabScroll = strip.effectiveScroll
        categories.forEachIndexed { index, category ->
            val selected = index == selectedIndex
            host.addWidget(
                widget = TabWidget(
                    x = strip.tabX(index),
                    y = strip.tabY(selected),
                    width = strip.tabWidth,
                    height = strip.tabHeight(selected),
                    message = Component.translatable(category.nameTranslationKey),
                    selected = selected,
                    clipLeft = strip.left,
                    clipRight = strip.right,
                    onSelect = {
                        selectedCategoryId = category.id
                        revealSelectedTab = true
                        fieldScroll.reset()
                        host.requestRebuild()
                    },
                ),
            )
        }
    }

    /**
     * 依欄位編輯器規格建立開關、選項、多選或數值控制項。
     *
     * @param layout 設定頁版面。
     * @param field 欄位定義。
     * @param config 用於讀取目前值與提示內容的設定。
     * @param y 控制項上界。
     * @param editable 是否允許修改此欄位。
     * @param locked 目前玩法鎖定這個欄位時的鎖定內容；沒有鎖定時為 null。
     */
    private fun addFieldControls(
        layout: FormScreenLayout,
        field: GameConfigFieldDefinition,
        config: GameConfig,
        y: Int,
        editable: Boolean,
        locked: GameConfigLockedField?,
    ) {
        val value = field.read(config)
        val tooltip = Tooltip.create(fieldTooltip(field, config, editable, locked))
        when (val editor = field.editor) {
            GameConfigEditorSpec.BooleanToggle -> {
                val enabled = (value as GameConfigPresentationValue.BooleanValue).enabled
                host.addWidget(
                    widget = RestartableMarqueeButtonWidget.builder(booleanText(enabled)) {
                        updateDraft(field, GameConfigPresentationValue.BooleanValue(!enabled))
                    }.pressesOnSecondaryClick().dimensions(layout.controlLeft, y, layout.controlWidth, FormScreenLayout.BUTTON_HEIGHT).build().also {
                        it.active = editable
                        it.tooltip = tooltip
                    },
                )
            }
            is GameConfigEditorSpec.SingleChoice -> {
                val option = (value as GameConfigPresentationValue.ChoiceValue).optionId
                host.addWidget(
                    widget = RestartableMarqueeButtonWidget.builder(optionText(option, editor)) {
                        val index = CycleButtonInput.nextIndex(
                            currentIndex = editor.optionIds.indexOf(option),
                            size = editor.optionIds.size,
                            step = CycleButtonInput.step(),
                        )
                        updateDraft(field, GameConfigPresentationValue.ChoiceValue(editor.optionIds[index]))
                    }.pressesOnSecondaryClick().dimensions(layout.controlLeft, y, layout.controlWidth, FormScreenLayout.BUTTON_HEIGHT).build().also {
                        it.active = editable
                        it.tooltip = tooltip
                    },
                )
            }
            is GameConfigEditorSpec.MultiChoice -> {
                val selected = (value as GameConfigPresentationValue.MultiChoiceValue).optionIds
                host.addWidget(
                    widget = RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftRoomScreenKeys.SELECTED_COUNT, selected.size)) {
                        openMultiChoice(field, editor, config, selected)
                    }.dimensions(layout.controlLeft, y, layout.controlWidth, FormScreenLayout.BUTTON_HEIGHT).build().also {
                        it.active = editable
                        it.tooltip = tooltip
                    },
                )
            }
            is GameConfigEditorSpec.IntegerInput -> {
                val number = (value as GameConfigPresentationValue.IntegerValue).number
                host.addWidget(
                    widget = RestartableMarqueeButtonWidget.builder(Component.literal("−")) {
                        val next = RoomConfigIntegerControl.decrement(current = number, editor = editor, shiftHeld = Screen.hasShiftDown())
                        updateDraft(field, GameConfigPresentationValue.IntegerValue(next))
                    }.dimensions(layout.integerMinusX, y, FormScreenLayout.INTEGER_BUTTON_WIDTH, FormScreenLayout.BUTTON_HEIGHT).build().also {
                        it.active = editable && RoomConfigIntegerControl.canDecrease(current = number, editor = editor)
                        it.tooltip = tooltip
                    },
                )
                host.addWidget(
                    widget = CenteredIntegerEditBox(
                        fieldTextRenderer = host.textRenderer,
                        x = layout.integerTextX,
                        y = y,
                        width = layout.integerTextWidth,
                        height = FormScreenLayout.BUTTON_HEIGHT,
                        narration = Component.translatable(field.nameTranslationKey),
                    ).also { widget ->
                        widget.setValue(number?.toString().orEmpty())
                        widget.setMaxLength(11)
                        widget.setEditable(editable)
                        widget.active = editable
                        widget.setTextColor(ScreenRendering.TEXT_COLOR)
                        widget.setTextColorUneditable(ScreenRendering.MUTED_TEXT_COLOR)
                        widget.tooltip = tooltip
                        widget.setResponder { raw ->
                            updateNumericDraft(field, editor, raw)
                            widget.setTextColor(
                                if (draft.isFieldInvalid(field.id)) ScreenRendering.ERROR_TEXT_COLOR else ScreenRendering.TEXT_COLOR,
                            )
                        }
                    },
                )
                host.addWidget(
                    widget = RestartableMarqueeButtonWidget.builder(Component.literal("+")) {
                        val next = RoomConfigIntegerControl.increment(current = number, editor = editor, shiftHeld = Screen.hasShiftDown())
                        updateDraft(field, GameConfigPresentationValue.IntegerValue(next))
                    }.dimensions(layout.integerPlusX, y, FormScreenLayout.INTEGER_BUTTON_WIDTH, FormScreenLayout.BUTTON_HEIGHT).build().also {
                        it.active = editable && RoomConfigIntegerControl.canIncrease(current = number, editor = editor)
                        it.tooltip = tooltip
                    },
                )
            }
        }
    }

    /**
     * 開啟多選欄位的選項畫面；套用時房間、房主身分或草稿已經改變的話放棄這次選擇。
     *
     * @param field 多選欄位。
     * @param editor 多選編輯器規格。
     * @param config 開啟當下顯示的設定。
     * @param selected 開啟當下已選的選項。
     */
    private fun openMultiChoice(
        field: GameConfigFieldDefinition,
        editor: GameConfigEditorSpec.MultiChoice,
        config: GameConfig,
        selected: Set<String>,
    ) {
        val basisConfig = host.currentConfig() ?: return
        val basisSnapshot = stateStore.roomSnapshot(tableId)
        host.openScreen(
            screen = MultiChoiceScreen(
                parent = host.screen,
                fieldName = field.nameTranslationKey,
                fieldDescriptionKey = field.descriptionTranslationKey,
                editor = editor,
                initialSelection = selected,
            ) { next ->
                val unchanged = host.currentConfig() == basisConfig &&
                    stateStore.roomSnapshot(tableId)?.isHost == true &&
                    basisSnapshot?.isHost == true &&
                    stateStore.tableOccupancy(tableId)?.occupancy == TableOccupancyDto.ROOM &&
                    draft.config == config &&
                    field.isEditable &&
                    field.isEnabled(config) &&
                    editor.accepts(GameConfigPresentationValue.MultiChoiceValue(next))
                if (unchanged) updateDraft(field, GameConfigPresentationValue.MultiChoiceValue(next))
                host.openScreen(host.screen)
            },
        )
    }

    /**
     * 建立底部操作列：可編輯時為重設為預設、復原、套用、完成；唯讀時只有返回成員頁的完成按鈕。
     *
     * @param layout 設定頁版面。
     * @param definition 目前規則的設定呈現定義。
     * @param config 目前顯示的設定。
     * @param authoritative 目前權威設定。
     * @param editable 是否可以修改設定。
     */
    private fun addFooter(
        layout: FormScreenLayout,
        definition: GameConfigPresentationDefinition,
        config: GameConfig,
        authoritative: GameConfig,
        editable: Boolean,
    ) {
        val footer = layout.footer(SettingsFooterButtons.COUNT)

        /**
         * 建立底部按鈕。
         *
         * @param index 按鈕在底部操作列中的順序。
         * @param key 按鈕文字翻譯鍵。
         * @param action 點擊時執行的動作。
         * @return 已加入所屬畫面的按鈕。
         */
        fun button(index: Int, key: String, action: () -> Unit): Button = host.addWidget(
            widget = RestartableMarqueeButtonWidget.builder(Component.translatable(key)) { action() }
                .dimensions(footer.buttonX(index), layout.footerY, footer.buttonWidth(index), FormScreenLayout.BUTTON_HEIGHT)
                .build(),
        )
        if (!editable) {
            button(SettingsFooterButtons.DONE, MinecraftRoomScreenKeys.DONE) { host.showRoomPage() }
            return
        }
        val defaults = defaultConfig(definition, config)
        resetButton = button(SettingsFooterButtons.RESET, MinecraftRoomScreenKeys.RESET_DEFAULTS_BUTTON) {
            draft.resetTo(defaults)
            host.requestRebuild()
        }.also { it.tooltip = Tooltip.create(resetTooltip()) }
        undoButton = button(SettingsFooterButtons.UNDO, MinecraftRoomScreenKeys.UNDO) {
            draft.restoreAuthoritative(authoritative)
            host.requestRebuild()
        }
        applyButton = button(SettingsFooterButtons.APPLY, MinecraftRoomScreenKeys.APPLY) { applyDraft() }
        doneButton = button(SettingsFooterButtons.DONE, MinecraftRoomScreenKeys.DONE) { finishSettings() }
        refreshDraftButtons(config, authoritative, defaults)
    }

    /**
     * 建立規則的預設設定並保留目前的玩法，套用與草稿相同的一致性約束。
     *
     * @param definition 規則的設定呈現定義。
     * @param current 目前顯示的設定，用來取得要保留的玩法。
     * @return 「重設為預設」採用的設定。
     */
    private fun defaultConfig(definition: GameConfigPresentationDefinition, current: GameConfig): GameConfig {
        val defaults = GameConfig(
            ruleConfig = definition.defaultRuleConfig(),
            flowConfig = configPresentations.defaultFlowConfig(
                ruleModuleId = definition.ruleModuleId,
                ruleConfig = definition.defaultRuleConfig(),
            ),
        )
        val variantId = GameConfigVariantResolver.current(definition, current).id
        return draft.normalized(config = GameConfigVariantResolver.apply(definition, defaults, variantId))
    }

    /**
     * 更新草稿欄位並要求重建控制項。
     *
     * @param field 要更新的欄位。
     * @param value 新欄位值。
     */
    private fun updateDraft(field: GameConfigFieldDefinition, value: GameConfigPresentationValue) {
        draft.updateField(field, value)
        normalizeDraftFlowConfig()
        host.requestRebuild()
    }

    /** 規則欄位變更後，讓依規則設定決定的共用流程欄位保持一致。 */
    private fun normalizeDraftFlowConfig() {
        val before = draft.config ?: return
        val updated = before.copy(
            flowConfig = configPresentations.normalizeFlowConfig(
                ruleModuleId = configResolver.resolve(before).ruleModuleId,
                ruleConfig = before.ruleConfig,
                current = before.flowConfig,
            ),
        )
        draft.replaceConfig(updated, effectiveFieldIds(updated))
    }

    /**
     * 取得設定實際呈現的欄位，供欄位變更後清理已消失欄位的驗證狀態。
     *
     * @param config 要解析的設定。
     * @return 呈現中的欄位 ID；規則沒有呈現定義時為空。
     */
    private fun effectiveFieldIds(config: GameConfig): Set<String> = configResolver.resolve(config).definition
        ?.let { definition -> definition.fields.mapTo(mutableSetOf()) { it.id } }
        .orEmpty()

    /**
     * 直接輸入整數時保留焦點，只更新草稿、驗證狀態與底部按鈕。
     *
     * @param field 要更新的欄位。
     * @param editor 整數輸入範圍與可空規格。
     * @param raw 原始輸入文字。
     */
    private fun updateNumericDraft(
        field: GameConfigFieldDefinition,
        editor: GameConfigEditorSpec.IntegerInput,
        raw: String,
    ) {
        if (!draft.markNumericInput(field, editor, raw)) {
            refreshDraftButtons()
            return
        }
        val beforeFieldIds = draft.config?.let(::effectiveFieldIds).orEmpty()
        draft.applyNumericInput(field, raw)
        normalizeDraftFlowConfig()
        if (beforeFieldIds != draft.config?.let(::effectiveFieldIds).orEmpty()) {
            host.requestRebuild()
        } else {
            refreshDraftButtons()
        }
    }

    /** 草稿未過期且驗證通過時送出設定更新；不自行判定伺服器是否接受。 */
    private fun applyDraft() {
        if (!draft.canDone(roomPlayerCount())) return
        val table = stateStore.tableOccupancy(tableId) ?: return
        val config = draft.config ?: return
        MahjongChannels.roomAction.sendToServer(json, RoomActionDto.UpdateConfig(table.tableId, config.toDto(networkRegistries)))
    }

    /** 有變更時送出並等待權威設定更新，沒有變更時立即返回成員頁。 */
    private fun finishSettings() {
        val authoritative = host.currentConfig() ?: return
        if ((draft.config ?: authoritative) == authoritative) {
            host.showRoomPage()
            return
        }
        draft.markReturnToRoomAfterApply()
        applyDraft()
    }

    /**
     * 依草稿、權威值與預設值同步底部按鈕及差異提示。
     *
     * @param current 目前草稿。
     * @param authoritative 目前權威設定。
     * @param defaults 用於判斷是否可重設的預設設定。
     */
    private fun refreshDraftButtons(
        current: GameConfig? = draft.config,
        authoritative: GameConfig? = host.currentConfig(),
        defaults: GameConfig? = current?.let { config -> configResolver.resolve(config).definition?.let { defaultConfig(it, config) } },
    ) {
        val roomPlayerCount = roomPlayerCount()
        applyButton?.active = draft.canApply(authoritative, roomPlayerCount)
        undoButton?.active = draft.canUndo(authoritative)
        resetButton?.active = draft.canReset(defaults)
        doneButton?.active = draft.canDone(roomPlayerCount)
        val changes = if (current != null && authoritative != null && current != authoritative && !draft.isStale) {
            Tooltip.create(gameConfigDifferenceText(configResolver, ruleNames, authoritative, current))
        } else {
            null
        }
        val playerLimit = current?.takeIf { draft.exceedsPlayerLimit(roomPlayerCount) }?.let { config ->
            Tooltip.create(playerLimitExceededText(config, roomPlayerCount).copy().withStyle(ChatFormatting.RED))
        }
        applyButton?.tooltip = playerLimit ?: changes
        doneButton?.tooltip = playerLimit
        undoButton?.tooltip = changes ?: Tooltip.create(undoTooltip())
    }

    /**
     * 建立欄位的提示框：說明、選項或數值範圍，以及可操作時的操作提示。
     *
     * @param field 欄位呈現定義。
     * @param config 讀取目前值及相依條件使用的設定。
     * @param editable 是否允許修改此欄位；不可修改時不顯示操作提示。
     * @param locked 目前玩法鎖定這個欄位時的鎖定內容：選項類欄位把其他選項標為不可選並附上原因，其他欄位以提醒
     *   顯示原因。沒有鎖定時為 null。
     * @return 完整的多行欄位提示文字。
     */
    private fun fieldTooltip(field: GameConfigFieldDefinition, config: GameConfig, editable: Boolean, locked: GameConfigLockedField?): Component {
        val description = Component.translatable(field.descriptionTranslationKey)
        val lockReason = locked?.let { Component.translatable(it.reasonTranslationKey) }
        val dependencyNotice = Component.translatable(MinecraftRoomScreenKeys.DISABLED_BY_DEPENDENCY).takeUnless { field.isEnabled(config) }
        val notice = lockReason ?: dependencyNotice
        return when (val editor = field.editor) {
            GameConfigEditorSpec.BooleanToggle -> {
                val enabled = (field.read(config) as GameConfigPresentationValue.BooleanValue).enabled
                ControlTooltip.options(
                    description = description,
                    options = listOf(
                        ControlTooltip.Option(label = booleanText(true), current = enabled, unavailableReason = lockReason.takeUnless { enabled }),
                        ControlTooltip.Option(label = booleanText(false), current = !enabled, unavailableReason = lockReason.takeIf { enabled }),
                    ),
                    hints = if (editable) ControlTooltip.toggleHints() else emptyList(),
                    notice = dependencyNotice,
                )
            }
            is GameConfigEditorSpec.SingleChoice -> {
                val current = (field.read(config) as GameConfigPresentationValue.ChoiceValue).optionId
                ControlTooltip.options(
                    description = description,
                    options = editor.optionIds.map { optionId ->
                        ControlTooltip.Option(
                            label = optionText(optionId, editor),
                            current = optionId == current,
                            unavailableReason = lockReason.takeUnless { optionId == current },
                        )
                    },
                    hints = CycleButtonInput.hintTexts(optionCount = editor.optionIds.size, active = editable),
                    notice = dependencyNotice,
                )
            }
            is GameConfigEditorSpec.MultiChoice -> {
                val selected = (field.read(config) as GameConfigPresentationValue.MultiChoiceValue).optionIds
                ControlTooltip.options(
                    description = description,
                    options = editor.optionIds.map { ControlTooltip.Option(label = optionText(it, editor), current = it in selected) },
                    hints = if (editable) ControlTooltip.chooseHints() else emptyList(),
                    title = Component.translatable(
                        MinecraftControlTooltipKeys.OPTIONS_SELECTED,
                        selected.size,
                        editor.minimumCount,
                        editor.maximumCount,
                    ),
                    notice = notice,
                )
            }
            is GameConfigEditorSpec.IntegerInput -> {
                val minimum = Component.literal(formatRoomInteger(editor.minimum))
                val unsetLabel = Component.translatable(MinecraftRoomScreenKeys.NONE).takeIf { editor.nullable }
                ControlTooltip.range(
                    description = description,
                    range = ControlTooltip.rangeText(
                        minimum = minimum,
                        maximum = Component.literal(formatRoomInteger(editor.maximum)),
                        unsetLabel = unsetLabel,
                    ),
                    hints = if (editable) {
                        ControlTooltip.integerHints(
                            step = Component.literal(formatRoomInteger(editor.step)),
                            shiftStep = Component.literal(formatRoomInteger(editor.step * RoomConfigIntegerControl.SHIFT_STEP_MULTIPLIER)),
                            minimum = minimum,
                            unsetLabel = unsetLabel,
                        )
                    } else {
                        emptyList()
                    },
                    notice = notice,
                )
            }
        }
    }

    /** 房間目前人數（含 AI）；房間資料尚未同步時為 0。 */
    private fun roomPlayerCount(): Int = stateStore.roomSnapshot(tableId)?.playerIds?.size ?: 0

    /**
     * 建立房間人數超過規則上限的說明。
     *
     * @param config 用於取得規則名稱與人數上限的設定。
     * @param roomPlayerCount 目前房間人數。
     * @return 超過人數上限的提示文字。
     */
    private fun playerLimitExceededText(config: GameConfig, roomPlayerCount: Int): Component = Component.translatable(
        MinecraftRoomScreenKeys.PLAYER_LIMIT_EXCEEDED,
        roomPlayerCount,
        ruleName(configResolver.resolve(config).ruleModuleId),
        config.ruleConfig.maxPlayers,
    )

    /** 建立復原按鈕的放棄草稿說明。 */
    private fun undoTooltip(): Component = Component.empty()
        .append(Component.translatable(MinecraftRoomScreenKeys.UNDO).withStyle(ChatFormatting.GOLD))
        .append("\n• ").append(Component.translatable(MinecraftRoomScreenKeys.UNDO_DISCARD).withStyle(ChatFormatting.WHITE))
        .append("\n• ").append(Component.translatable(MinecraftRoomScreenKeys.UNDO_RESTORE).withStyle(ChatFormatting.GRAY))

    /** 建立重設為預設按鈕的說明，提醒尚未儲存。 */
    private fun resetTooltip(): Component = Component.empty()
        .append(Component.translatable(MinecraftRoomScreenKeys.RESET_DEFAULTS_BUTTON).withStyle(ChatFormatting.GOLD))
        .append("\n• ").append(Component.translatable(MinecraftRoomScreenKeys.RESET_DEFAULTS).withStyle(ChatFormatting.WHITE))
        .append("\n• ").append(Component.translatable(MinecraftRoomScreenKeys.RESET_NOT_SAVED).withStyle(ChatFormatting.GRAY))

    /**
     * 依目前視窗尺寸建立設定頁版面。
     *
     * @return 含頁面切換與規則列的設定頁版面。
     */
    private fun layout(): FormScreenLayout = FormScreenLayout(
        windowWidth = host.screen.width,
        windowHeight = host.screen.height,
        hasPageSwitch = true,
        hasRuleRow = true,
        ruleLabelWidth = host.textRenderer.width(ruleLabel()),
        variantLabelWidth = host.textRenderer.width(variantLabel()),
    )

    /**
     * 依目前捲動位置建立分類頁籤列版面。
     *
     * @param layout 設定頁版面。
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

    /** 目前草稿或權威設定解析出的欄位定義；沒有呈現定義時為 null。 */
    private fun resolvedDefinition(): GameConfigPresentationDefinition? = (draft.config ?: host.currentConfig())?.let(configResolver::resolve)?.definition

    /** 目前分類的完整欄位數，供捲軸幾何與捲動上限共用。 */
    private fun currentFieldCount(): Int = resolvedDefinition()?.let(GameConfigVariantResolver::visibleFields)?.count { it.categoryId == selectedCategoryId } ?: 0

    /** 規則選擇器的標籤。 */
    private fun ruleLabel(): Component = Component.translatable(MinecraftRoomScreenKeys.RULE)

    /** 玩法選擇器的標籤。 */
    private fun variantLabel(): Component = Component.translatable(MinecraftRoomScreenKeys.VARIANT_SELECTOR)

    /**
     * 取得規則名稱，未登記時顯示原始識別碼。
     *
     * @param moduleId 規則模組識別碼。
     * @return 本地化名稱或識別碼。
     */
    private fun ruleName(moduleId: String): Component = ruleNames.find(moduleId)?.let(Component::translatable) ?: Component.literal(moduleId)

    /**
     * 取得布林值的本地化文字。
     *
     * @param enabled 是否啟用。
     * @return 對應開啟或關閉的文字。
     */
    private fun booleanText(enabled: Boolean): Component = Component.translatable(if (enabled) MinecraftRoomScreenKeys.TRUE else MinecraftRoomScreenKeys.FALSE)

    /**
     * 取得設定選項的本地化文字。
     *
     * @param optionId 選項識別碼。
     * @param editor 單選或多選欄位編輯器描述。
     * @return 已登記的本地化文字；未登記翻譯鍵時顯示完整選項 ID。
     */
    private fun optionText(optionId: String, editor: GameConfigEditorSpec.ChoiceEditor): Component = editor.translationKeyFor(optionId)?.let(Component::translatable) ?: Component.literal(optionId)

    private companion object {
        /** 規則選擇器展開清單的識別值。 */
        const val RULE_SELECTOR_KEY = "rule"

        /** 玩法選擇器展開清單的識別值。 */
        const val VARIANT_SELECTOR_KEY = "variant"

        /** 規則一覽按鈕上的文字。 */
        const val RULE_CATALOGUE_BUTTON_LABEL = "?"
    }
}
