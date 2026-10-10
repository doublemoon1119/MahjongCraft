package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** 宣告式模板唯一允許使用的受控顯示值。 */
sealed interface PresentationValue {
    /**
     * 本地化文字。
     *
     * @property translationKey 本地化翻譯鍵。
     * @property arguments 已序列化的顯示參數。
     */
    data class TextValue(
        val translationKey: String,
        val arguments: List<String> = emptyList(),
    ) : PresentationValue

    /**
     * 單張牌面 asset。
     *
     * @property assetKey 牌面 asset key。
     */
    data class TileValue(
        val assetKey: String,
    ) : PresentationValue

    /**
     * 保留順序的多張牌面 asset。
     *
     * @property assetKeys 牌面 asset key。
     */
    data class TileListValue(
        val assetKeys: List<String>,
    ) : PresentationValue

    /**
     * 保留手牌、副露及牌背語意的多組牌面。
     *
     * @property groups 各組牌面 asset key。
     */
    data class TileGroupsValue(
        val groups: List<List<String>>,
    ) : PresentationValue

    /**
     * 玩家識別快照；renderer 可安全顯示 FACE 與名稱，不需要接觸 PlayerEntity。
     *
     * @property playerId 玩家 ID。
     * @property displayName 顯示名稱。
     * @property isAi 是否為 AI 玩家。
     */
    data class PlayerIdentityValue(
        val playerId: String,
        val displayName: String,
        val isAi: Boolean,
    ) : PresentationValue

    /**
     * 可逐項揭曉的文字條目。
     *
     * @property entries 欲顯示的條目。
     */
    data class EntryListValue(
        val entries: List<Entry>,
    ) : PresentationValue {
        /**
         * 單一條目的本地化名稱與右側值。
         *
         * @property translationKey 條目名稱的 translation key。
         * @property trailingText 右側的純文字值；與 [trailingTranslationKey] 不得同時提供。
         * @property trailingTranslationKey 右側值的 translation key。
         * @property trailingTranslationArgument [trailingTranslationKey] 的單一參數。
         * @property highlighted 是否以強調樣式顯示，例如日麻的役滿條目。
         */
        data class Entry(
            val translationKey: String,
            val trailingText: String = "",
            val trailingTranslationKey: String? = null,
            val trailingTranslationArgument: String? = null,
            val highlighted: Boolean = false,
        )
    }
}
