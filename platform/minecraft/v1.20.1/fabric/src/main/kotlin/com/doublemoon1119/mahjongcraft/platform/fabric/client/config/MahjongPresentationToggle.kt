package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys

/**
 * 設定畫面上可個別開關的顯示項目，明確對應名稱、說明翻譯鍵與 [MahjongPresentationVisibilityConfig] 欄位。
 *
 * @property nameKey 開關名稱的翻譯鍵。
 * @property descriptionKey 開關說明的翻譯鍵。
 * @property read 讀取目前開關值。
 * @property update 寫入新開關值後的顯示設定。
 */
internal enum class MahjongPresentationToggle(
    val nameKey: String,
    val descriptionKey: String,
    val read: (MahjongPresentationVisibilityConfig) -> Boolean,
    val update: (MahjongPresentationVisibilityConfig, Boolean) -> MahjongPresentationVisibilityConfig,
) {
    /** 局況資訊。 */
    RoundInfo(
        nameKey = MinecraftClientConfigScreenKeys.PRESENTATION_ROUND_INFO,
        descriptionKey = MinecraftClientConfigScreenKeys.PRESENTATION_ROUND_INFO_DESCRIPTION,
        read = { it.roundInfoEnabled },
        update = { config, enabled -> config.copy(roundInfoEnabled = enabled) },
    ),

    /** 玩家資訊。 */
    PlayerInfo(
        nameKey = MinecraftClientConfigScreenKeys.PRESENTATION_PLAYER_INFO,
        descriptionKey = MinecraftClientConfigScreenKeys.PRESENTATION_PLAYER_INFO_DESCRIPTION,
        read = { it.playerInfoEnabled },
        update = { config, enabled -> config.copy(playerInfoEnabled = enabled) },
    ),

    /** 大廳資訊。 */
    LobbyInfo(
        nameKey = MinecraftClientConfigScreenKeys.PRESENTATION_LOBBY_INFO,
        descriptionKey = MinecraftClientConfigScreenKeys.PRESENTATION_LOBBY_INFO_DESCRIPTION,
        read = { it.lobbyInfoEnabled },
        update = { config, enabled -> config.copy(lobbyInfoEnabled = enabled) },
    ),

    /** 骰子結果。 */
    DiceResult(
        nameKey = MinecraftClientConfigScreenKeys.PRESENTATION_DICE_RESULT,
        descriptionKey = MinecraftClientConfigScreenKeys.PRESENTATION_DICE_RESULT_DESCRIPTION,
        read = { it.diceResultEnabled },
        update = { config, enabled -> config.copy(diceResultEnabled = enabled) },
    ),

    /** 精簡提示。 */
    CompactPrompt(
        nameKey = MinecraftClientConfigScreenKeys.PRESENTATION_COMPACT_PROMPT,
        descriptionKey = MinecraftClientConfigScreenKeys.PRESENTATION_COMPACT_PROMPT_DESCRIPTION,
        read = { it.compactPromptEnabled },
        update = { config, enabled -> config.copy(compactPromptEnabled = enabled) },
    ),

    /** 捨牌分析。 */
    DiscardAnalysis(
        nameKey = MinecraftClientConfigScreenKeys.PRESENTATION_DISCARD_ANALYSIS,
        descriptionKey = MinecraftClientConfigScreenKeys.PRESENTATION_DISCARD_ANALYSIS_DESCRIPTION,
        read = { it.discardAnalysisEnabled },
        update = { config, enabled -> config.copy(discardAnalysisEnabled = enabled) },
    ),

    /** 和牌結算。 */
    WinSettlement(
        nameKey = MinecraftClientConfigScreenKeys.PRESENTATION_WIN_SETTLEMENT,
        descriptionKey = MinecraftClientConfigScreenKeys.PRESENTATION_WIN_SETTLEMENT_DESCRIPTION,
        read = { it.winSettlementEnabled },
        update = { config, enabled -> config.copy(winSettlementEnabled = enabled) },
    ),

    /** 流局結算。 */
    DrawSettlement(
        nameKey = MinecraftClientConfigScreenKeys.PRESENTATION_DRAW_SETTLEMENT,
        descriptionKey = MinecraftClientConfigScreenKeys.PRESENTATION_DRAW_SETTLEMENT_DESCRIPTION,
        read = { it.drawSettlementEnabled },
        update = { config, enabled -> config.copy(drawSettlementEnabled = enabled) },
    ),

    /** 整場結算。 */
    MatchSettlement(
        nameKey = MinecraftClientConfigScreenKeys.PRESENTATION_MATCH_SETTLEMENT,
        descriptionKey = MinecraftClientConfigScreenKeys.PRESENTATION_MATCH_SETTLEMENT_DESCRIPTION,
        read = { it.matchSettlementEnabled },
        update = { config, enabled -> config.copy(matchSettlementEnabled = enabled) },
    ),

    /** 相同牌標示。 */
    MatchingTileHighlight(
        nameKey = MinecraftClientConfigScreenKeys.PRESENTATION_MATCHING_TILE_HIGHLIGHT,
        descriptionKey = MinecraftClientConfigScreenKeys.PRESENTATION_MATCHING_TILE_HIGHLIGHT_DESCRIPTION,
        read = { it.matchingTileHighlightEnabled },
        update = { config, enabled -> config.copy(matchingTileHighlightEnabled = enabled) },
    ),

    /** 打牌提示。 */
    DiscardPopup(
        nameKey = MinecraftClientConfigScreenKeys.PRESENTATION_DISCARD_POPUP,
        descriptionKey = MinecraftClientConfigScreenKeys.PRESENTATION_DISCARD_POPUP_DESCRIPTION,
        read = { it.discardPopupEnabled },
        update = { config, enabled -> config.copy(discardPopupEnabled = enabled) },
    ),

    /** 鳴牌提示。 */
    MeldPopup(
        nameKey = MinecraftClientConfigScreenKeys.PRESENTATION_MELD_POPUP,
        descriptionKey = MinecraftClientConfigScreenKeys.PRESENTATION_MELD_POPUP_DESCRIPTION,
        read = { it.meldPopupEnabled },
        update = { config, enabled -> config.copy(meldPopupEnabled = enabled) },
    ),

    /** 自動操作狀態。 */
    AutomaticControlStatus(
        nameKey = MinecraftClientConfigScreenKeys.PRESENTATION_AUTOMATIC_CONTROL_STATUS,
        descriptionKey = MinecraftClientConfigScreenKeys.PRESENTATION_AUTOMATIC_CONTROL_STATUS_DESCRIPTION,
        read = { it.automaticControlStatusEnabled },
        update = { config, enabled -> config.copy(automaticControlStatusEnabled = enabled) },
    ),
}
