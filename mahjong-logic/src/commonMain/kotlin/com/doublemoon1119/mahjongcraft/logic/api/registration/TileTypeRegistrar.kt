package com.doublemoon1119.mahjongcraft.logic.api.registration

import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeDefinition

/** 擴充登記牌種時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface TileTypeRegistrar {
    /** registry 是否已禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 註冊一種新的擴充牌。
     *
     * @throws IllegalStateException 當 registry 已凍結時拋出。
     * @throws IllegalArgumentException 當 [definition] 的 ID 已存在時拋出。
     */
    fun register(definition: TileTypeDefinition)
}
