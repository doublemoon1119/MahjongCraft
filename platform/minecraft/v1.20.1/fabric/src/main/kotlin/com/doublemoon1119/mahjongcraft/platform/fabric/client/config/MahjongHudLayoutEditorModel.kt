package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.DecisionCard
import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.DecisionCardLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionPlayerRelationDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionActionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 可拖曳的 HUD 配置區塊。
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

    /** 手牌分析；寬度隨分析內容變動，固定水平置中。 */
    ANALYSIS(
        translationKey = MinecraftClientConfigScreenKeys.HUD_LAYOUT_DISCARD_ANALYSIS,
        adjustsHorizontally = false,
    ),

    /** 自動操作狀態；整組共用一個位置，兩軸皆可調整。 */
    AUTOMATIC_CONTROL(
        translationKey = MinecraftClientConfigScreenKeys.HUD_LAYOUT_AUTOMATIC_CONTROL_STATUS,
        adjustsHorizontally = true,
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
 * 操作面板可切換的代表性預覽情境。
 *
 * 每個情境是一份與正式對局相同格式的範例決策提示；編輯器以它畫出真實的操作面板，預覽尺寸也由實際操作面板的版面
 * （[DecisionCardLayout]）計算，因此編輯器中的位置與遊戲中面板的位置一致。
 *
 * @property translationKey 情境名稱翻譯鍵。
 * @property prompt 情境的範例決策提示。
 */
internal enum class HudPreviewScenario(
    val translationKey: String,
    val prompt: PlayerDecisionPromptDto,
) {
    /** 一般鳴牌：上家打出五條，可以碰或兩種吃。 */
    CALL(
        translationKey = MinecraftClientConfigScreenKeys.HUD_LAYOUT_SCENARIO_CALL,
        prompt = previewPrompt(
            triggerTileAssetKey = "s5",
            fromLeftPlayer = true,
            actions = listOf(
                previewAction(BuiltInGameActionIds.PON, listOf("s5", "s5", "s5")),
                previewAction(BuiltInGameActionIds.CHI, listOf("s4", "s5", "s6"), claimedTileIndex = 1),
                previewAction(BuiltInGameActionIds.CHI, listOf("s3", "s4", "s5"), claimedTileIndex = 2),
                previewAction(BuiltInGameActionIds.PASS, emptyList()),
            ),
        ),
    ),

    /** 立直宣告：自己摸到一萬，可以立直、暗槓或自摸。 */
    RIICHI(
        translationKey = MinecraftClientConfigScreenKeys.HUD_LAYOUT_SCENARIO_RIICHI,
        prompt = previewPrompt(
            triggerTileAssetKey = "m1",
            fromLeftPlayer = false,
            actions = listOf(
                previewAction(RiichiGameAction.Riichi.id, RIICHI_PREVIEW_TILES),
                previewAction(BuiltInGameActionIds.KAN_CLOSED, listOf("m9", "m9", "m9", "m9")),
                previewAction(BuiltInGameActionIds.TSUMO, listOf("m1")),
            ),
        ),
    ),

    /** 九種九牌等長牌列操作：自己摸到東，可以宣告九種九牌或立直。 */
    ABORTIVE_DRAW(
        translationKey = MinecraftClientConfigScreenKeys.HUD_LAYOUT_SCENARIO_ABORTIVE_DRAW,
        prompt = previewPrompt(
            triggerTileAssetKey = "east",
            fromLeftPlayer = false,
            actions = listOf(
                previewAction(
                    RiichiExhaustiveDrawReason.KyuushuKyuuhai.id,
                    listOf("m1", "m9", "p1", "p9", "s1", "s9", "east", "south", "west", "north", "white_dragon", "green_dragon"),
                ),
                previewAction(RiichiGameAction.Riichi.id, RIICHI_PREVIEW_TILES),
            ),
        ),
    ),
    ;

    /**
     * 這個情境在 [screenWidth] × [screenHeight] 畫面上的操作面板版面；不含標題文字寬度與倒數寬度。
     *
     * 編輯器量測到實際文字後的尺寸優先，這裡只在尚未量測時提供預覽尺寸。
     */
    fun layout(screenWidth: Int, screenHeight: Int): DecisionCardLayout = DecisionCardLayout(
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        cards = prompt.actions
            .filterNot { it.actionId == BuiltInGameActionIds.PASS }
            .map { DecisionCard(previewTileCount = it.previewTileAssetKeys.size, hasClaimedTileMarker = it.claimedTileIndex != null) },
        headerTextWidth = 0,
        triggerLineCount = 1,
        triggerTextWidth = 0,
        hasTriggerTile = true,
        panelRatioY = 0.0,
        timerWidth = 0,
    )
}

/** 立直卡片的範例預覽牌：可以打出宣告立直的牌。 */
private val RIICHI_PREVIEW_TILES: List<String> = listOf("m1", "m4", "m7", "p2", "p5", "p8", "s3", "s6", "s9")

/** 預覽情境的範例決策提示；來自上家捨牌時附上來源玩家，否則是自己摸到 [triggerTileAssetKey]。 */
private fun previewPrompt(
    triggerTileAssetKey: String,
    fromLeftPlayer: Boolean,
    actions: List<PlayerDecisionActionDto>,
): PlayerDecisionPromptDto = PlayerDecisionPromptDto(
    decisionKey = "hud_layout_preview",
    ruleModuleId = BuiltInRuleModuleIds.RIICHI,
    actions = actions,
    triggerTileAssetKey = triggerTileAssetKey,
    triggerPlayerId = if (fromLeftPlayer) PREVIEW_TRIGGER_PLAYER_ID else null,
    triggerPlayerName = if (fromLeftPlayer) PREVIEW_TRIGGER_PLAYER_NAME else null,
    triggerPlayerRelation = if (fromLeftPlayer) DecisionPlayerRelationDto.LEFT else null,
    triggerActionId = if (fromLeftPlayer) BuiltInGameActionIds.DISCARD else null,
    preparation = null,
    discardAnalyses = emptyList(),
)

/** 預覽情境的一張動作卡。 */
private fun previewAction(
    actionId: String,
    previewTileAssetKeys: List<String>,
    claimedTileIndex: Int? = null,
): PlayerDecisionActionDto = PlayerDecisionActionDto(
    token = actionId,
    actionId = actionId,
    referenceTileAssetKey = null,
    previewTileAssetKeys = previewTileAssetKeys,
    claimedTileIndex = claimedTileIndex,
    tileSelection = null,
)

/** 鳴牌情境中打出觸發牌的範例玩家。 */
private const val PREVIEW_TRIGGER_PLAYER_ID: String = "00000000-0000-0000-0000-000000000000"
private const val PREVIEW_TRIGGER_PLAYER_NAME: String = "Alex"

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
 * @property measuredSizes 各 HUD 以範例內容在目前畫面上量到的實際尺寸；量測需要文字寬高，由編輯器畫面在每次
 * 繪製時寫入，尚未量測的 HUD 退回內建預設尺寸。
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
    val measuredSizes: Map<HudElement, MahjongHudPreviewSize> = emptyMap(),
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
        val (preferredWidth, preferredHeight) = measuredSizes[element]?.let { it.width to it.height } ?: when (element) {
            HudElement.DECISION -> scenario.layout(screenWidth, screenHeight).let { it.panelWidth to it.groupHeight }
            HudElement.COMPACT -> COMPACT_PREVIEW_WIDTH to COMPACT_PREVIEW_HEIGHT
            HudElement.ANALYSIS -> ANALYSIS_PREVIEW_WIDTH to ANALYSIS_PREVIEW_HEIGHT
            HudElement.AUTOMATIC_CONTROL -> AUTOMATIC_CONTROL_PREVIEW_WIDTH to AUTOMATIC_CONTROL_PREVIEW_HEIGHT
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
            HudElement.AUTOMATIC_CONTROL -> draft.copy(
                automaticControlStatusX = horizontal,
                automaticControlStatusY = vertical,
            )
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

    /** 切換操作面板的預覽情境；舊情境量到的操作面板尺寸隨之失效，等待重新量測。 */
    fun selectScenario(scenario: HudPreviewScenario): MahjongHudLayoutEditorModel = copy(
        scenario = scenario,
        measuredSizes = measuredSizes - HudElement.DECISION,
    )

    /** 記錄 [element] 以範例內容量到的實際尺寸，讓預覽框與實際 HUD 一樣大。 */
    fun withMeasuredSize(element: HudElement, size: MahjongHudPreviewSize): MahjongHudLayoutEditorModel = copy(measuredSizes = measuredSizes + (element to size))

    /** 切換未選取 HUD 的預覽方式。 */
    fun selectVisibility(visibility: HudPreviewVisibility): MahjongHudLayoutEditorModel = copy(otherHudVisibility = visibility)

    /** 設定控制項是否被玩家手動隱藏。 */
    fun withControlsHidden(hidden: Boolean): MahjongHudLayoutEditorModel = copy(controlsManuallyHidden = hidden)

    /** 取得指定區塊目前的水平位置比例；不可水平調整的區塊回傳置中比例。 */
    private fun horizontalRatio(element: HudElement): Double = when (element) {
        HudElement.COMPACT -> draft.compactPromptX
        HudElement.AUTOMATIC_CONTROL -> draft.automaticControlStatusX
        else -> CENTER_RATIO
    }

    /** 取得指定區塊目前的垂直位置比例。 */
    private fun verticalRatio(element: HudElement): Double = when (element) {
        HudElement.DECISION -> draft.decisionPanelY
        HudElement.COMPACT -> draft.compactPromptY
        HudElement.ANALYSIS -> draft.discardAnalysisY
        HudElement.AUTOMATIC_CONTROL -> draft.automaticControlStatusY
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

        /** 手牌分析的預覽寬度。 */
        internal const val ANALYSIS_PREVIEW_WIDTH = 220

        /** 手牌分析的預覽高度。 */
        internal const val ANALYSIS_PREVIEW_HEIGHT = 72

        /** 尚未量測到文字寬高時，自動操作狀態的預覽寬度。 */
        internal const val AUTOMATIC_CONTROL_PREVIEW_WIDTH = 96

        /** 尚未量測到文字寬高時，自動操作狀態的預覽高度。 */
        internal const val AUTOMATIC_CONTROL_PREVIEW_HEIGHT = 44
    }
}

/** 靠近畫面中線時吸附至正中央，避免玩家難以手動對齊。 */
internal fun snap(value: Double): Double = if (abs(value - MahjongHudLayoutEditorModel.CENTER_RATIO) <= MahjongHudLayoutEditorModel.SNAP_THRESHOLD) {
    MahjongHudLayoutEditorModel.CENTER_RATIO
} else {
    value
}
