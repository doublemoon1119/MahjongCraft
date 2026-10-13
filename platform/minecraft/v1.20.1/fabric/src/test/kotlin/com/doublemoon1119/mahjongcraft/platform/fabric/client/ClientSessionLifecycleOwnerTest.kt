package com.doublemoon1119.mahjongcraft.platform.fabric.client

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證新連線及斷線使用相同清理清單，不依賴事件成對抵達。 */
class ClientSessionLifecycleOwnerTest {
    /** 連續 JOIN 沒有 DISCONNECT，仍逐次清除每項暫態且不漏掉遊戲與決策資料。 */
    @Test
    fun `repeated joins clear every transient resource once per connection`() {
        val calls = mutableListOf<String>()
        val owner = owner(calls)
        owner.beginSession()
        assertEquals(RESOURCES, calls)
        calls.clear()
        owner.beginSession()
        assertEquals(RESOURCES, calls)
    }

    /** 斷線清理與開新連線完全一致，共用設定不會被清理兩次。 */
    @Test
    fun `disconnect uses the same ordered reset list without duplicate settings reset`() {
        val calls = mutableListOf<String>()
        val owner = owner(calls)
        owner.endSession()
        assertEquals(RESOURCES, calls)
        calls.clear()
        owner.beginSession()
        assertEquals(RESOURCES, calls)
    }

    /**
     * 建立只記錄呼叫順序的清理入口。
     *
     * @param calls 收集每次清理的資源名稱。
     * @return 不依賴原生客戶端的清理入口。
     */
    private fun owner(calls: MutableList<String>): ClientSessionLifecycleOwner = ClientSessionLifecycleOwner(
        clearHistoryQueries = { calls += "history-and-settings" },
        clearHistoryArchiveStatus = { calls += "archive" },
        clearProfiles = { calls += "profiles" },
        clearHistoryChatEntries = { calls += "chat" },
        clearGameState = { calls += "game" },
        clearAutomaticControl = { calls += "automatic" },
        clearDecisionTimer = { calls += "timer" },
        clearDecisionPrompt = { calls += "prompt" },
        clearDecisionHud = { calls += "hud" },
    )

    /** 預期的單次清理順序；不包含持久化偏好。 */
    private companion object {
        /** 每個資源僅出現一次。 */
        val RESOURCES = listOf("history-and-settings", "archive", "profiles", "chat", "game", "automatic", "timer", "prompt", "hud")
    }
}
