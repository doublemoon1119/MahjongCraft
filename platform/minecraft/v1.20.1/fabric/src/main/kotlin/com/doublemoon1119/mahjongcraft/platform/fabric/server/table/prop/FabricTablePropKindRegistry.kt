package com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop

import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricTablePropKindRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.tableprop.FabricTablePropKind
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropKindDefinition

/** 依 [FabricTablePropKind.id] 登記 Fabric 端的規則桌面物件種類；擴充只透過 [FabricTablePropKindRegistrar] 登記。 */
interface FabricTablePropKindRegistry : FabricTablePropKindRegistrar {

    /** 登記當下擷取的共用種類定義快照。 */
    val definitions: Map<String, TablePropKindDefinition>

    /** 目前已登記的全部種類，依登記順序排列。 */
    val kinds: List<FabricTablePropKind>

    /**
     * 查詢指定種類；未登記時回傳 null。
     *
     * @param kindId 種類的命名空間識別碼。
     * @return 相符種類；未登記時為 null。
     */
    fun find(kindId: String): FabricTablePropKind?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
