package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.hudCoordinate
import net.minecraft.text.Text

/** Prompt 是否包含需要玩家明確選擇的內容。 */
internal val PlayerDecisionPromptDto.isInteractive: Boolean
    get() = actions.isNotEmpty() || preparation != null

/** 精簡 HUD 在倒數上方要顯示的內容。 */
internal sealed interface CompactDecisionHudContent {
    /** 只有倒數。 */
    data object TimerOnly : CompactDecisionHudContent

    /** 進行中多選選牌的進度提示。 */
    data class TileSelection(val progress: DecisionTileSelectionState.Progress) : CompactDecisionHudContent

    /** 操作介面已被 Esc 收起、可以重新開啟的提醒。 */
    data object ReopenReminder : CompactDecisionHudContent
}

/**
 * 決定精簡 HUD 要顯示什麼。
 *
 * 進行中的選牌進度優先；沒有選牌但玩家把仍然有效的互動式操作介面收起來時顯示重開提醒。玩家已明確進入
 * 任何一種實體牌操作模式（[isPhysicalSelectionActive]）就不再提醒重開——那是刻意收起畫面去點實體牌，
 * 不是忘記。
 */
internal fun compactDecisionHudContent(
    prompt: PlayerDecisionPromptDto?,
    dismissedDecisionKey: String?,
    isPhysicalSelectionActive: Boolean,
    tileSelectionProgress: DecisionTileSelectionState.Progress?,
): CompactDecisionHudContent = when {
    tileSelectionProgress != null -> CompactDecisionHudContent.TileSelection(tileSelectionProgress)
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
 * 倒數固定貼齊群組下緣，提示文字往上長；群組整體的位置由玩家在 HUD 編輯器調整的比例決定。
 *
 * @property screenWidth 目前 GUI scaled 畫面寬度。
 * @property screenHeight 目前 GUI scaled 畫面高度。
 * @property ratioX 玩家設定的水平位置比例。
 * @property ratioY 玩家設定的垂直位置比例。
 * @property expanded 是否要為倒數上方的提示文字保留空間。
 */
internal data class CompactDecisionHudLayout(
    val screenWidth: Int,
    val screenHeight: Int,
    val ratioX: Double,
    val ratioY: Double,
    val expanded: Boolean,
) {
    /** 群組寬度；畫面比固定寬度還窄時讓給畫面。 */
    val groupWidth: Int
        get() = GROUP_WIDTH.coerceAtMost(screenWidth)

    /** 群組高度。 */
    val groupHeight: Int
        get() = if (expanded) EXPANDED_HEIGHT else TIMER_HEIGHT

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

    /** 第二行提示文字的上緣。 */
    val detailTextTop: Int
        get() = groupTop + TEXT_LINE_HEIGHT

    internal companion object {
        const val GROUP_WIDTH = 220
        const val TIMER_HEIGHT = 14
        const val EXPANDED_HEIGHT = 38
        const val TEXT_LINE_HEIGHT = 11
    }
}
