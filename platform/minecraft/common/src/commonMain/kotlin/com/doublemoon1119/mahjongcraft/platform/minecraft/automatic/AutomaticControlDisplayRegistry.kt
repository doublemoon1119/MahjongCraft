package com.doublemoon1119.mahjongcraft.platform.minecraft.automatic

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.AutomaticControlDisplayRegistrar

/** 管理自動操作 Minecraft 顯示資料的凍結式 registry；擴充只透過 [AutomaticControlDisplayRegistrar] 登記。 */
interface AutomaticControlDisplayRegistry : AutomaticControlDisplayRegistrar {

    /**
     * 查詢指定 control ID 的顯示資料；規則登記了自己的顯示時優先使用，否則使用預設。
     *
     * @param ruleModuleId 目前對局的規則模組 ID；不在對局中時為 null，只查預設。
     * @param controlId 自動操作的穩定 ID。
     * @return 已登記的顯示資料，或都未登記時的 null。
     */
    fun find(ruleModuleId: String?, controlId: String): AutomaticControlDisplay?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
