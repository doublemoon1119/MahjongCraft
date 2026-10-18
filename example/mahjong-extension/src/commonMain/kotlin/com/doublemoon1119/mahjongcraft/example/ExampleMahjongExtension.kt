package com.doublemoon1119.mahjongcraft.example

import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry

/** 平台無關的範例擴充入口；平台轉接層可繼承並補上自己的呈現與原生整合。 */
open class ExampleMahjongExtension : MahjongExtension {
    override val id: String = ExampleExtensionIds.EXTENSION

    /** 建置骨架不提供額外玩法，因此不向 [registry] 登記規則。 */
    override fun registerRuleModules(registry: MahjongModuleRegistry) = Unit

    /** 建置骨架沒有自訂資料型別，因此不向 [registries] 登記網路轉換器。 */
    override fun registerNetworkDtos(registries: NetworkDtoRegistries) = Unit

    /** 建置骨架沒有自訂保存資料，因此不向 [registries] 登記保存轉換器。 */
    override fun registerPersistenceDtos(registries: PersistenceRegistries) = Unit
}
