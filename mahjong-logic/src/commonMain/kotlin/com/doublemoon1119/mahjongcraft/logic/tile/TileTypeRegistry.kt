package com.doublemoon1119.mahjongcraft.logic.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId

/** 管理內建與第三方擴充牌種定義的 runtime registry。 */
interface TileTypeRegistry {
    /** registry 是否已禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 註冊一種新的擴充牌。
     *
     * @throws IllegalStateException 當 registry 已凍結時拋出。
     * @throws IllegalArgumentException 當 [definition] 的 ID 已存在時拋出。
     */
    fun register(definition: TileTypeDefinition)

    /** 凍結 registry；後續呼叫 [register] 將失敗。 */
    fun freeze()

    /** 依 [id] 尋找牌種定義；尚未註冊時回傳 null。 */
    fun find(id: TileTypeId): TileTypeDefinition?

    /**
     * 依 [id] 取得牌種定義。
     *
     * @throws IllegalStateException 當 [id] 尚未註冊時拋出。
     */
    fun require(id: TileTypeId): TileTypeDefinition

    /** 取得依註冊順序排列的全部牌種定義。 */
    fun getAll(): List<TileTypeDefinition>
}
