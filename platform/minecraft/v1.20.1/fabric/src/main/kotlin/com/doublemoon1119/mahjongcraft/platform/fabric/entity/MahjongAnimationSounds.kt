package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/** 牌與骰子動畫使用的語意聲音 ID。 */
internal object MahjongAnimationSounds {
    /** 麻將牌落到牌河或副露區，以及手牌改變姿態時使用的聲音。 */
    const val TILE_PUT_DOWN: String = "minecraft:entity.item_frame.place"

    /** 牌牆每一墩落到桌面時使用的聲音。 */
    const val WALL_STACK_LAND: String = "minecraft:entity.item_frame.place"

    /** 開局每批牌從牌牆起飛時使用的聲音。 */
    const val DEAL_BATCH: String = "minecraft:entity.item_frame.place"

    /** 每巡摸牌抵達手牌右側摸牌位時使用的聲音。 */
    const val DRAW_TILE_LAND: String = "minecraft:entity.item_frame.place"

    /** 一批骰子開始擲出時使用的聲音。 */
    const val DICE_THROW: String = "minecraft:block.wooden_button.click_on"

    /** 單顆骰子第一次落桌時使用的聲音。 */
    const val DICE_LAND: String = "minecraft:entity.item_frame.place"

    /** 胡牌閃電落下時使用的雷聲。 */
    const val WIN_LIGHTNING_THUNDER: String = "minecraft:entity.lightning_bolt.thunder"

    /** 動畫聲音預定時刻後仍允許正常 server tick 執行的寬限。 */
    const val EVENT_GRACE_TICKS: Long = 2L
}
