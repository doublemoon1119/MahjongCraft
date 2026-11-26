package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.RelativeDirectionPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPaoHand
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPaoLiability
import kotlinx.serialization.Serializable

/**
 * 台灣麻將包牌責任組件的 persistence DTO。
 *
 * @property hand 觸發包牌的牌型。
 * @property direction 包牌者相對於被包玩家的方位。
 */
@Serializable
data class TaiwanPaoLiabilityPersistenceDto(
    val hand: TaiwanPaoHandPersistenceValue,
    val direction: RelativeDirectionPersistenceDto,
)

/** 包牌牌型的穩定保存值。 */
@Serializable
enum class TaiwanPaoHandPersistenceValue {
    /** 大三元。 */
    BIG_DRAGONS,

    /** 大四喜。 */
    BIG_WINDS,
}

/**
 * 轉成 persistence DTO。
 *
 * @return 轉換後的結果。
 */
fun TaiwanPaoLiability.toPersistenceDto(): TaiwanPaoLiabilityPersistenceDto = TaiwanPaoLiabilityPersistenceDto(
    hand = when (hand) {
        TaiwanPaoHand.BIG_DRAGONS -> TaiwanPaoHandPersistenceValue.BIG_DRAGONS
        TaiwanPaoHand.BIG_WINDS -> TaiwanPaoHandPersistenceValue.BIG_WINDS
    },
    direction = direction.toPersistenceDto(),
)

/**
 * 還原成包牌責任。
 *
 * @return 轉換後的結果。
 */
fun TaiwanPaoLiabilityPersistenceDto.toDomain(): TaiwanPaoLiability = TaiwanPaoLiability(
    hand = when (hand) {
        TaiwanPaoHandPersistenceValue.BIG_DRAGONS -> TaiwanPaoHand.BIG_DRAGONS
        TaiwanPaoHandPersistenceValue.BIG_WINDS -> TaiwanPaoHand.BIG_WINDS
    },
    direction = direction.toDomain(),
)
