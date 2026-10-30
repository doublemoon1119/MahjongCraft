package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoLiability
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoYaku

/** 將 [PaoLiability] 轉換成 persistence DTO。 */
fun PaoLiability.toPersistenceDto(): PaoLiabilityPersistenceDto = PaoLiabilityPersistenceDto(
    yaku = when (yaku) {
        PaoYaku.Daisangen -> PaoYakuPersistenceDto.DAISANGEN
        PaoYaku.Daisuushii -> PaoYakuPersistenceDto.DAISUUSHII
    },
    direction = direction.toPersistenceDto(),
)

/** 將包牌責任 persistence DTO 還原成 [PaoLiability]。 */
fun PaoLiabilityPersistenceDto.toDomain(): PaoLiability = PaoLiability(
    yaku = when (yaku) {
        PaoYakuPersistenceDto.DAISANGEN -> PaoYaku.Daisangen
        PaoYakuPersistenceDto.DAISUUSHII -> PaoYaku.Daisuushii
    },
    direction = direction.toDomain(),
)
