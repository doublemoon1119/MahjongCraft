package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.prop

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.TablePropKindRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.tableprop.TablePropKind
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropKindDefinition

/** 依 [TablePropKind.id] 登記規則桌面物件種類；擴充只透過 [TablePropKindRegistrar] 登記。 */
interface TablePropKindRegistry : TablePropKindRegistrar {

    /** 登記當下擷取的共用種類定義快照。 */
    val definitions: Map<String, TablePropKindDefinition>

    /** 目前已登記的全部種類，依登記順序排列。 */
    val kinds: List<TablePropKind>

    /**
     * 查詢指定種類；未登記時回傳 null。
     *
     * @param kindId 種類的命名空間識別碼。
     * @return 相符種類；未登記時為 null。
     */
    fun find(kindId: String): TablePropKind?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
