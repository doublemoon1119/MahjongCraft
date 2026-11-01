package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import kotlinx.serialization.Serializable

/**
 * 已追蹤角色的公開互動標記，不代表玩家有權改動該角色或席位。
 *
 * @property entityId 原生角色 UUID 的字串表示。
 * @property bound 是否被 MahjongCraft 接管。
 */
@Serializable
data class AiCharacterTargetPayload(
    val entityId: String,
    val bound: Boolean,
)
