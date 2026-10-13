package com.doublemoon1119.mahjongcraft.platform.minecraft.room

/** 依 priority 與 provider ID 解析房間成員外觀的凍結式 registry。 */
interface RoomMemberAppearanceSourceRegistry {
    /** 目前已登記 provider ID 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記完整描述；凍結後或身分重複時拒絕，不提前呼叫來源。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: RoomMemberAppearanceSourceDefinition)

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
