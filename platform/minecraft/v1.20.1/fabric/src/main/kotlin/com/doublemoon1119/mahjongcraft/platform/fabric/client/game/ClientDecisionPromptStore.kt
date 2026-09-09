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

    /** 已由玩家明確點擊某個帶有選牌需求的動作、正等待點擊實體牌的 decision key。 */
    private var tileSelectionDecisionKey: String? = null

    /** [tileSelectionDecisionKey] 對應的動作 token，供實體牌 renderer 查詢合法候選牌。 */
    private var tileSelectionActionToken: String? = null

    /** 套用指定遊戲的 prompt；null 表示該決策只有精簡倒數。 */
    fun apply(gameId: Uuid, prompt: PlayerDecisionPromptDto?) {
        if (this.prompt?.decisionKey != prompt?.decisionKey) endTileSelection()
        this.gameId = gameId
        this.prompt = prompt
    }

    /** 玩家明確點擊帶有選牌需求的動作卡片後，啟用該動作合法候選牌高亮。 */
    fun beginTileSelection(decisionKey: String, actionToken: String) {
        if (prompt?.decisionKey == decisionKey) {
            tileSelectionDecisionKey = decisionKey
            tileSelectionActionToken = actionToken
        }
    }

    /** 選牌完成或情境失效後清除選取狀態。 */
    fun endTileSelection() {
        tileSelectionDecisionKey = null
        tileSelectionActionToken = null
    }

    /** 目前是否正在為某個動作選取實體牌。 */
    fun isTileSelectionActive(): Boolean = prompt?.decisionKey == tileSelectionDecisionKey

    /** 目前選牌動作的合法候選牌 ID；未進入選牌模式或找不到對應動作時回傳空清單。 */
    fun activeTileSelectionEligibleTileIds(): List<String> {
        val token = tileSelectionActionToken.takeIf { isTileSelectionActive() } ?: return emptyList()
        return prompt?.actions?.firstOrNull { it.token == token }?.tileSelection?.eligibleTileIds.orEmpty()
    }

    /** 只清除指定遊戲，避免較舊停止封包清掉另一場狀態。 */
    fun stop(gameId: Uuid) {
        if (this.gameId == gameId) clear()
    }

    /** 清除離線或失去決策權後不再有效的資料。 */
    fun clear() {
        gameId = null
        prompt = null
        endTileSelection()
    }
}
