package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSource
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.platform.fabric.client.character.ClientAiCharacterPreviewRegistry

/** 客戶端採用相同的登記、來源錯誤回報與成功後凍結流程。 */
internal object FabricMahjongClientExtensionBootstrap {
    /** 第三方客戶端入口，不由 dedicated server 掃描。 */
    const val ENTRYPOINT: String = "mahjongcraft:client_extension"

    /**
     * 依序登記內建與第三方客戶端能力，任何登記錯誤都停止初始化。
     *
     * @param extensions 第三方入口，沿用 loader 提供順序。
     * @param registry 客戶端自己的登記表。
     * @param builtIn 不屬於特定規則的內建能力。
     * @param failurePolicy 取消與致命錯誤的傳遞規則。
     * @return 依內建及第三方來源區分的登記增量。
     */
    fun registerAndFreeze(
        extensions: Iterable<FabricMahjongClientExtension>,
        registry: ClientAiCharacterPreviewRegistry,
        builtIn: FabricMahjongClientExtension,
        failurePolicy: ExtensionFailurePolicy,
    ): List<ExtensionRegistrationSource> {
        val sources = mutableListOf<ExtensionRegistrationSource>()
        var before = registry.registrationSnapshot()
        val ids = mutableSetOf<String>()
        for (extension in listOf(builtIn) + extensions) {
            try {
                NamespacedId.requireValid(extension.id) { "Invalid client extension ID" }
                require(ids.add(extension.id)) { "Duplicate client extension ID: ${extension.id}" }
                extension.registerCharacterPreviews(registry)
                val after = registry.registrationSnapshot()
                sources += ExtensionRegistrationSource(if (extension === builtIn) null else extension.id, before.additionsSince(after))
                before = after
            } catch (cause: Throwable) {
                if (!failurePolicy.isIsolatable(cause)) throw cause
                throw FabricMahjongExtensionRegistrationException(extension.id, cause)
            }
        }
        registry.freeze()
        return sources
    }
}
