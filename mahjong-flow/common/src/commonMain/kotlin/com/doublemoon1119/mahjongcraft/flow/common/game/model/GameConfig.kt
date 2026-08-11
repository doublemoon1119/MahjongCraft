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
    val timeControl: ActionTimeControl = ActionTimeControl.Normal,
    val spectatingPolicy: SpectatingPolicy = SpectatingPolicy.ENABLED,
    val spectatorHandVisibility: SpectatorHandVisibility = SpectatorHandVisibility.REVEALED,
)

/**
 * 玩家每次動作使用的 A+B 思考時間設定。
 *
 * A 會在每次動作重新取得；A 用完後才消耗該玩家整場共用的 B。
 *
 * 內建組合沿用既有遊戲提供的五種選項；不符合內建組合的設定使用 [Custom]。
 */
sealed interface ActionTimeControl {
    /** 每次動作重新取得的基本思考秒數。 */
    val actionSeconds: Int

    /** 每位玩家開局時取得的共用保留秒數。 */
    val reserveSeconds: Int

    /** 三秒 A 與五秒 B 的內建組合。 */
    data object VeryShort : ActionTimeControl {
        override val actionSeconds: Int = 3
        override val reserveSeconds: Int = 5
    }

    /** 五秒 A 與十秒 B 的內建組合。 */
    data object Short : ActionTimeControl {
        override val actionSeconds: Int = 5
        override val reserveSeconds: Int = 10
    }

    /** 五秒 A 與二十秒 B 的預設組合。 */
    data object Normal : ActionTimeControl {
        override val actionSeconds: Int = 5
        override val reserveSeconds: Int = 20
    }

    /** 六十秒 A 且不提供 B 的內建組合。 */
    data object Long : ActionTimeControl {
        override val actionSeconds: Int = 60
        override val reserveSeconds: Int = 0
    }

    /** 三百秒 A 且不提供 B 的內建組合。 */
    data object VeryLong : ActionTimeControl {
        override val actionSeconds: Int = 300
        override val reserveSeconds: Int = 0
    }

    /**
     * 不符合內建組合的自訂 A+B 設定。
     *
     * @property actionSeconds 每次動作重新取得的基本思考秒數。
     * @property reserveSeconds 每位玩家開局時取得的共用保留秒數。
     */
    data class Custom(
        override val actionSeconds: Int,
        override val reserveSeconds: Int,
    ) : ActionTimeControl {
        init {
            validate(actionSeconds, reserveSeconds)
        }
    }

    /** 建立與正規化 [ActionTimeControl] 的集中入口。 */
    companion object {
        /**
         * 將 A+B 秒數正規化成內建組合或 [Custom]。
         *
         * @param actionSeconds 每次動作重新取得的基本思考秒數。
         * @param reserveSeconds 每位玩家開局時取得的共用保留秒數。
         * @return 數值符合內建組合時回傳對應 singleton，否則回傳 [Custom]。
         */
        fun from(actionSeconds: Int, reserveSeconds: Int): ActionTimeControl {
            validate(actionSeconds, reserveSeconds)
            return when (actionSeconds to reserveSeconds) {
                VeryShort.actionSeconds to VeryShort.reserveSeconds -> VeryShort
                Short.actionSeconds to Short.reserveSeconds -> Short
                Normal.actionSeconds to Normal.reserveSeconds -> Normal
                Long.actionSeconds to Long.reserveSeconds -> Long
                VeryLong.actionSeconds to VeryLong.reserveSeconds -> VeryLong
                else -> Custom(actionSeconds, reserveSeconds)
            }
        }

        /** 驗證 A+B 秒數可用於決策計時。 */
        private fun validate(actionSeconds: Int, reserveSeconds: Int) {
            require(actionSeconds >= 0) { "Action time must not be negative" }
            require(reserveSeconds >= 0) { "Reserve time must not be negative" }
            require(actionSeconds > 0 || reserveSeconds > 0) { "Action and reserve time must not both be zero" }
        }
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
