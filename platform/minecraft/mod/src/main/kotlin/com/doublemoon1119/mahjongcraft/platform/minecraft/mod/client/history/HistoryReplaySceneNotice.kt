package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys

/** 回放取得牌面後仍擺不出牌桌時，場景中顯示的說明。 */
internal object HistoryReplaySceneNotice {
    /**
     * 選出說明：先看規則有沒有安裝與讀取，再看牌譜資料與擺牌結果。
     *
     * @param ruleInstalled 這台電腦是否裝有這場對局的規則。
     * @param rulesStatus 這場對局規則設定的讀取狀態。還沒開始讀取時為 null。
     * @param moduleResolved 是否已由規則設定取得規則模組。
     * @param hasNotes 牌面是否附有擺牌附註。
     * @return 說明的翻譯鍵。規則設定還在讀取時為讀取中。
     */
    fun key(
        ruleInstalled: Boolean,
        rulesStatus: HistoryBrowseStatus?,
        moduleResolved: Boolean,
        hasNotes: Boolean,
    ): String = when {
        !ruleInstalled -> MinecraftHistoryScreenKeys.REPLAY_RULE_NOT_INSTALLED
        rulesStatus is HistoryBrowseStatus.Failed -> rulesStatus.reason.translationKey
        // 規則設定已讀到卻換不成規則模組，代表這台電腦無法使用這套規則。
        !moduleResolved && rulesStatus == HistoryBrowseStatus.Ready -> MinecraftHistoryScreenKeys.REPLAY_RULE_NOT_INSTALLED
        !moduleResolved -> MinecraftHistoryScreenKeys.LOADING
        !hasNotes -> MinecraftHistoryScreenKeys.REPLAY_UNAVAILABLE
        else -> MinecraftHistoryScreenKeys.REPLAY_ARRANGEMENT_FAILED
    }
}
