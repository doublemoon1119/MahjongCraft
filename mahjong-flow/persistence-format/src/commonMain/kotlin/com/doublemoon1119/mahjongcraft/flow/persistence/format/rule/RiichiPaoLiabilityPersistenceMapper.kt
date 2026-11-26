package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPaoLiability
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPaoYaku

/** 將 [RiichiPaoLiability] 轉換成 persistence DTO。 */
fun RiichiPaoLiability.toPersistenceDto(): RiichiPaoLiabilityPersistenceDto = RiichiPaoLiabilityPersistenceDto(
    yaku = when (yaku) {
        RiichiPaoYaku.Daisangen -> RiichiPaoYakuPersistenceDto.DAISANGEN
        RiichiPaoYaku.Daisuushii -> RiichiPaoYakuPersistenceDto.DAISUUSHII
    },
    direction = direction.toPersistenceDto(),
)

/** 將包牌責任 persistence DTO 還原成 [RiichiPaoLiability]。 */
fun RiichiPaoLiabilityPersistenceDto.toDomain(): RiichiPaoLiability = RiichiPaoLiability(
    yaku = when (yaku) {
        RiichiPaoYakuPersistenceDto.DAISANGEN -> RiichiPaoYaku.Daisangen
        RiichiPaoYakuPersistenceDto.DAISUUSHII -> RiichiPaoYaku.Daisuushii
    },
    direction = direction.toDomain(),
)
