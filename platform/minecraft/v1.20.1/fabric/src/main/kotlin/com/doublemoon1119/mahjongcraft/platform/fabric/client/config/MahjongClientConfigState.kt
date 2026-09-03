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
