package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/** 一局完成結果分類的 persistence DTO。 */
@Serializable
enum class RoundCompletionClassificationPersistenceDto {
    /** 胡牌或胡牌等價結果。 */
    WIN,

    /** 一般荒牌流局。 */
    EXHAUSTIVE_DRAW,

    /** 途中流局。 */
    ABORTIVE_DRAW,

    /** 第三方規則結果。 */
    EXTENSION,
}
