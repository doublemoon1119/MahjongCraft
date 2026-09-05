package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * MahjongCraft client 端持久化設定；跟 server 端設定（[FabricServerConfigManager]）
 * 完全分開存放，互不影響。TOML 欄位一律 kebab-case，跟 server 設定同一套命名慣例。
 */
@Serializable
data class MahjongClientConfigState(
    /**
     * 是否在牌面角落顯示輔助標籤（數字／字母），給非中文圈玩家辨識牌面用；透過
     * `/mahjongcraft_client label toggle` 切換。
     */
    @SerialName("tile-labels-enabled")
    val tileLabelsEnabled: Boolean = false,

    /**
     * 是否啟用自動整理手牌；透過 `/mahjongcraft_client hand_sort toggle` 切換。跟
     * [tileLabelsEnabled] 不同，這個偏好還需要同步給伺服器（見 `FabricHandSortCommand` KDoc）——
     * 手牌 tile entity 是伺服器端共用的實體，排序結果必須由伺服器套用才會反映在實際世界座標上。
     */
    @SerialName("auto-sort-hand-enabled")
    val autoSortHandEnabled: Boolean = true,

    /** MahjongCraft 內建遊戲 HUD 的本機位置。 */
    @SerialName("hud-layout")
    val hudLayout: MahjongHudLayoutConfig = MahjongHudLayoutConfig(),

    /** 可由玩家個別關閉的 HUD、遊戲面板與短暫視覺回饋。 */
    @SerialName("presentation-visibility")
    val presentationVisibility: MahjongPresentationVisibilityConfig = MahjongPresentationVisibilityConfig(),
)

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
)

/**
 * HUD 位置以合法可移動範圍的比例保存；零代表左／上界，一代表右／下界。
 * 動態寬度面板固定水平置中，因此只保存垂直位置。
 */
@Serializable
data class MahjongHudLayoutConfig(
    /** 操作面板的垂直位置比例。 */
    @SerialName("decision-panel-y")
    val decisionPanelY: Double = 0.88,

    /** 一般倒數與等待提醒的水平位置比例。 */
    @SerialName("compact-prompt-x")
    val compactPromptX: Double = 0.5,

    /** 一般倒數與等待提醒的垂直位置比例。 */
    @SerialName("compact-prompt-y")
    val compactPromptY: Double = 0.90,

    /** 打牌分析面板的垂直位置比例。 */
    @SerialName("discard-analysis-y")
    val discardAnalysisY: Double = 0.86,
) {
    init {
        require(decisionPanelY in 0.0..1.0) { "decision-panel-y must be between 0.0 and 1.0" }
        require(compactPromptX in 0.0..1.0) { "compact-prompt-x must be between 0.0 and 1.0" }
        require(compactPromptY in 0.0..1.0) { "compact-prompt-y must be between 0.0 and 1.0" }
        require(discardAnalysisY in 0.0..1.0) { "discard-analysis-y must be between 0.0 and 1.0" }
    }
}
