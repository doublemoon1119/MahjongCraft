package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterPreviewRegistrar

/**
 * 獨立的客戶端擴充入口；以 mahjongcraft:client_extension entrypoint 提供，不由伺服器掃描。
 * 需要原生客戶端類別的工廠留在此入口，不能放入共用或伺服器的擴充初始化。
 */
interface FabricMahjongClientExtension {
    /** 完整擴充識別碼，供重複檢查與來源診斷。 */
    val id: String

    /**
     * 登記此客戶端可建立的模型預覽。
     *
     * @param registry 初始化期間的寫入入口。
     */
    fun registerCharacterPreviews(registry: FabricAiCharacterPreviewRegistrar)
}
