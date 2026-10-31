package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TablePropDescriberRegistrar

/** 依規則模組識別碼登記 [TablePropDescriber]；沒有登記的規則，桌上不會擺任何由規則描述的物件；擴充只透過 [TablePropDescriberRegistrar] 登記。 */
interface TablePropDescriberRegistry : TablePropDescriberRegistrar {

    /**
     * 查詢指定規則模組的描述；未登記時回傳 null。
     *
     * @param ruleModuleId 規則模組識別碼。
     * @return 已登記描述器；未登記時為 `null`。
     */
    fun find(ruleModuleId: String): TablePropDescriber?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
