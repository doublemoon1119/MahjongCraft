package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPaoYaku
import kotlinx.serialization.Serializable

/** [RiichiPaoYaku] 的網路 DTO。 */
@Serializable
enum class RiichiPaoYakuDto {
    /** 大三元。 */
    Daisangen,

    /** 大四喜。 */
    Daisuushii,
}
