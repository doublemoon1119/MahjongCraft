package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import kotlinx.serialization.Serializable

/** 權威局位階段的網路 DTO。 */
@Serializable
enum class MatchRoundPhaseDto {
    /** 原定賽程。 */
    REGULAR,

    /** 規則追加的延長賽程。 */
    EXTRA,
}
