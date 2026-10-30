package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.table.TableState
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
     * 依觀察者的讀取權裁切指定牌張的牌面、外觀與公開特徵，與 [snapshotFor] 使用同一份解算。
     *
     * 只回傳讀取者看得到的牌；沒有資格觀察本局時回傳空集合。
     *
     * @param game 包含完整桌況與流程設定的權威遊戲。
     * @param observerId 讀取者識別碼。
     * @param tileIds 要讀取的牌張。
     * @return 讀取者可知的牌面，依牌張識別碼索引。
     */
    fun tileFacesFor(
        game: Game,
        observerId: Uuid,
        tileIds: Collection<Uuid>,
    ): Map<Uuid, TileFaceView>

    /**
     * 以公共讀取者裁切指定牌張，只回傳本局所有合法觀察者都看得到的牌面。
     *
     * 用於全桌共用的呈現，例如和牌展示；不借用任何一位玩家的讀取權。
     *
     * @param tableState 目前權威桌況。
     * @param tileIds 要讀取的牌張。
     * @return 已公開的牌面，依牌張識別碼索引。
     */
    fun publicTileFaces(tableState: TableState, tileIds: Collection<Uuid>): Map<Uuid, TileFaceView>

    /**
     * 產生只向本人公開輸入與提交內容的規則選擇快照；不透露其他玩家是否被詢問或已提交。
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
            ownInputSpec = ruleDecision.inputSpecsByPlayerId[observerId],
            ownSubmission = ruleDecision.submissionsByPlayerId[observerId],
        )
    }
}
