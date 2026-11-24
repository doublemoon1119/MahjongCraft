package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證回放擺不出牌桌時顯示的說明。 */
class HistoryReplaySceneNoticeTest {
    /** 沒裝規則時直接說明，不等規則設定讀取。 */
    @Test
    fun `a missing rule is explained before anything else`() {
        assertEquals(MinecraftHistoryScreenKeys.REPLAY_RULE_NOT_INSTALLED, key(ruleInstalled = false, rulesStatus = HistoryBrowseStatus.Loading))
        // 規則設定讀到了卻換不成規則模組，同樣是這台電腦用不了這套規則。
        assertEquals(MinecraftHistoryScreenKeys.REPLAY_RULE_NOT_INSTALLED, key(rulesStatus = HistoryBrowseStatus.Ready))
    }

    /** 規則設定讀取中顯示讀取中，讀取失敗顯示失敗原因。 */
    @Test
    fun `rule settings loading and failures`() {
        assertEquals(MinecraftHistoryScreenKeys.LOADING, key(rulesStatus = HistoryBrowseStatus.Loading))
        assertEquals(MinecraftHistoryScreenKeys.LOADING, key(rulesStatus = null))
        val failure = HistoryBrowseFailure.entries.first()
        assertEquals(failure.translationKey, key(rulesStatus = HistoryBrowseStatus.Failed(failure)))
    }

    /** 規則可用後，缺少擺牌附註與擺牌失敗各有自己的說明。 */
    @Test
    fun `missing notes and arrangement failures`() {
        assertEquals(MinecraftHistoryScreenKeys.REPLAY_UNAVAILABLE, key(rulesStatus = HistoryBrowseStatus.Ready, moduleResolved = true, hasNotes = false))
        assertEquals(MinecraftHistoryScreenKeys.REPLAY_ARRANGEMENT_FAILED, key(rulesStatus = HistoryBrowseStatus.Ready, moduleResolved = true, hasNotes = true))
    }

    /**
     * 選出說明。
     *
     * @param ruleInstalled 是否裝有規則。
     * @param rulesStatus 規則設定的讀取狀態。
     * @param moduleResolved 是否已取得規則模組。
     * @param hasNotes 是否附有擺牌附註。
     * @return 說明的翻譯鍵。
     */
    private fun key(
        ruleInstalled: Boolean = true,
        rulesStatus: HistoryBrowseStatus?,
        moduleResolved: Boolean = false,
        hasNotes: Boolean = true,
    ): String = HistoryReplaySceneNotice.key(ruleInstalled = ruleInstalled, rulesStatus = rulesStatus, moduleResolved = moduleResolved, hasNotes = hasNotes)
}
