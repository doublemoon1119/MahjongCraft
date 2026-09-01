package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 保存目前只對本機玩家公開的權威操作 prompt。 */
@Single
class ClientDecisionPromptStore {
    /** Prompt 所屬遊戲。 */
    var gameId: Uuid? = null
        private set

    /** 最後同步且仍有效的 prompt。 */
    var prompt: PlayerDecisionPromptDto? = null
        private set

    /** 已由玩家明確選擇立直、正等待點擊宣告牌的 decision key。 */
    var riichiSelectionDecisionKey: String? = null
        private set

    /** 套用指定遊戲的 prompt；null 表示該決策只有精簡倒數。 */
    fun apply(gameId: Uuid, prompt: PlayerDecisionPromptDto?) {
        if (this.prompt?.decisionKey != prompt?.decisionKey) riichiSelectionDecisionKey = null
        this.gameId = gameId
        this.prompt = prompt
    }

    /** 玩家明確點擊立直後才啟用宣告牌高亮。 */
    fun beginRiichiSelection(decisionKey: String) {
        if (prompt?.decisionKey == decisionKey) riichiSelectionDecisionKey = decisionKey
    }

    /** 目前是否正在選擇立直宣告牌。 */
    fun isRiichiSelectionActive(): Boolean = prompt?.decisionKey == riichiSelectionDecisionKey

    /** 只清除指定遊戲，避免較舊停止封包清掉另一場狀態。 */
    fun stop(gameId: Uuid) {
        if (this.gameId == gameId) clear()
    }

    /** 清除離線或失去決策權後不再有效的資料。 */
    fun clear() {
        gameId = null
        prompt = null
        riichiSelectionDecisionKey = null
    }
}
