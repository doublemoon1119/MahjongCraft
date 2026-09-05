package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/** 牌與骰子動畫使用的語意聲音 ID。 */
internal object MahjongAnimationSounds {
    /** 麻將牌落到牌河時使用的聲音。 */
    const val TILE_DISCARD_LAND: String = "mahjongcraft:tile.discard_land"

    /** 麻將牌落到副露區時使用的聲音。 */
    const val TILE_MELD_LAND: String = "mahjongcraft:tile.meld_land"

    /** 整組手牌翻起、倒下或蓋下時使用的聲音。 */
    const val TILE_HAND_TURN: String = "mahjongcraft:tile.hand_turn"

    /** 牌牆每一墩落到桌面時使用的聲音。 */
    const val WALL_STACK_LAND: String = "mahjongcraft:tile.wall_stack_land"

    /** 開局每批牌從牌牆起飛時使用的聲音。 */
    const val DEAL_BATCH: String = "mahjongcraft:tile.deal_batch"

    /** 寶牌指示牌開始翻面公開時使用的提示聲音。 */
    const val DORA_REVEAL: String = "mahjongcraft:tile.dora_reveal"

    /** 每巡摸牌抵達手牌右側摸牌位時使用的聲音。 */
    const val DRAW_TILE_LAND: String = "mahjongcraft:tile.draw_land"

    /** 單顆骰子每次接觸桌面時使用的聲音。 */
    const val DICE_LAND: String = "mahjongcraft:dice.land"

    /** 玩家自由放置點棒時使用的聲音。 */
    const val SCORING_STICK_PLACE: String = "mahjongcraft:scoring_stick.place"

    /** 胡牌閃電落下時使用的雷聲。 */
    const val WIN_LIGHTNING: String = "mahjongcraft:win.lightning"

    /** 動畫聲音預定時刻後仍允許正常 server tick 執行的寬限。 */
    const val EVENT_GRACE_TICKS: Long = 2L
}
