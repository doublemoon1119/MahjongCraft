package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.PlayerPortraitSourceRegistrar

/** 依 priority 與穩定 provider ID 解析玩家 portrait 的凍結式 registry；擴充只透過 [PlayerPortraitSourceRegistrar] 登記。 */
interface PlayerPortraitSourceRegistry : PlayerPortraitSourceRegistrar {
    /** 由已登記內容推得的登記識別碼。 */
    override val registrationKeys: Set<String> get() = providerIds.toSet()

    /** 目前已登記 provider ID 的集合。 */
    val providerIds: Set<String>

    /**
     * 依 provider 優先順序解析玩家 portrait；無 provider 能提供來源時回傳 null。
     *
     * @param context 要解析的玩家身分。
     * @return 第一個提供來源的 provider ID 與來源，或沒有來源時的 null。
     */
    fun resolve(context: PlayerPortraitSourceContext): ResolvedPlayerPortraitSource?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
