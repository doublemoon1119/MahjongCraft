package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.platform.fabric.client.CLIENT_COMMAND_ROOT
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 保存目前連線的有限聊天入口，不以對局保存完成作為開啟條件。
 *
 * @property transport 提供目前連線版本的歷史傳輸。
 */
@Single
class ClientHistoryChatEntryStore(private val transport: HistoryQueryTransport) {
    /** 僅本地建立的入口識別與所屬連線版本。 */
    private val entries = LinkedHashMap<String, Entry>()

    /**
     * 建立不包含玩家名稱或伺服器任意命令的聊天點擊命令。
     *
     * @param matchId 選用的對局識別；無效識別只開啟一般列表。
     * @return 使用本地隨機入口識別的 client 命令。
     */
    fun createCommand(matchId: String?): String {
        val validated = matchId?.let { runCatching { Uuid.parse(it).toString() }.getOrNull() }
        val token = Uuid.random().toString()
        entries[token] = Entry(validated, transport.sessionRevision.value)
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        return "/$CLIENT_COMMAND_ROOT history $CHAT_NODE $token"
    }

    /**
     * 只接受目前連線建立的入口；重複點擊交由開啟控制器合併。
     *
     * @param token 聊天命令的本地入口識別。
     * @param open 接受有效入口的畫面開啟 callback。
     * @return 是否找到目前連線的有效入口。
     */
    fun open(token: String, open: (String?) -> Unit): Boolean {
        val entry = entries[token] ?: return false
        if (entry.revision != transport.sessionRevision.value) return false
        open(entry.matchId)
        return true
    }

    /** 斷線時失效所有聊天入口，避免舊訊息查詢另一個伺服器。 */
    fun clear() = entries.clear()

    /**
     * 單一聊天入口的對局與連線身分。
     *
     * @property matchId 選用的有效對局 UUID。
     * @property revision 建立入口時的連線版本。
     */
    private data class Entry(val matchId: String?, val revision: Long)

    /** 聊天入口的固定命令與容量限制。 */
    companion object {
        /** 與一般畫面命令分離的本地聊天入口節點。 */
        const val CHAT_NODE = "chat_entry"

        /** 長時間連線最多保留的聊天入口數。 */
        private const val MAX_ENTRIES = 64
    }
}
