package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * HUD 位置以合法可移動範圍的比例保存；零代表左／上界，一代表右／下界。
 * 動態寬度面板固定水平置中，因此只保存垂直位置。
 *
 * @property decisionPanelY 操作面板的垂直位置比例。
 * @property compactPromptX 一般倒數與等待提醒的水平位置比例。
 * @property compactPromptY 一般倒數與等待提醒的垂直位置比例。
 * @property discardAnalysisY 手牌分析面板的垂直位置比例。
 * @property automaticControlStatusX 自動操作狀態 HUD 的水平位置比例。
 * @property automaticControlStatusY 自動操作狀態 HUD 的垂直位置比例。
 * @throws IllegalArgumentException 任一位置比例不在 0 至 1（含邊界）之間時。
 */
@Serializable
data class MahjongHudLayoutConfig(
    @SerialName("decision-panel-y") val decisionPanelY: Double = 0.88,
    @SerialName("compact-prompt-x") val compactPromptX: Double = 0.95,
    @SerialName("compact-prompt-y") val compactPromptY: Double = 0.78,
    @SerialName("discard-analysis-y") val discardAnalysisY: Double = 0.8,
    @SerialName("automatic-control-status-x") val automaticControlStatusX: Double = 0.03,
    @SerialName("automatic-control-status-y") val automaticControlStatusY: Double = 0.22,
) {
    init {
        require(decisionPanelY in 0.0..1.0) { "decision-panel-y must be between 0.0 and 1.0" }
        require(compactPromptX in 0.0..1.0) { "compact-prompt-x must be between 0.0 and 1.0" }
        require(compactPromptY in 0.0..1.0) { "compact-prompt-y must be between 0.0 and 1.0" }
        require(discardAnalysisY in 0.0..1.0) { "discard-analysis-y must be between 0.0 and 1.0" }
        require(automaticControlStatusX in 0.0..1.0) { "automatic-control-status-x must be between 0.0 and 1.0" }
        require(automaticControlStatusY in 0.0..1.0) { "automatic-control-status-y must be between 0.0 and 1.0" }
    }
}
