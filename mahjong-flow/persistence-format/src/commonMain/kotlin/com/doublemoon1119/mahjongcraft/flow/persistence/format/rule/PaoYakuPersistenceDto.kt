package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoYaku
import kotlinx.serialization.Serializable

/** [PaoYaku] 的 persistence DTO。 */
@Serializable
enum class PaoYakuPersistenceDto { DAISANGEN, DAISUUSHII }
