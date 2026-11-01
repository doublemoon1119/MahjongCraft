package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterCapabilityRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricSeatProviderRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricTablePropKindRegistrar

/**
 * 第三方 mod 登記 Fabric 1.20.1 專屬整合的契約，例如需要自己的 entity 才能呈現的規則桌面物件種類。
 *
 * 發現方式與 `MinecraftMahjongExtension` 相同：同一個類別同時實作 `MahjongExtension` 與這個介面即可，
 * loader 從 `MahjongExtension` entrypoint 的掃描結果中篩選出有實作這個介面的部分。
 */
interface FabricMahjongExtension {
    /** 第三方 extension 的穩定識別字串，用於診斷註冊錯誤。 */
    val id: String

    /**
     * 登記規則桌面物件種類，說明描述裡的種類識別碼要生成哪種 entity、如何找回既有的 entity。
     *
     * 預設不登記；只使用內建種類的 extension 不必實作。
     *
     * @param registry Fabric 桌面物件種類 registry。
     */
    fun registerTablePropKinds(registry: FabricTablePropKindRegistrar) = Unit

    /**
     * 登記此 Fabric 版本可解析的座位 provider。
     *
     * 預設不登記；只使用內建座位 provider 的 extension 不必實作。
     *
     * @param registry Fabric 座位 provider registry。
     */
    fun registerSeatProviders(registry: FabricSeatProviderRegistrar) = Unit

    /**
     * 登記世界角色能力；第三方不需要接觸 MahjongCraft 的依賴注入。
     *
     * @param registry 初始化期間可寫入的角色能力登記入口。
     */
    fun registerAiCharacterCapabilities(registry: FabricAiCharacterCapabilityRegistrar) = Unit
}
