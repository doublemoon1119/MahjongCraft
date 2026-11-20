package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricTablePropPreviewRegistrar

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

    /**
     * 登記此客戶端可畫出的桌上物件，例如牌譜回放中當時桌上的物件；沒有登記的種類在客戶端場景中不畫。
     *
     * @param registry 初始化期間的寫入入口。
     */
    fun registerTablePropPreviews(registry: FabricTablePropPreviewRegistrar) = Unit
}
