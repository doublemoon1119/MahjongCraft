package com.doublemoon1119.mahjongcraft.extension

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry

/**
 * 將平台發現的第三方 extension 登記至 runtime 實際使用的 registry，完成後凍結所有 registry。
 */
object MahjongExtensionRegistrar {
    /**
     * 依 [extensions] 順序執行註冊，並在全部成功後凍結 registry。
     *
     * @throws MahjongExtensionRegistrationException 若任一 extension 註冊失敗。
     */
    fun registerAndFreeze(
        extensions: Iterable<MahjongExtension>,
        moduleRegistry: MahjongModuleRegistry,
        networkRegistries: NetworkDtoRegistries,
        persistenceRegistries: PersistenceRegistries,
    ) {
        extensions.forEach { extension ->
            try {
                extension.registerRuleModules(moduleRegistry)
                extension.registerNetworkDtos(networkRegistries)
                extension.registerPersistenceDtos(persistenceRegistries)
            } catch (cause: Exception) {
                throw MahjongExtensionRegistrationException(extension.id, cause)
            }
        }

        moduleRegistry.freeze()
        networkRegistries.freeze()
        persistenceRegistries.freeze()
    }
}

/** 表示指定第三方 extension 無法完成 runtime registry 註冊。 */
class MahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register Mahjong extension: $extensionId", cause)
