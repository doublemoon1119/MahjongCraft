package com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop

/** 依 [FabricTablePropKind.id] 登記 Fabric 端的規則桌面物件種類。 */
interface FabricTablePropKindRegistry {
    /** 目前已登記的種類識別碼快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 目前已登記的全部種類，依登記順序排列。 */
    val kinds: List<FabricTablePropKind>

    /** 登記一個種類。 */
    fun register(kind: FabricTablePropKind)

    /** 查詢指定種類；未登記時回傳 null。 */
    fun find(kindId: String): FabricTablePropKind?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
