package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.uuid.Uuid

/** 根據權威遊戲狀態產生指定讀取者可見的遊戲快照。 */
interface GameVisibilityPolicy {
    /**
     * 產生已依參與者與旁觀設定裁切手牌資訊的快照。
     *
     * @param game 包含完整桌況與流程設定的權威遊戲。
     * @param observerId 欲取得快照的讀取者識別碼。
     * @return 指定讀取者可取得的遊戲快照。
     */
    fun snapshotFor(game: Game, observerId: Uuid): TableStateSnapshot

    /**
     * 產生只向本人公開輸入與提交內容的規則選擇快照。
     *
     * @param game 含有目前待處理選擇步驟的權威遊戲。
     * @param observerId 欲取得快照的讀取者識別碼。
     * @return 含該讀取者輸入規格與提交的快照；沒有選擇步驟時為 null。
     */
    fun ruleDecisionSnapshotFor(game: Game, observerId: Uuid): RuleDecisionSnapshot? {
        val ruleDecision = game.pendingRuleDecision ?: return null
        return RuleDecisionSnapshot(
            stepId = ruleDecision.stepId,
            stepIndex = ruleDecision.stepIndex,
            participantPlayerIds = ruleDecision.participantPlayerIds,
            completedPlayerIds = ruleDecision.completedPlayerIds,
            ownInputSpec = ruleDecision.inputSpecsByPlayerId[observerId],
            ownSubmission = ruleDecision.submissionsByPlayerId[observerId],
        )
    }
}
