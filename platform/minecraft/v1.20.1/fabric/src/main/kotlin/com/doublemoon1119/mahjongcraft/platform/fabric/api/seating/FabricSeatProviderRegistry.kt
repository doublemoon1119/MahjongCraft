package com.doublemoon1119.mahjongcraft.platform.fabric.api.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld

/**
 * 擴充登記 Fabric 原生座位 provider 的公開 registry。
 *
 * 擴充只能登記與查看登記狀態；凍結與取得 provider 由 MahjongCraft 在擴充初始化完成後進行。
 */
interface FabricSeatProviderRegistry {
    /** 目前已登記的 provider 識別碼快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記一個座位 provider。
     *
     * @param definition 包含命名空間識別碼、解析能力與優先序的完整描述。
     */
    fun register(
        definition: RegisteredSeatProvider<ServerWorld, Entity>,
    )
}
