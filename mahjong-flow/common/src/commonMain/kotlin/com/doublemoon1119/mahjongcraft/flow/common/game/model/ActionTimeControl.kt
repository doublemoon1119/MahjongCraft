package com.doublemoon1119.mahjongcraft.flow.common.game.model

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
