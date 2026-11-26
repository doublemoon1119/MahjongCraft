package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import kotlinx.serialization.Serializable

/** 台灣麻將擴充動作的 persistence payload。 */
@Serializable
sealed interface TaiwanGameActionPersistenceDto {
    /** 公開花牌並補牌。 */
    @Serializable
    data object RevealFlower : TaiwanGameActionPersistenceDto

    /** 打出一張牌並宣告聽牌。 */
    @Serializable
    data object DeclareReady : TaiwanGameActionPersistenceDto
}
