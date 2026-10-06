package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 流局時玩家結算身分的用語。
 *
 * @property beneficiaryTranslationKey 在這次流局中得分的玩家，例如日麻一般荒牌流局的「聽牌」。
 * @property othersTranslationKey 其他玩家，例如日麻一般荒牌流局的「未聽」。
 */
data class ExhaustiveDrawSettlementStatusLabels(
    val beneficiaryTranslationKey: String,
    val othersTranslationKey: String,
) {
    init {
        require(beneficiaryTranslationKey.isNotBlank() && othersTranslationKey.isNotBlank()) { "Settlement status translation keys must not be blank" }
    }
}

/** 將完整流局原因 ID 映射至 Minecraft translation key，以及這種流局的玩家結算身分用語。 */
interface ExhaustiveDrawReasonDisplayNameRegistry {
    /** 目前已登記名稱或結算身分用語的流局原因 ID 快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 全部已註冊的完整 reason ID。 */
    val reasonIds: Set<String>

    /** 登記 reason ID 的本地化名稱。 */
    fun register(reasonId: String, translationKey: String)

    /** 查詢本地化名稱；未知 ID 回傳 null。 */
    fun find(reasonId: String): String?

    /** 登記這種流局的玩家結算身分用語；重複 ID 會失敗。 */
    fun registerSettlementStatusLabels(
        reasonId: String,
        labels: ExhaustiveDrawSettlementStatusLabels,
    )

    /** 查詢這種流局的玩家結算身分用語；未登記時回傳 null，呈現端改用通用的「受益」用語。 */
    fun findSettlementStatusLabels(reasonId: String): ExhaustiveDrawSettlementStatusLabels?

    /** 凍結 registry。 */
    fun freeze()
}

/** [ExhaustiveDrawReasonDisplayNameRegistry] 的記憶體實作。 */
class ExhaustiveDrawReasonDisplayNameRegistryImpl : ExhaustiveDrawReasonDisplayNameRegistry {
    private val translations = mutableMapOf<String, String>()
    private val statusLabels = mutableMapOf<String, ExhaustiveDrawSettlementStatusLabels>()
    override var isFrozen: Boolean = false
        private set
    override val reasonIds: Set<String> get() = translations.keys
    override val registrationKeys: Set<String> get() = translations.keys + statusLabels.keys

    override fun register(reasonId: String, translationKey: String) {
        check(!isFrozen) { "Exhaustive-draw reason display-name registry is frozen" }
        NamespacedId.requireValid(reasonId) { "Exhaustive-draw reason ID must be namespaced: $reasonId" }
        require(translationKey.isNotBlank()) { "Translation key must not be blank" }
        require(translations.putIfAbsent(reasonId, translationKey) == null) { "Duplicate exhaustive-draw reason: $reasonId" }
    }

    override fun find(reasonId: String): String? = translations[reasonId]

    override fun registerSettlementStatusLabels(
        reasonId: String,
        labels: ExhaustiveDrawSettlementStatusLabels,
    ) {
        check(!isFrozen) { "Exhaustive-draw reason display-name registry is frozen" }
        NamespacedId.requireValid(reasonId) { "Exhaustive-draw reason ID must be namespaced: $reasonId" }
        require(statusLabels.putIfAbsent(reasonId, labels) == null) { "Duplicate exhaustive-draw settlement status labels: $reasonId" }
    }

    override fun findSettlementStatusLabels(reasonId: String): ExhaustiveDrawSettlementStatusLabels? = statusLabels[reasonId]

    override fun freeze() {
        isFrozen = true
    }
}
