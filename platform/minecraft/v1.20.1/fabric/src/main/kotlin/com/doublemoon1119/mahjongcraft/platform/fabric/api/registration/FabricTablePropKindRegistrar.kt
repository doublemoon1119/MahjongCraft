package com.doublemoon1119.mahjongcraft.platform.fabric.api.registration

import com.doublemoon1119.mahjongcraft.platform.fabric.api.tableprop.FabricTablePropKind

/** 擴充登記 Fabric 桌面物件種類時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface FabricTablePropKindRegistrar {
    /** 目前已登記的種類識別碼快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記一個種類。
     *
     * @param kind 要登記的 Fabric 桌面物件種類。
     */
    fun register(kind: FabricTablePropKind)
}
