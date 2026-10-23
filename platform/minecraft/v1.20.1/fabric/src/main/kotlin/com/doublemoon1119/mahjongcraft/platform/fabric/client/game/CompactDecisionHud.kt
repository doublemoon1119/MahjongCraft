package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.hudCoordinate
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import net.minecraft.text.Text

/** Prompt 是否包含需要玩家明確選擇的內容。 */
internal val PlayerDecisionPromptDto.isInteractive: Boolean
    get() = actions.isNotEmpty() || ruleDecision != null

/**
 * 取得補充說明可使用的整行寬度，不以 HUD 的標準寬度提早切行。
 *
 * @param screenWidth GUI 縮放後的畫面寬度。
 * @return 扣除左右邊距後的寬度，至少為一像素。
 */
internal fun compactDescriptionMaximumWidth(screenWidth: Int): Int = (screenWidth - CompactDecisionHudLayout.TEXT_MARGIN * 2).coerceAtLeast(1)

/** 精簡 HUD 在倒數上方要顯示的內容。 */
internal sealed interface CompactDecisionHudContent {
    /** 只有倒數。 */
    data object TimerOnly : CompactDecisionHudContent

    /**
     * 進行中選牌的進度提示。
     *
     * @property progress 已選張數與可選範圍。
     * @property description 選牌所屬動作的說明；規則選擇或沒有說明的動作為 `null`。
     */
    data class TileSelection(
        val progress: DecisionTileSelectionState.Progress,
        val description: Text? = null,
    ) : CompactDecisionHudContent

    /** 操作介面已被 Esc 收起、可以重新開啟的提醒。 */
    data object ReopenReminder : CompactDecisionHudContent
}

/**
 * 決定精簡 HUD 要顯示什麼。
 *
 * 進行中的選牌進度優先；沒有選牌但玩家把仍然有效的互動式操作介面收起來時顯示重開提醒。玩家已明確進入
 * 任何一種實體牌操作模式（[isPhysicalSelectionActive]）就不再提醒重開——那是刻意收起畫面去點實體牌，
 * 不是忘記。
 *
 * @param prompt 目前權威決策提示；沒有提示時為 null。
 * @param dismissedDecisionKey 玩家已收起的操作介面所屬決策鍵。
 * @param isPhysicalSelectionActive 是否正在透過實體牌選牌。
 * @param tileSelectionProgress 進行中的選牌進度；未選牌時為 null。
 * @param tileSelectionDescription 選牌動作的補充說明。
 * @return 選牌進度、重開提醒或僅顯示倒數。
 */
internal fun compactDecisionHudContent(
    prompt: PlayerDecisionPromptDto?,
    dismissedDecisionKey: String?,
    isPhysicalSelectionActive: Boolean,
    tileSelectionProgress: DecisionTileSelectionState.Progress?,
    tileSelectionDescription: Text? = null,
): CompactDecisionHudContent = when {
    tileSelectionProgress != null -> CompactDecisionHudContent.TileSelection(tileSelectionProgress, tileSelectionDescription)
    prompt != null &&
        dismissedDecisionKey == prompt.decisionKey &&
        prompt.isInteractive &&
        !isPhysicalSelectionActive -> CompactDecisionHudContent.ReopenReminder
    else -> CompactDecisionHudContent.TimerOnly
}

/**
 * 選牌進度的細節文字。
 *
 * 還沒選到 `minCount` 張時提示還缺幾張，落在合法範圍內（含剛好選滿 `maxCount`）時提示可以確認——選滿
 * 本身已經隱含「已達上限」，不需要額外的一次性提醒。
 *
 * @param progress 已選張數與可選範圍。
 * @return 尚缺張數或可確認選牌的本地化提示。
 */
internal fun tileSelectionDetailText(progress: DecisionTileSelectionState.Progress): Text = if (progress.selectedCount < progress.validRange.first) {
    Text.translatable(
        "mahjongcraft.hud.tile_selection_need_more",
        progress.validRange.first - progress.selectedCount,
        progress.selectedCount,
        progress.validRange.last,
    )
} else {
    Text.translatable("mahjongcraft.hud.tile_selection_ready", progress.selectedCount, progress.validRange.last)
}

/**
 * 精簡 HUD 的版位。
 *
 * 倒數固定貼齊群組下緣，提示文字往上長：標題在最上方，接著是說明，最後是細節文字。群組整體的位置由玩家在
 * HUD 編輯器調整的比例決定。
 *
 * @property screenWidth 目前 GUI scaled 畫面寬度。
 * @property screenHeight 目前 GUI scaled 畫面高度。
 * @property ratioX 玩家設定的水平位置比例。
 * @property ratioY 玩家設定的垂直位置比例。
 * @property expanded 是否要為倒數上方的提示文字保留空間。
 * @property descriptionLineCount 標題與細節文字之間的說明行數；只在 [expanded] 時保留空間。
 * @property contentWidth 文字量測後所需的群組寬度；預設使用標準寬度。
 */
internal data class CompactDecisionHudLayout(
    val screenWidth: Int,
    val screenHeight: Int,
    val ratioX: Double,
    val ratioY: Double,
    val expanded: Boolean,
    val descriptionLineCount: Int = 0,
    val contentWidth: Int = GROUP_WIDTH,
) {
    /** 群組依文字所需寬度擴張，但不超出畫面。 */
    val groupWidth: Int
        get() = maxOf(GROUP_WIDTH, contentWidth).coerceAtMost(screenWidth)

    /** 群組高度。 */
    val groupHeight: Int
        get() = if (expanded) EXPANDED_HEIGHT + descriptionLineCount * TEXT_LINE_HEIGHT else TIMER_HEIGHT

    /** 群組左界。 */
    val groupLeft: Int
        get() = hudCoordinate(ratioX, screenWidth, groupWidth)

    /** 群組上界，同時也是第一行提示文字的上緣。 */
    val groupTop: Int
        get() = hudCoordinate(ratioY, screenHeight, groupHeight)

    /** 群組水平中心，所有文字都以此置中。 */
    val centerX: Int
        get() = groupLeft + groupWidth / 2

    /** 倒數的上緣。 */
    val timerTop: Int
        get() = groupTop + groupHeight - TIMER_HEIGHT

    /**
     * 計算緊接在標題下方的說明行上緣。
     *
     * @param index 從零開始的說明行索引。
     * @return 該行的上緣座標。
     */
    fun descriptionLineTop(index: Int): Int = groupTop + TEXT_LINE_HEIGHT * (1 + index)

    /** 細節文字的上緣，在所有說明行之後。 */
    val detailTextTop: Int
        get() = groupTop + TEXT_LINE_HEIGHT * (1 + descriptionLineCount)

    internal companion object {
        /** 精簡 HUD 群組的標準寬度。 */
        const val GROUP_WIDTH = 220

        /** 文字左右各自保留的畫面邊距。 */
        const val TEXT_MARGIN = 8

        /** 倒數文字保留的高度。 */
        const val TIMER_HEIGHT = 14

        /** 展開提示且不含額外說明行時的高度。 */
        const val EXPANDED_HEIGHT = 38

        /** 每行提示文字占用的高度。 */
        const val TEXT_LINE_HEIGHT = 11
    }
}
