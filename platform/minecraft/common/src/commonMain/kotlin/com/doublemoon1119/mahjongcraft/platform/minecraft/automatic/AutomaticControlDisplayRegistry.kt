package com.doublemoon1119.mahjongcraft.platform.minecraft.automatic

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.AutomaticControlDisplayRegistrar

/** 管理自動操作 Minecraft 顯示資料的凍結式 registry；擴充只透過 [AutomaticControlDisplayRegistrar] 登記。 */
interface AutomaticControlDisplayRegistry : AutomaticControlDisplayRegistrar {

    /**
     * 查詢指定 control ID 的顯示資料；未登記時回傳 `null`。
     *
     * @param controlId 自動操作的穩定 ID。
     * @return 已登記的顯示資料，或未登記時的 null。
     */
    fun find(controlId: String): AutomaticControlDisplay?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
