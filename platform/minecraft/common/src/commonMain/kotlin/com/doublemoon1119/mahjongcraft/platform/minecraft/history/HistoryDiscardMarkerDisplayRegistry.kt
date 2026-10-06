package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 歷史牌河上一種公開標記的呈現方式。
 *
 * @property labelTranslationKey 滑鼠提示中顯示的標記名稱。
 * @property sideways 帶有這個標記的牌是否橫擺。
 */
data class HistoryDiscardMarkerDisplay(
    val labelTranslationKey: String,
    val sideways: Boolean = false,
) {
    init {
        require(labelTranslationKey.isNotBlank()) { "History discard marker label must not be blank" }
    }
}

/** 將歷史牌河公開標記 ID 映射至呈現方式；沒有登記的標記不橫擺，提示直接顯示 ID。 */
interface HistoryDiscardMarkerDisplayRegistry {
    /** 目前已登記標記 ID 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 登記一種標記的呈現方式；重複 ID 會失敗。 */
    fun register(
        markerId: String,
        display: HistoryDiscardMarkerDisplay,
    )

    /** 查詢標記的呈現方式；未登記時回傳 null。 */
    fun find(markerId: String): HistoryDiscardMarkerDisplay?

    /** 凍結 registry。 */
    fun freeze()
}

/** [HistoryDiscardMarkerDisplayRegistry] 的記憶體實作。 */
class HistoryDiscardMarkerDisplayRegistryImpl : HistoryDiscardMarkerDisplayRegistry {
    private val displays = mutableMapOf<String, HistoryDiscardMarkerDisplay>()
    override var isFrozen: Boolean = false
        private set
    override val registrationKeys: Set<String> get() = displays.keys.toSet()

    override fun register(
        markerId: String,
        display: HistoryDiscardMarkerDisplay,
    ) {
        check(!isFrozen) { "History discard marker display registry is frozen" }
        NamespacedId.requireValid(markerId) { "History discard marker ID must be namespaced: $markerId" }
        require(displays.putIfAbsent(markerId, display) == null) { "Duplicate history discard marker display: $markerId" }
    }

    override fun find(markerId: String): HistoryDiscardMarkerDisplay? = displays[markerId]

    override fun freeze() {
        isFrozen = true
    }
}
