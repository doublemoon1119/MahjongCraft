package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

/**
 * 單步耗時拆解出的環節。
 *
 * 前三項在伺服器主執行緒上，加總就是單步耗時（單步從開始到結束的時間扣掉等待 AI 結果的時間）；[AI_DECISION] 在背景執行緒上，
 * 不計入單步耗時。歷史記錄無法分攤到同時進行的各步，改以每 tick 統計。
 */
enum class StressStepStage {
    /** 建立 AI 視角快照。 */
    AI_CONTEXT,

    /** 規則判斷、狀態提交與歷史記錄等其餘主執行緒流程：單步耗時扣掉建立 AI 視角快照與快照同步。 */
    RULES_AND_STATE,

    /** 為每位觀察者裁切可見快照。 */
    SNAPSHOT_SYNC,

    /** 策略在背景執行緒上的思考。 */
    AI_DECISION,
}
