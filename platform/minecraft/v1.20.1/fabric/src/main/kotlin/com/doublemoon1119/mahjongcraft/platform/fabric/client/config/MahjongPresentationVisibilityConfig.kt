package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 玩家可選擇是否呈現的非必要客戶端資訊。 */
@Serializable
data class MahjongPresentationVisibilityConfig(
    /** 是否顯示桌面中央局況面板。 */
    @SerialName("round-info-enabled") val roundInfoEnabled: Boolean = true,
    /** 是否顯示各座位玩家資訊面板。 */
    @SerialName("player-info-enabled") val playerInfoEnabled: Boolean = true,
    /** 是否顯示可加入遊戲提示面板。 */
    @SerialName("lobby-info-enabled") val lobbyInfoEnabled: Boolean = true,
    /** 是否顯示擲骰結果面板。 */
    @SerialName("dice-result-enabled") val diceResultEnabled: Boolean = true,
    /** 是否顯示一般倒數與等待提醒。 */
    @SerialName("compact-prompt-enabled") val compactPromptEnabled: Boolean = true,
    /** 是否顯示打牌後聽牌分析。 */
    @SerialName("discard-analysis-enabled") val discardAnalysisEnabled: Boolean = true,
    /** 是否顯示胡牌結算面板。 */
    @SerialName("win-settlement-enabled") val winSettlementEnabled: Boolean = true,
    /** 是否顯示流局結算面板。 */
    @SerialName("draw-settlement-enabled") val drawSettlementEnabled: Boolean = true,
    /** 是否顯示整場結算面板。 */
    @SerialName("match-settlement-enabled") val matchSettlementEnabled: Boolean = true,
    /** 是否高亮準星所指牌張的同種可見牌。 */
    @SerialName("matching-tile-highlight-enabled") val matchingTileHighlightEnabled: Boolean = true,
    /** 是否顯示捨牌落地後的短暫牌面提示。 */
    @SerialName("discard-popup-enabled") val discardPopupEnabled: Boolean = true,
    /** 是否顯示鳴牌落地後的短暫牌組提示。 */
    @SerialName("meld-popup-enabled") val meldPopupEnabled: Boolean = true,
    /** 是否顯示目前已啟用的自動操作狀態 HUD。 */
    @SerialName("automatic-control-status-enabled") val automaticControlStatusEnabled: Boolean = true,
)
