package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension

import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSource
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** 將內建項目與第三方原生整合 extension 登記至 runtime registry，完成後統一凍結。 */
internal object NativeMahjongExtensionBootstrap {
    /**
     * 依固定順序執行內建 extension 與第三方 extension 的回呼，全部成功後凍結 registry。
     *
     * @param extensions 依序執行登記回呼的原生整合 extension。
     * @param registries extension 使用並於成功後凍結的原生整合 registry 集合。
     * @param builtIn 不屬於任何規則的內建原生整合 extension。
     * @param failurePolicy 判斷登記回呼丟出的失敗能否包成帶擴充識別碼的註冊例外；無法隔離的失敗原樣往外傳。
     * @return 依來源分組的登記資訊；內建登記的來源識別碼為 null。
     * @throws NativeMahjongExtensionRegistrationException 若任一 extension 登記失敗。
     */
    fun registerAndFreeze(
        extensions: Iterable<NativeMahjongExtension>,
        registries: NativeExtensionRegistries,
        builtIn: NativeMahjongExtension,
        failurePolicy: ExtensionFailurePolicy,
    ): List<ExtensionRegistrationSource> {
        val registeredExtensionIds = mutableSetOf<String>()
        registerCallbacks(builtIn, registries, registeredExtensionIds, failurePolicy)
        var snapshot = registries.registrationSnapshot()
        val sources = mutableListOf(ExtensionRegistrationSource(extensionId = null, categories = snapshot.toCategories()))
        extensions.forEach { extension ->
            registerCallbacks(extension, registries, registeredExtensionIds, failurePolicy)
            val after = registries.registrationSnapshot()
            sources += ExtensionRegistrationSource(extensionId = extension.id, categories = snapshot.additionsSince(after))
            snapshot = after
        }
        registries.freezeAll()
        return sources
    }

    /**
     * 驗證 extension 識別碼、拒絕重複後依固定順序執行原生整合登記回呼。
     *
     * @param extension 要執行登記回呼的 extension。
     * @param registries 提供給 extension 回呼使用的原生整合 registry 集合。
     * @param registeredExtensionIds 已使用的 extension 識別碼集合。
     * @param failurePolicy 判斷登記回呼丟出的失敗能否包成帶擴充識別碼的註冊例外；無法隔離的失敗原樣往外傳。
     */
    private fun registerCallbacks(
        extension: NativeMahjongExtension,
        registries: NativeExtensionRegistries,
        registeredExtensionIds: MutableSet<String>,
        failurePolicy: ExtensionFailurePolicy,
    ) {
        try {
            NamespacedId.requireValid(extension.id) { "Native Mahjong extension ID must be namespaced: ${extension.id}" }
            require(registeredExtensionIds.add(extension.id)) {
                "Duplicate native Mahjong extension id: ${extension.id}"
            }
            extension.registerTablePropKinds(registries.tablePropKindRegistry)
            extension.registerSeatProviders(registries.seatProviderRegistry)
            extension.registerAiCharacterCapabilities(registries.characterCapabilityRegistry)
            extension.registerSeatPlacements(registries.seatPlacementRegistry)
            extension.registerRoomParticipants(registries.roomParticipantRegistry)
        } catch (cause: Throwable) {
            if (!failurePolicy.isIsolatable(cause)) throw cause
            throw NativeMahjongExtensionRegistrationException(extension.id, cause)
        }
    }
}
