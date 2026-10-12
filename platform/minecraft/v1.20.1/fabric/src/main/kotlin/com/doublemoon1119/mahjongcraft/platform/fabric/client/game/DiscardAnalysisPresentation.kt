package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HandReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WaitingTileAvailabilityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailabilityIds
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.hudCoordinate
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import net.minecraft.text.Text

/**
 * 捨牌分析面板中的一格等待牌。
 *
 * @property tile 等待的牌種。
 * @property countText 剩餘張數文字。
 * @property countColor 剩餘張數的顏色，張數越少越醒目。
 * @property availabilityText 這張牌專屬的和牌資格文字；整份分析共用同一種資格時為 `null`，改由狀態列呈現。
 */
internal data class DiscardAnalysisCell(
    val tile: TileDto,
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
    /**
     * 捨出指定牌張後的預測分析。
     *
     * @property ruleModuleId 分析所屬規則；未提供時為 null。
     * @property analysis 捨牌後的權威分析。
     */
    data class AfterDiscard(
        val ruleModuleId: String?,
        val analysis: DiscardReadinessAnalysisDto,
    ) : HandAnalysisSelection

    /**
     * 目前手牌的分析。
     *
     * @property analysis 目前手牌的權威分析。
     */
    data class Current(val analysis: HandReadinessAnalysisDto) : HandAnalysisSelection
}

/**
 * 優先選用指向牌張的合法捨牌預測，否則使用目前手牌分析。
 *
 * @param prompt 最新權威決策提示。
 * @param activeActionToken 正在選牌的動作令牌；未選牌時為 null。
 * @param pointedTileId 游標指向的牌張識別碼。
 * @param currentAnalysis 目前手牌的權威分析。
 * @return 可用的分析來源；兩種來源都不存在時為 null。
 */
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
 * 預設的那幾張。[WaitingTileAvailabilityIds.WIN_AVAILABLE] 代表沒有特殊限制，任何情況都不顯示。
 *
 * @param texts 規則用語解析器。
 * @param ruleModuleId 分析所屬規則。
 * @param analysis 捨牌後的權威分析。
 * @return 等待牌格與共用狀態列。
 */
internal fun discardAnalysisContent(
    texts: DecisionTextResolver,
    ruleModuleId: String?,
    analysis: DiscardReadinessAnalysisDto,
): DiscardAnalysisContent = readinessAnalysisContent(texts, ruleModuleId, analysis.waitingTiles, analysis.statusIndicatorId)

/**
 * 由目前手牌的權威分析建立與捨牌預測相同格式的面板內容。
 *
 * @param texts 規則用語解析器。
 * @param analysis 目前手牌的權威分析。
 * @return 等待牌格與共用狀態列。
 */
internal fun handAnalysisContent(
    texts: DecisionTextResolver,
    analysis: HandReadinessAnalysisDto,
): DiscardAnalysisContent = readinessAnalysisContent(
    texts,
    analysis.ruleModuleId,
    analysis.waitingTiles,
    analysis.statusIndicatorId,
)

/**
 * 將等待牌與資格限制組成共用分析面板內容。
 *
 * @param texts 規則用語解析器。
 * @param ruleModuleId 分析所屬規則。
 * @param waitingTiles 等待牌與可用張數。
 * @param statusIndicatorId 整體分析狀態識別碼；沒有狀態時為 null。
 * @return 等待牌格、資格文字與共用狀態列。
 */
private fun readinessAnalysisContent(
    texts: DecisionTextResolver,
    ruleModuleId: String?,
    waitingTiles: List<WaitingTileAvailabilityDto>,
    statusIndicatorId: String?,
): DiscardAnalysisContent {
    val sharedAvailability = waitingTiles.map { it.winAvailability }
        .distinct()
        .singleOrNull()
        ?.takeUnless { it == WaitingTileAvailabilityIds.WIN_AVAILABLE }
    val hasAvailabilityRow = sharedAvailability == null &&
        waitingTiles.any { it.winAvailability != WaitingTileAvailabilityIds.WIN_AVAILABLE }
    return DiscardAnalysisContent(
        statusTexts = listOfNotNull(
            statusIndicatorId?.let { texts.statusText(ruleModuleId, it) },
            sharedAvailability?.let { texts.statusText(ruleModuleId, it) },
        ),
        cells = waitingTiles.map { waiting ->
            DiscardAnalysisCell(
                tile = waiting.tile,
                countText = Text.translatable("mahjongcraft.hud.remaining_tiles", waiting.remainingCount),
                countColor = when (waiting.remainingCount) {
                    0 -> 0xAA4444
                    1 -> 0xFFD54F
                    else -> 0xFFFFFF
                },
                availabilityText = if (hasAvailabilityRow && waiting.winAvailability != WaitingTileAvailabilityIds.WIN_AVAILABLE) {
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
 * @property widestStatusTextWidth 狀態列最寬文字的像素寬度。
 * @property hasAvailabilityRow 格子內是否要保留和牌資格那一行。
 * @property ratioY 玩家設定的垂直位置比例。
 */
internal data class DiscardAnalysisLayout(
    val screenWidth: Int,
    val screenHeight: Int,
    val cellCount: Int,
    val statusLineCount: Int,
    val widestCellContentWidth: Int,
    val widestStatusTextWidth: Int,
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

    /** 格子區寬度。 */
    val gridWidth: Int
        get() = columns * cellWidth

    /** 面板寬度；狀態列比格子區寬時由狀態列決定，文字才不會超出背景。 */
    val panelWidth: Int
        get() = PADDING * 2 + maxOf(gridWidth, widestStatusTextWidth)

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

    /** 格子區左界；狀態列撐寬面板時格子仍維持置中。 */
    val gridLeft: Int
        get() = panelLeft + (panelWidth - gridWidth) / 2

    /**
     * 狀態列第 [index] 行的上緣。
     *
     * @param index 從零開始的狀態行索引。
     * @return 該狀態行的上緣座標。
     */
    fun statusTextTop(index: Int): Int = panelTop + PADDING + index * STATUS_TEXT_HEIGHT

    /** 狀態列與格子之間分隔線的上緣；沒有狀態列時不繪製分隔線。 */
    val dividerTop: Int
        get() = panelTop + PADDING + statusLineCount * STATUS_TEXT_HEIGHT + STATUS_DIVIDER_GAP

    /**
     * 第 [index] 格的牌面版位。
     *
     * @param index 從零開始的等待牌格索引。
     * @return 該格內置中的牌面邊界。
     */
    fun tileBounds(index: Int): DecisionBounds {
        val cellLeft = gridLeft + (index % columns) * cellWidth
        return DecisionBounds(
            x = cellLeft + (cellWidth - TILE_WIDTH) / 2,
            y = panelTop + PADDING + statusHeight + (index / columns) * cellHeight,
            width = TILE_WIDTH,
            height = TILE_HEIGHT,
        )
    }

    /**
     * 第 [index] 格的水平中心。
     *
     * @param index 從零開始的等待牌格索引。
     * @return 該格的水平中心座標。
     */
    fun cellCenterX(index: Int): Int = gridLeft + (index % columns) * cellWidth + cellWidth / 2

    /**
     * 第 [index] 格剩餘張數文字的上緣。
     *
     * @param index 從零開始的等待牌格索引。
     * @return 剩餘張數文字的上緣座標。
     */
    fun countTextTop(index: Int): Int = tileBounds(index).let { it.y + it.height + 1 }

    /**
     * 第 [index] 格和牌資格文字的上緣。
     *
     * @param index 從零開始的等待牌格索引。
     * @return 和牌資格文字的上緣座標。
     */
    fun availabilityTextTop(index: Int): Int = tileBounds(index).let { it.y + it.height + COUNT_HEIGHT }

    internal companion object {
        /** 每列等待牌格數上限。 */
        const val MAX_COLUMNS = 7

        /** 面板內距。 */
        const val PADDING = 6

        /** 相鄰牌格的水平間距。 */
        const val CELL_GAP = 6

        /** 牌面寬度。 */
        const val TILE_WIDTH = 18

        /** 牌面高度。 */
        const val TILE_HEIGHT = 24

        /** 剩餘張數文字保留高度。 */
        const val COUNT_HEIGHT = 11

        /** 和牌資格文字保留高度。 */
        const val AVAILABILITY_HEIGHT = 10

        /** 每行狀態文字的高度。 */
        const val STATUS_TEXT_HEIGHT = 9

        /** 狀態文字與分隔線的間距。 */
        const val STATUS_DIVIDER_GAP = 3

        /** 分隔線與牌格區的間距。 */
        const val STATUS_TILE_GAP = 5
    }
}
