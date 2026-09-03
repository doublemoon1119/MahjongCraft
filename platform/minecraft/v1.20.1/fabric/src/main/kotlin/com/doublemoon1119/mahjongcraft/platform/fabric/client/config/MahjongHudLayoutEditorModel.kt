package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 可拖曳的三個 HUD 配置區塊。
 *
 * @property translationKey 區塊名稱翻譯鍵。
 * @property adjustsHorizontally 這個區塊是否可調整水平位置；固定水平置中的動態寬度面板為 `false`，
 * 拖曳時只更新垂直比例。
 */
internal enum class HudElement(
    val translationKey: String,
    val adjustsHorizontally: Boolean,
) {
    /** 操作面板；寬度隨候選動作數量變動，固定水平置中。 */
    DECISION(
        translationKey = MinecraftClientConfigScreenKeys.HUD_LAYOUT_DECISION_PANEL,
        adjustsHorizontally = false,
    ),

    /** 一般倒數與等待提醒；兩軸皆可調整。 */
    COMPACT(
        translationKey = MinecraftClientConfigScreenKeys.HUD_LAYOUT_COMPACT_PROMPT,
        adjustsHorizontally = true,
    ),

    /** 打牌分析；寬度隨分析內容變動，固定水平置中。 */
    ANALYSIS(
        translationKey = MinecraftClientConfigScreenKeys.HUD_LAYOUT_DISCARD_ANALYSIS,
        adjustsHorizontally = false,
    ),
}

/**
 * 非作用中 HUD 的兩種可見性。
 *
 * @property translationKey 選項名稱翻譯鍵。
 */
internal enum class HudPreviewVisibility(val translationKey: String) {
    /** 只顯示外框與名稱。 */
    OUTLINE(MinecraftClientConfigScreenKeys.HUD_LAYOUT_VISIBILITY_OUTLINE),

    /** 完全隱藏。 */
    HIDDEN(MinecraftClientConfigScreenKeys.HUD_LAYOUT_VISIBILITY_HIDDEN),
}

/**
 * 操作面板可切換的代表性動態尺寸情境。
 *
 * @property translationKey 情境名稱翻譯鍵。
 * @property width 預覽寬度。
 * @property height 預覽高度。
 */
internal enum class HudPreviewScenario(
    val translationKey: String,
    val width: Int,
    val height: Int,
) {
    /** 一般鳴牌。 */
    CALL(
        translationKey = MinecraftClientConfigScreenKeys.HUD_LAYOUT_SCENARIO_CALL,
        width = 300,
        height = 104,
    ),

    /** 立直宣告。 */
    RIICHI(
        translationKey = MinecraftClientConfigScreenKeys.HUD_LAYOUT_SCENARIO_RIICHI,
        width = 380,
        height = 112,
    ),

    /** 九種九牌等長牌列操作。 */
    ABORTIVE_DRAW(
        translationKey = MinecraftClientConfigScreenKeys.HUD_LAYOUT_SCENARIO_ABORTIVE_DRAW,
        width = 440,
        height = 124,
    ),
}

/**
 * 頂部工具列的三種下拉選單。
 *
 * @property translationKey 選單按鈕翻譯鍵。
 */
internal enum class HudDropdownKind(val translationKey: String) {
    /** 目前編輯的 HUD。 */
    HUD(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_HUD),

    /** 目前 HUD 的預覽方式。 */
    VISIBILITY(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_VISIBILITY),

    /** 操作面板的代表性內容情境。 */
    SCENARIO(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_SCENARIO),
}

/**
 * HUD 預覽框在目前畫面尺寸下的實際像素尺寸。
 *
 * @property width 寬度。
 * @property height 高度。
 */
internal data class MahjongHudPreviewSize(
    val width: Int,
    val height: Int,
)

/**
 * HUD 位置編輯器的全部可測試狀態與狀態轉換，與 `Screen` 的繪製及原版 widget 完全分離——編輯器本身
 * 只負責把滑鼠事件轉成這裡的呼叫，再依回傳的新狀態重繪。
 *
 * 所有轉換都回傳新的實例而不就地修改，確保「拖曳 → 放開 → 復原」這類序列可以直接以值比較驗證。
 *
 * @property baseline 最近一次成功套用的配置。
 * @property draft 目前編輯中的配置草稿。
 * @property selectedElement 目前取得完整預覽與拖曳焦點的 HUD。
 * @property scenario 操作面板目前使用的尺寸預覽情境。
 * @property otherHudVisibility 所有未選取 HUD 共用的預覽方式。
 * @property controlsManuallyHidden 玩家是否手動隱藏所有編輯器控制項。
 * @property dragging 目前被拖曳的 HUD 區塊；`null` 代表沒有拖曳進行中。
 * @property dragOffsetX 拖曳起點相對 HUD 左上角的 X。
 * @property dragOffsetY 拖曳起點相對 HUD 左上角的 Y。
 */
internal data class MahjongHudLayoutEditorModel(
    val baseline: MahjongHudLayoutConfig,
    val draft: MahjongHudLayoutConfig = baseline,
    val selectedElement: HudElement = HudElement.DECISION,
    val scenario: HudPreviewScenario = HudPreviewScenario.CALL,
    val otherHudVisibility: HudPreviewVisibility = HudPreviewVisibility.HIDDEN,
    val controlsManuallyHidden: Boolean = false,
    val dragging: HudElement? = null,
    val dragOffsetX: Double = 0.0,
    val dragOffsetY: Double = 0.0,
) {
    /** 草稿與最近一次套用的配置是否不同，決定套用與復原按鈕是否可用。 */
    val hasUnsavedChanges: Boolean
        get() = draft != baseline

    /** 草稿是否仍是預設配置，決定重設按鈕是否可用。 */
    val isDefault: Boolean
        get() = draft == MahjongHudLayoutConfig()

    /** 只有未手動隱藏且未拖曳時顯示編輯器控制項。 */
    val controlsVisible: Boolean
        get() = !controlsManuallyHidden && dragging == null

    /** 依畫面大小限制單一預覽框尺寸，確保極小解析度下仍留有可見邊界。 */
    fun previewSize(
        element: HudElement,
        screenWidth: Int,
        screenHeight: Int,
    ): MahjongHudPreviewSize {
        val (preferredWidth, preferredHeight) = when (element) {
            HudElement.DECISION -> scenario.width to scenario.height
            HudElement.COMPACT -> COMPACT_PREVIEW_WIDTH to COMPACT_PREVIEW_HEIGHT
            HudElement.ANALYSIS -> ANALYSIS_PREVIEW_WIDTH to ANALYSIS_PREVIEW_HEIGHT
        }
        return MahjongHudPreviewSize(
            width = minOf(preferredWidth, screenWidth - PREVIEW_SCREEN_MARGIN).coerceAtLeast(1),
            height = minOf(preferredHeight, screenHeight - PREVIEW_SCREEN_MARGIN).coerceAtLeast(1),
        )
    }

    /** 取得一個預覽框目前的完整 bounds；不可調整水平位置的區塊固定水平置中。 */
    fun bounds(
        element: HudElement,
        screenWidth: Int,
        screenHeight: Int,
    ): MahjongHudBounds {
        val size = previewSize(
            element = element,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        val left = if (element.adjustsHorizontally) {
            hudCoordinate(
                ratio = horizontalRatio(element),
                screenSize = screenWidth,
                elementSize = size.width,
            )
        } else {
            (screenWidth - size.width) / 2
        }
        return MahjongHudBounds(
            left = left,
            top = hudCoordinate(
                ratio = verticalRatio(element),
                screenSize = screenHeight,
                elementSize = size.height,
            ),
            width = size.width,
            height = size.height,
        )
    }

    /**
     * 找出畫面座標命中的 HUD 區塊；目前選取的區塊優先於其他區塊，而其他區塊只在未被隱藏時可命中。
     *
     * @return 命中的區塊；沒有命中任何區塊時為 `null`。
     */
    fun hitTest(
        mouseX: Double,
        mouseY: Double,
        screenWidth: Int,
        screenHeight: Int,
    ): HudElement? {
        val candidates = buildList {
            add(selectedElement)
            if (otherHudVisibility != HudPreviewVisibility.HIDDEN) {
                HudElement.entries.filterTo(this) { it != selectedElement }
            }
        }
        return candidates.firstOrNull {
            bounds(
                element = it,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            ).contains(mouseX, mouseY)
        }
    }

    /** 開始拖曳指定區塊，並記錄游標相對該區塊左上角的位移，使拖曳不會瞬間跳動。 */
    fun beginDrag(
        element: HudElement,
        mouseX: Double,
        mouseY: Double,
        screenWidth: Int,
        screenHeight: Int,
    ): MahjongHudLayoutEditorModel {
        val bounds = bounds(
            element = element,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        return copy(
            selectedElement = element,
            dragging = element,
            dragOffsetX = mouseX - bounds.left,
            dragOffsetY = mouseY - bounds.top,
        )
    }

    /**
     * 依目前游標位置更新草稿；只寫入該區塊允許調整的軸，且比例本身即受
     * [hudRatio] 限制，因此 bounds 永遠不會超出畫面。沒有拖曳進行中時原樣回傳。
     */
    fun dragTo(
        mouseX: Double,
        mouseY: Double,
        screenWidth: Int,
        screenHeight: Int,
    ): MahjongHudLayoutEditorModel {
        val element = dragging ?: return this
        val size = previewSize(
            element = element,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        val horizontal = snap(
            hudRatio(
                coordinate = (mouseX - dragOffsetX).roundToInt(),
                screenSize = screenWidth,
                elementSize = size.width,
            ),
        )
        val vertical = snap(
            hudRatio(
                coordinate = (mouseY - dragOffsetY).roundToInt(),
                screenSize = screenHeight,
                elementSize = size.height,
            ),
        )
        val updated = when (element) {
            HudElement.DECISION -> draft.copy(decisionPanelY = vertical)
            HudElement.COMPACT -> draft.copy(compactPromptX = horizontal, compactPromptY = vertical)
            HudElement.ANALYSIS -> draft.copy(discardAnalysisY = vertical)
        }
        return copy(draft = updated)
    }

    /** 結束拖曳，恢復控制項顯示。 */
    fun endDrag(): MahjongHudLayoutEditorModel = copy(dragging = null)

    /** 將草稿重設為預設配置。 */
    fun reset(): MahjongHudLayoutEditorModel = copy(draft = MahjongHudLayoutConfig())

    /** 將草稿還原為最近一次成功套用的配置。 */
    fun undo(): MahjongHudLayoutEditorModel = copy(draft = baseline)

    /** 記錄草稿已成功保存，成為新的比較基準。 */
    fun markApplied(): MahjongHudLayoutEditorModel = copy(baseline = draft)

    /** 選取要編輯的 HUD 區塊。 */
    fun selectElement(element: HudElement): MahjongHudLayoutEditorModel = copy(selectedElement = element)

    /** 切換操作面板的尺寸預覽情境。 */
    fun selectScenario(scenario: HudPreviewScenario): MahjongHudLayoutEditorModel = copy(scenario = scenario)

    /** 切換未選取 HUD 的預覽方式。 */
    fun selectVisibility(visibility: HudPreviewVisibility): MahjongHudLayoutEditorModel = copy(otherHudVisibility = visibility)

    /** 設定控制項是否被玩家手動隱藏。 */
    fun withControlsHidden(hidden: Boolean): MahjongHudLayoutEditorModel = copy(controlsManuallyHidden = hidden)

    /** 取得指定區塊目前的水平位置比例；不可水平調整的區塊回傳置中比例。 */
    private fun horizontalRatio(element: HudElement): Double = when (element) {
        HudElement.COMPACT -> draft.compactPromptX
        else -> CENTER_RATIO
    }

    /** 取得指定區塊目前的垂直位置比例。 */
    private fun verticalRatio(element: HudElement): Double = when (element) {
        HudElement.DECISION -> draft.decisionPanelY
        HudElement.COMPACT -> draft.compactPromptY
        HudElement.ANALYSIS -> draft.discardAnalysisY
    }

    /** 編輯器的吸附與預覽尺寸常數。 */
    internal companion object {
        /** 中線吸附比例範圍。 */
        internal const val SNAP_THRESHOLD = 0.015

        /** 畫面中線比例。 */
        internal const val CENTER_RATIO = 0.5

        /** 預覽框與畫面邊界之間至少保留的總留白。 */
        internal const val PREVIEW_SCREEN_MARGIN = 16

        /** 一般倒數與等待提醒的預覽寬度。 */
        internal const val COMPACT_PREVIEW_WIDTH = 190

        /** 一般倒數與等待提醒的預覽高度。 */
        internal const val COMPACT_PREVIEW_HEIGHT = 46

        /** 打牌分析的預覽寬度。 */
        internal const val ANALYSIS_PREVIEW_WIDTH = 220

        /** 打牌分析的預覽高度。 */
        internal const val ANALYSIS_PREVIEW_HEIGHT = 72
    }
}

/** 靠近畫面中線時吸附至正中央，避免玩家難以手動對齊。 */
internal fun snap(value: Double): Double = if (abs(value - MahjongHudLayoutEditorModel.CENTER_RATIO) <= MahjongHudLayoutEditorModel.SNAP_THRESHOLD) {
    MahjongHudLayoutEditorModel.CENTER_RATIO
} else {
    value
}
