package com.doublemoon1119.mahjongcraft.platform.fabric.api.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatProvider
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld

/** 登記 Fabric 原生座位 provider 的公開 registry。 */
interface FabricSeatProviderRegistry {
    /** 目前已登記的 provider 識別碼快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記一個座位 provider。
     *
     * @param id provider 的命名空間識別碼。
     * @param provider 解析 Fabric 世界與實體座位的 provider。
     * @param priority provider 的解析優先順序；數值較大者優先。
     */
    fun register(
        id: String,
        provider: SeatProvider<ServerWorld, Entity>,
        priority: Int = 0,
    )

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()

    /** 取得凍結後供座位協調器使用的 provider 快照。 */
    fun entries(): List<RegisteredSeatProvider<ServerWorld, Entity>>
}
