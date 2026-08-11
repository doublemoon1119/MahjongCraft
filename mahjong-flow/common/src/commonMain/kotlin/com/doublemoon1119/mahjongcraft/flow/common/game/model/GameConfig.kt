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
 * @property timeControl 玩家動作採用的基本思考時間與保留思考時間。
 * @property decisionTimeoutPolicy 玩家耗盡全部思考時間後採用的流程政策。
 * @property spectatingPolicy 外部玩家是否可以旁觀進行中的遊戲。
 * @property spectatorHandVisibility 旁觀者可見的手牌範圍。
 */
data class GameFlowConfig(
    val timeControl: ActionTimeControl = ActionTimeControl.Normal,
    val decisionTimeoutPolicy: DecisionTimeoutPolicy = DecisionTimeoutPolicy.FORCED_AUTO_PLAY,
    val spectatingPolicy: SpectatingPolicy = SpectatingPolicy.ENABLED,
    val spectatorHandVisibility: SpectatorHandVisibility = SpectatorHandVisibility.REVEALED,
)

/** 玩家耗盡基本思考時間與保留思考時間後採用的流程政策。 */
enum class DecisionTimeoutPolicy {
    /** 停止接受玩家手動操作，並由伺服器自動摸切及跳過所有反應至遊戲結束。 */
    FORCED_AUTO_PLAY,
}

/**
 * 玩家每次動作使用的基本思考時間與保留思考時間設定。
 *
 * 基本思考時間會在每次動作重新取得；用完後才消耗該玩家整場共用的保留思考時間。
 *
 * 內建組合沿用既有遊戲提供的五種選項；不符合內建組合的設定使用 [Custom]。
 */
sealed interface ActionTimeControl {
    /** 每次動作重新取得的基本思考秒數。 */
    val baseSeconds: Int

    /** 每位玩家開局時取得的共用保留秒數。 */
    val reserveSeconds: Int

    /** 三秒基本思考時間與五秒保留思考時間的內建組合。 */
    data object VeryShort : ActionTimeControl {
        override val baseSeconds: Int = 3
        override val reserveSeconds: Int = 5
    }

    /** 五秒基本思考時間與十秒保留思考時間的內建組合。 */
    data object Short : ActionTimeControl {
        override val baseSeconds: Int = 5
        override val reserveSeconds: Int = 10
    }

    /** 五秒基本思考時間與二十秒保留思考時間的預設組合。 */
    data object Normal : ActionTimeControl {
        override val baseSeconds: Int = 5
        override val reserveSeconds: Int = 20
    }

    /** 六十秒基本思考時間且不提供保留思考時間的內建組合。 */
    data object Long : ActionTimeControl {
        override val baseSeconds: Int = 60
        override val reserveSeconds: Int = 0
    }

    /** 三百秒基本思考時間且不提供保留思考時間的內建組合。 */
    data object VeryLong : ActionTimeControl {
        override val baseSeconds: Int = 300
        override val reserveSeconds: Int = 0
    }

    /**
     * 不符合內建組合的自訂基本思考時間與保留思考時間設定。
     *
     * @property baseSeconds 每次動作重新取得的基本思考秒數。
     * @property reserveSeconds 每位玩家開局時取得的共用保留秒數。
     */
    data class Custom(
        override val baseSeconds: Int,
        override val reserveSeconds: Int,
    ) : ActionTimeControl {
        init {
            validate(baseSeconds, reserveSeconds)
        }
    }

    /** 建立與正規化 [ActionTimeControl] 的集中入口。 */
    companion object {
        /**
         * 將基本思考秒數與保留思考秒數正規化成內建組合或 [Custom]。
         *
         * @param baseSeconds 每次動作重新取得的基本思考秒數。
         * @param reserveSeconds 每位玩家開局時取得的共用保留秒數。
         * @return 數值符合內建組合時回傳對應 singleton，否則回傳 [Custom]。
         */
        fun from(baseSeconds: Int, reserveSeconds: Int): ActionTimeControl {
            validate(baseSeconds, reserveSeconds)
            return when (baseSeconds to reserveSeconds) {
                VeryShort.baseSeconds to VeryShort.reserveSeconds -> VeryShort
                Short.baseSeconds to Short.reserveSeconds -> Short
                Normal.baseSeconds to Normal.reserveSeconds -> Normal
                Long.baseSeconds to Long.reserveSeconds -> Long
                VeryLong.baseSeconds to VeryLong.reserveSeconds -> VeryLong
                else -> Custom(baseSeconds, reserveSeconds)
            }
        }

        /** 驗證基本思考秒數與保留思考秒數可用於決策計時。 */
        private fun validate(baseSeconds: Int, reserveSeconds: Int) {
            require(baseSeconds >= 0) { "Base time must not be negative" }
            require(reserveSeconds >= 0) { "Reserve time must not be negative" }
            require(baseSeconds > 0 || reserveSeconds > 0) { "Base and reserve time must not both be zero" }
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
