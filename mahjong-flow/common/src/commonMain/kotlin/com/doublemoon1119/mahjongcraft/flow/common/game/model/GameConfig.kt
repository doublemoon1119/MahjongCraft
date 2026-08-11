package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/**
 * 建立一場遊戲所需的完整設定。
 *
 * @property ruleConfig 影響麻將合法動作與結算的規則設定。
 * @property flowConfig 影響伺服器流程、計時與觀看權限的設定。
 */
data class GameConfig(
    val ruleConfig: MahjongRuleConfig,
    val flowConfig: GameFlowConfig = GameFlowConfig(),
)

/**
 * 與麻將規則無關的遊戲流程設定。
 *
 * @property timeControl 玩家動作採用的 A+B 思考時間。
 * @property spectatingPolicy 外部玩家是否可以旁觀進行中的遊戲。
 * @property spectatorHandVisibility 旁觀者可見的手牌範圍。
 */
data class GameFlowConfig(
    val timeControl: ActionTimeControl = ActionTimeControl(),
    val spectatingPolicy: SpectatingPolicy = SpectatingPolicy.ENABLED,
    val spectatorHandVisibility: SpectatorHandVisibility = SpectatorHandVisibility.REVEALED,
)

/**
 * 玩家每次動作使用的 A+B 思考時間設定。
 *
 * A 會在每次動作重新取得；A 用完後才消耗該玩家整場共用的 B。
 *
 * @property actionSeconds 每次動作重新取得的基本思考秒數。
 * @property reserveSeconds 每位玩家開局時取得的共用保留秒數。
 */
data class ActionTimeControl(
    val actionSeconds: Int = 5,
    val reserveSeconds: Int = 20,
) {
    init {
        require(actionSeconds >= 0) { "Action time must not be negative" }
        require(reserveSeconds >= 0) { "Reserve time must not be negative" }
        require(actionSeconds > 0 || reserveSeconds > 0) { "Action and reserve time must not both be zero" }
    }
}

/** 外部玩家是否可以旁觀進行中的遊戲。 */
enum class SpectatingPolicy {
    /** 不接受外部玩家取得遊戲快照。 */
    DISABLED,

    /** 接受外部玩家取得依可見性政策過濾的遊戲快照。 */
    ENABLED,
}

/** 外部旁觀者取得遊戲快照時的手牌可見範圍。 */
enum class SpectatorHandVisibility {
    /** 隱藏所有玩家的未公開手牌。 */
    HIDDEN,

    /** 顯示所有玩家的手牌。 */
    REVEALED,
}
