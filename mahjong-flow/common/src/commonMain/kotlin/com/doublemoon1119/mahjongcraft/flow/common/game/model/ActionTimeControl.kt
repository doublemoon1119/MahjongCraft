package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 玩家每次動作使用的基本思考時間與保留思考時間設定。
 *
 * 基本思考時間會在每次動作重新取得；用完後才消耗該玩家整場共用的保留思考時間。兩者都必須是整數秒。
 *
 * 內建組合沿用既有遊戲提供的五種選項；不符合內建組合的設定使用 [Custom]。
 */
sealed interface ActionTimeControl {
    /** 每次動作重新取得的基本思考時間。 */
    val base: Duration

    /** 每位玩家開局時取得的共用保留思考時間。 */
    val reserve: Duration

    /** 三秒基本思考時間與五秒保留思考時間的內建組合。 */
    data object VeryShort : ActionTimeControl {
        override val base: Duration = 3.seconds
        override val reserve: Duration = 5.seconds
    }

    /** 五秒基本思考時間與十秒保留思考時間的內建組合。 */
    data object Short : ActionTimeControl {
        override val base: Duration = 5.seconds
        override val reserve: Duration = 10.seconds
    }

    /** 五秒基本思考時間與二十秒保留思考時間的預設組合。 */
    data object Normal : ActionTimeControl {
        override val base: Duration = 5.seconds
        override val reserve: Duration = 20.seconds
    }

    /** 六十秒基本思考時間且不提供保留思考時間的內建組合。 */
    data object Long : ActionTimeControl {
        override val base: Duration = 60.seconds
        override val reserve: Duration = Duration.ZERO
    }

    /** 三百秒基本思考時間且不提供保留思考時間的內建組合。 */
    data object VeryLong : ActionTimeControl {
        override val base: Duration = 300.seconds
        override val reserve: Duration = Duration.ZERO
    }

    /**
     * 不符合內建組合的自訂基本思考時間與保留思考時間設定。
     *
     * @property base 每次動作重新取得的基本思考時間。
     * @property reserve 每位玩家開局時取得的共用保留思考時間。
     */
    data class Custom(
        override val base: Duration,
        override val reserve: Duration,
    ) : ActionTimeControl {
        init {
            validate(base, reserve)
        }
    }

    /** 建立與正規化 [ActionTimeControl] 的集中入口。 */
    companion object {
        /**
         * 將基本思考時間與保留思考時間正規化成內建組合或 [Custom]。
         *
         * @param base 每次動作重新取得的基本思考時間。
         * @param reserve 每位玩家開局時取得的共用保留思考時間。
         * @return 數值符合內建組合時回傳對應 singleton，否則回傳 [Custom]。
         */
        fun from(base: Duration, reserve: Duration): ActionTimeControl {
            validate(base, reserve)
            return listOf(VeryShort, Short, Normal, Long, VeryLong)
                .firstOrNull { it.base == base && it.reserve == reserve }
                ?: Custom(base, reserve)
        }

        /**
         * 驗證基本思考時間與保留思考時間可用於決策計時。
         *
         * @param base 每次動作重新取得的基本思考時間。
         * @param reserve 玩家整場共用的保留思考時間。
         */
        private fun validate(base: Duration, reserve: Duration) {
            require(base.isNonNegativeWholeSeconds()) { "Base time must be a non-negative whole number of seconds" }
            require(reserve.isNonNegativeWholeSeconds()) {
                "Reserve time must be a non-negative whole number of seconds"
            }
            require(base.isPositive() || reserve.isPositive()) { "Base and reserve time must not both be zero" }
        }
    }
}
