package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfileDefinition

/** 擴充登記桌面牌位配置時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface TableTileLayoutProfileRegistrar {
    /** 目前已登記的規則模組識別碼快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記一個規則的桌面牌位配置；凍結後或同一規則重複登記時拒絕。
     *
     * @param definition 規則模組識別碼與配置。
     */
    fun register(definition: TableTileLayoutProfileDefinition)
}
