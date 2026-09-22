package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HandReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WIN_AVAILABLE_ID
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WaitingTileAvailabilityDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.hudCoordinate
import net.minecraft.text.Text

/**
 * 捨牌分析面板中的一格等待牌。
 *
 * @property tileAssetKey 牌面資產。
 * @property countText 剩餘張數文字。
 * @property countColor 剩餘張數的顏色，張數越少越醒目。
 * @property availabilityText 這張牌專屬的和牌資格文字；整份分析共用同一種資格時為 `null`，改由狀態列呈現。
 */
internal data class DiscardAnalysisCell(
    val tileAssetKey: String,
    val countText: Text,
    val countColor: Int,
    val availabilityText: Text?,
)

/**
 * 捨牌分析面板的內容。
 *
 * @property statusTexts 面板頂端的狀態列；分析本身的狀態指示與整份分析共用的和牌資格各佔一行。
 * @property cells 每張等待牌一格。
 * @property hasAvailabilityRow 是否有任何一格需要額外一行和牌資格文字，決定格子高度。
 */
internal data class DiscardAnalysisContent(
    val statusTexts: List<Text>,
    val cells: List<DiscardAnalysisCell>,
    val hasAvailabilityRow: Boolean,
)

/** 手牌分析 HUD 目前應採用的權威資料來源。 */
internal sealed interface HandAnalysisSelection {
    data class AfterDiscard(
        val ruleModuleId: String?,
        val analysis: DiscardReadinessAnalysisDto,
    ) : HandAnalysisSelection

    data class Current(val analysis: HandReadinessAnalysisDto) : HandAnalysisSelection
}

/** 合法捨牌預測優先；沒有指向合法候選時退回目前手牌分析。 */
internal fun selectHandAnalysis(
    prompt: PlayerDecisionPromptDto?,
    activeActionToken: String?,
    pointedTileId: String?,
    currentAnalysis: HandReadinessAnalysisDto?,
): HandAnalysisSelection? {
    val afterDiscard = pointedTileId?.let { tileId ->
        prompt?.discardAnalysesForAction(activeActionToken)
            ?.firstOrNull { it.discardTileId == tileId }
    }
    return when {
        afterDiscard != null -> HandAnalysisSelection.AfterDiscard(prompt?.ruleModuleId, afterDiscard)
        currentAnalysis != null -> HandAnalysisSelection.Current(currentAnalysis)
        else -> null
    }
}

/**
 * 由權威分析組出面板內容。
 *
 * 全部等待牌共用同一種非預設和牌資格時，該資格提升到狀態列只講一次；資格不一致時改為逐格標示，只標非
 * 預設的那幾張。[WIN_AVAILABLE_ID] 代表沒有特殊限制，任何情況都不顯示。
 */
internal fun discardAnalysisContent(
    texts: DecisionTextResolver,
    ruleModuleId: String?,
    analysis: DiscardReadinessAnalysisDto,
): DiscardAnalysisContent = readinessAnalysisContent(texts, ruleModuleId, analysis.waitingTiles, analysis.statusIndicatorId)

/** 由目前手牌的權威分析組出與捨牌預測完全相同的面板內容。 */
internal fun handAnalysisContent(
    texts: DecisionTextResolver,
    analysis: HandReadinessAnalysisDto,
): DiscardAnalysisContent = readinessAnalysisContent(
    texts,
    analysis.ruleModuleId,
    analysis.waitingTiles,
    analysis.statusIndicatorId,
)

private fun readinessAnalysisContent(
    texts: DecisionTextResolver,
    ruleModuleId: String?,
    waitingTiles: List<WaitingTileAvailabilityDto>,
    statusIndicatorId: String?,
): DiscardAnalysisContent {
    val sharedAvailability = waitingTiles.map { it.winAvailability }
        .distinct()
        .singleOrNull()
        ?.takeUnless { it == WIN_AVAILABLE_ID }
    val hasAvailabilityRow = sharedAvailability == null &&
        waitingTiles.any { it.winAvailability != WIN_AVAILABLE_ID }
    return DiscardAnalysisContent(
        statusTexts = listOfNotNull(
            statusIndicatorId?.let { texts.statusText(ruleModuleId, it) },
            sharedAvailability?.let { texts.statusText(ruleModuleId, it) },
        ),
        cells = waitingTiles.map { waiting ->
            DiscardAnalysisCell(
                tileAssetKey = waiting.tileAssetKey,
                countText = Text.translatable("mahjongcraft.hud.remaining_tiles", waiting.remainingCount),
                countColor = when (waiting.remainingCount) {
                    0 -> 0xAA4444
                    1 -> 0xFFD54F
                    else -> 0xFFFFFF
                },
                availabilityText = if (hasAvailabilityRow && waiting.winAvailability != WIN_AVAILABLE_ID) {
                    texts.statusText(ruleModuleId, waiting.winAvailability)
                } else {
                    null
                },
            )
        },
        hasAvailabilityRow = hasAvailabilityRow,
    )
}

/**
 * 捨牌分析面板的版面幾何。
 *
 * 面板水平置中，垂直位置由玩家在 HUD 編輯器調整的比例決定。欄寬依實際文字寬度動態計算，避免不同語系下的
 * 剩餘張數與和牌資格文字互相碰撞，因此 [widestCellContentWidth] 由呼叫端量測後傳入。
 *
 * @property screenWidth 目前 GUI scaled 畫面寬度。
 * @property screenHeight 目前 GUI scaled 畫面高度。
 * @property cellCount 等待牌格數。
 * @property statusLineCount 狀態列行數。
 * @property widestCellContentWidth 單格內最寬的內容寬度，已包含牌面本身的寬度。
 * @property hasAvailabilityRow 格子內是否要保留和牌資格那一行。
 * @property ratioY 玩家設定的垂直位置比例。
 */
internal data class DiscardAnalysisLayout(
    val screenWidth: Int,
    val screenHeight: Int,
    val cellCount: Int,
    val statusLineCount: Int,
    val widestCellContentWidth: Int,
    val hasAvailabilityRow: Boolean,
    val ratioY: Double,
) {
    /** 每列格數。 */
    val columns: Int
        get() = minOf(MAX_COLUMNS, cellCount.coerceAtLeast(1))

    /** 列數。 */
    val rowCount: Int
        get() = (cellCount + columns - 1) / columns

    /** 單格寬度。 */
    val cellWidth: Int
        get() = widestCellContentWidth + CELL_GAP

    /** 單格高度。 */
    val cellHeight: Int
        get() = TILE_HEIGHT + COUNT_HEIGHT + if (hasAvailabilityRow) AVAILABILITY_HEIGHT else 0

    /** 狀態列連同分隔線與其下方留白所佔的高度。 */
    val statusHeight: Int
        get() = if (statusLineCount == 0) 0 else statusLineCount * STATUS_TEXT_HEIGHT + STATUS_DIVIDER_GAP + 1 + STATUS_TILE_GAP

    /** 面板寬度。 */
    val panelWidth: Int
        get() = PADDING * 2 + columns * cellWidth

    /** 面板高度。 */
    val panelHeight: Int
        get() = PADDING * 2 + statusHeight + rowCount * cellHeight

    /** 面板左界。 */
    val panelLeft: Int
        get() = (screenWidth - panelWidth) / 2

    /** 面板上界。 */
    val panelTop: Int
        get() = hudCoordinate(ratioY, screenHeight, panelHeight)

    /** 面板水平中心，狀態列與每格的文字都以此或以格中心置中。 */
    val panelCenterX: Int
        get() = panelLeft + panelWidth / 2

    /** 狀態列第 [index] 行的上緣。 */
    fun statusTextTop(index: Int): Int = panelTop + PADDING + index * STATUS_TEXT_HEIGHT

    /** 狀態列與格子之間分隔線的上緣；沒有狀態列時不繪製分隔線。 */
    val dividerTop: Int
        get() = panelTop + PADDING + statusLineCount * STATUS_TEXT_HEIGHT + STATUS_DIVIDER_GAP

    /** 第 [index] 格的牌面版位。 */
    fun tileBounds(index: Int): DecisionBounds {
        val cellLeft = panelLeft + PADDING + (index % columns) * cellWidth
        return DecisionBounds(
            x = cellLeft + (cellWidth - TILE_WIDTH) / 2,
            y = panelTop + PADDING + statusHeight + (index / columns) * cellHeight,
            width = TILE_WIDTH,
            height = TILE_HEIGHT,
        )
    }

    /** 第 [index] 格的水平中心。 */
    fun cellCenterX(index: Int): Int = panelLeft + PADDING + (index % columns) * cellWidth + cellWidth / 2

    /** 第 [index] 格剩餘張數文字的上緣。 */
    fun countTextTop(index: Int): Int = tileBounds(index).let { it.y + it.height + 1 }

    /** 第 [index] 格和牌資格文字的上緣。 */
    fun availabilityTextTop(index: Int): Int = tileBounds(index).let { it.y + it.height + COUNT_HEIGHT }

    internal companion object {
        const val MAX_COLUMNS = 7
        const val PADDING = 6
        const val CELL_GAP = 6
        const val TILE_WIDTH = 18
        const val TILE_HEIGHT = 24
        const val COUNT_HEIGHT = 11
        const val AVAILABILITY_HEIGHT = 10
        const val STATUS_TEXT_HEIGHT = 9
        const val STATUS_DIVIDER_GAP = 3
        const val STATUS_TILE_GAP = 5
    }
}
