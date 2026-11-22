package com.doublemoon1119.mahjongcraft.platform.fabric.api.registration

import com.doublemoon1119.mahjongcraft.platform.fabric.api.tableprop.TablePropPreviewDefinition

/** 只在客戶端初始化使用的桌上物件預覽登記入口，不在伺服器載入工廠。 */
interface TablePropPreviewRegistrar {
    /** 已登記種類識別碼的快照。 */
    val registrationKeys: Set<String>

    /** 是否已完成初始化。 */
    val isFrozen: Boolean

    /**
     * 登記一種桌上物件預覽，不立即建立實體。
     *
     * @param definition 自帶種類定義的預覽。
     */
    fun register(definition: TablePropPreviewDefinition)
}
