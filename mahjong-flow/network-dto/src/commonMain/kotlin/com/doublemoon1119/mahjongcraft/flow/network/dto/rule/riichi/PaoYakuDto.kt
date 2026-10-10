package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoYaku
import kotlinx.serialization.Serializable

/** [PaoYaku] 的網路 DTO。 */
@Serializable
enum class PaoYakuDto { Daisangen, Daisuushii }
