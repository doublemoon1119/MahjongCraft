package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPaoYaku
import kotlinx.serialization.Serializable

/** [RiichiPaoYaku] 的 persistence DTO。 */
@Serializable
enum class RiichiPaoYakuPersistenceDto {
    /** 大三元。 */
    DAISANGEN,

    /** 大四喜。 */
    DAISUUSHII,
}
