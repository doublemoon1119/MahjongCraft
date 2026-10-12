package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoYaku
import kotlinx.serialization.Serializable

/** [PaoYaku] 的 persistence DTO。 */
@Serializable
enum class PaoYakuPersistenceDto {
    /** 大三元。 */
    DAISANGEN,

    /** 大四喜。 */
    DAISUUSHII,
}
