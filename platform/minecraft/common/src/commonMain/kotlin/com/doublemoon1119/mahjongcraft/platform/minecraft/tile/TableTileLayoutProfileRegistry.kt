package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TableTileLayoutProfileRegistrar

/** 依規則模組識別碼查詢桌面牌位配置；擴充只透過 [TableTileLayoutProfileRegistrar] 登記。 */
interface TableTileLayoutProfileRegistry : TableTileLayoutProfileRegistrar {
    /**
     * 取得指定規則的桌面牌位配置。
     *
     * @param ruleModuleId 規則模組識別碼。
     * @return 已登記的配置；未登記時為 [TableTileLayoutProfile.STANDARD]。
     */
    fun profileFor(ruleModuleId: String): TableTileLayoutProfile

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
