package com.doublemoon1119.mahjongcraft.platform.minecraft.room

/** 依 priority 與 provider ID 解析房間成員外觀的凍結式 registry。 */
interface RoomMemberAppearanceSourceRegistry {
    /** 目前已登記 provider ID 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記一個成員外觀提供者。
     *
     * @param providerId provider 的穩定 namespaced ID。
     * @param priority 解析優先順序；數值較大者先執行。
     * @param provider 依成員身分解析外觀來源的 provider。
     */
    fun register(providerId: String, priority: Int = 0, provider: RoomMemberAppearanceSourceProvider)

    /**
     * 依 provider 優先順序解析成員外觀來源；皆無來源時依成員是否為 AI 選擇預設來源。
     *
     * @param context 要解析的房間成員公開身分。
     * @return provider 提供的來源，或依 AI 身分選取的預設來源。
     */
    fun resolve(context: RoomMemberAppearanceContext): RoomMemberAppearanceSource

    /** 凍結 registry。 */
    fun freeze()
}
