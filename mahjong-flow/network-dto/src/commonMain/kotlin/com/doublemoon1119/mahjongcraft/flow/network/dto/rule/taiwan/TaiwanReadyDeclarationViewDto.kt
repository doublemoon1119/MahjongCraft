package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.network.dto.component.ComponentViewDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 公開的宣告聽牌。
 *
 * @property kind 宣告種類名稱。
 */
@Serializable
@SerialName("mahjongcraft:taiwan/player_view/ready_declaration")
data class TaiwanReadyDeclarationViewDto(
    val kind: String,
) : ComponentViewDto
