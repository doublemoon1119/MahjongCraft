package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 玩家可選擇是否呈現的非必要客戶端資訊。
 *
 * @property roundInfoEnabled 是否顯示桌面中央局況面板。
 * @property playerInfoEnabled 是否顯示各座位玩家資訊面板。
 * @property lobbyInfoEnabled 是否顯示可加入遊戲提示面板。
 * @property diceResultEnabled 是否顯示擲骰結果面板。
 * @property compactPromptEnabled 是否顯示一般倒數與等待提醒。
 * @property discardAnalysisEnabled 是否顯示打牌後聽牌分析。
 * @property winSettlementEnabled 是否顯示胡牌結算面板。
 * @property drawSettlementEnabled 是否顯示流局結算面板。
 * @property matchSettlementEnabled 是否顯示整場結算面板。
 * @property matchingTileHighlightEnabled 是否高亮準星所指牌張的同種可見牌。
 * @property latestDiscardHighlightEnabled 規則有開最新捨牌標示時，是否讓最後打出的牌外框發光。
 * @property discardPopupEnabled 是否顯示捨牌落地後的短暫牌面提示。
 * @property meldPopupEnabled 是否顯示鳴牌落地後的短暫牌組提示。
 * @property automaticControlStatusEnabled 是否顯示目前已啟用的自動操作狀態 HUD。
 */
@Serializable
data class MahjongPresentationVisibilityConfig(
    @SerialName("round-info-enabled") val roundInfoEnabled: Boolean = true,
    @SerialName("player-info-enabled") val playerInfoEnabled: Boolean = true,
    @SerialName("lobby-info-enabled") val lobbyInfoEnabled: Boolean = true,
    @SerialName("dice-result-enabled") val diceResultEnabled: Boolean = true,
    @SerialName("compact-prompt-enabled") val compactPromptEnabled: Boolean = true,
    @SerialName("discard-analysis-enabled") val discardAnalysisEnabled: Boolean = true,
    @SerialName("win-settlement-enabled") val winSettlementEnabled: Boolean = true,
    @SerialName("draw-settlement-enabled") val drawSettlementEnabled: Boolean = true,
    @SerialName("match-settlement-enabled") val matchSettlementEnabled: Boolean = true,
    @SerialName("matching-tile-highlight-enabled") val matchingTileHighlightEnabled: Boolean = true,
    @SerialName("latest-discard-highlight-enabled") val latestDiscardHighlightEnabled: Boolean = true,
    @SerialName("discard-popup-enabled") val discardPopupEnabled: Boolean = true,
    @SerialName("meld-popup-enabled") val meldPopupEnabled: Boolean = true,
    @SerialName("automatic-control-status-enabled") val automaticControlStatusEnabled: Boolean = true,
)
