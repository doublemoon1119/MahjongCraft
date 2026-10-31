package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RoomMemberAppearanceSourceRegistrar

/** 依 priority 與 provider ID 解析房間成員外觀的凍結式 registry；擴充只透過 [RoomMemberAppearanceSourceRegistrar] 登記。 */
interface RoomMemberAppearanceSourceRegistry : RoomMemberAppearanceSourceRegistrar {

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
