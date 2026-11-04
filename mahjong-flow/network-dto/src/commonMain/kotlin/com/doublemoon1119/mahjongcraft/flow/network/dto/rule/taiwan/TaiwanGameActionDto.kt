package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameActionDto
import kotlinx.serialization.Serializable

/** 台灣麻將擴充動作的網路 DTO。 */
@Serializable
sealed interface TaiwanGameActionDto : ExtensionGameActionDto {
    /** 公開花牌並補牌。 */
    @Serializable
    data object RevealFlower : TaiwanGameActionDto

    /** 打出一張牌並宣告聽牌。 */
    @Serializable
    data object DeclareReady : TaiwanGameActionDto
}
