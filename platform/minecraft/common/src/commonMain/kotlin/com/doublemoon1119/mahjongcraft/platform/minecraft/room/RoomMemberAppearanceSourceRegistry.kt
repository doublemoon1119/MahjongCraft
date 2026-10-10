package com.doublemoon1119.mahjongcraft.platform.minecraft.room

/** 依 priority 與 provider ID 解析房間成員外觀的凍結式 registry。 */
interface RoomMemberAppearanceSourceRegistry {
    /** 目前已登記 provider ID 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 全部已登記的 provider ID。 */
    fun register(providerId: String, priority: Int = 0, provider: RoomMemberAppearanceSourceProvider)

    /** 解析成員外觀來源。 */
    fun resolve(context: RoomMemberAppearanceContext): RoomMemberAppearanceSource

    /** 凍結 registry。 */
    fun freeze()
}
