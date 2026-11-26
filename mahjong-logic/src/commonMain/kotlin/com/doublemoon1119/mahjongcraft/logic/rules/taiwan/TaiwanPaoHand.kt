package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType

/**
 * 適用包牌的牌型。
 *
 * @property taiType 胡牌時必須成立的計台項目，包牌才會套用。
 */
enum class TaiwanPaoHand(val taiType: TaiwanTaiType) {
    /** 大三元：第三組三元牌由別人打出的牌碰或明槓完成。 */
    BIG_DRAGONS(TaiwanTaiType.BigDragons),

    /** 大四喜：第四組風牌由別人打出的牌碰或明槓完成。 */
    BIG_WINDS(TaiwanTaiType.BigWinds),
}
