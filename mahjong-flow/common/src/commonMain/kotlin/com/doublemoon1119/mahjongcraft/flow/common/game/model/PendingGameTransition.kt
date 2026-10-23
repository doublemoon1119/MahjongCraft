package com.doublemoon1119.mahjongcraft.flow.common.game.model

/**
 * 呈現結束後尚待完成的權威遊戲流程。
 *
 * 使用 sealed interface 讓後續需要攜帶流程參數時可新增 `data class` 分支；呈現用的役種、
 * 贏家或展示理由不屬於權威流程參數，不應放在這裡。
 */
sealed interface PendingGameTransition {
    /** 本局已結算，待呈現結束後進入下一局或判定終局。 */
    data object AdvanceRound : PendingGameTransition

    /** 牌山已摸完、一般流局結算前的規則選擇已完成，待進行一般流局結算。 */
    data object SettleExhaustiveDraw : PendingGameTransition

    /** 整場對局已結束，待呈現結束後返回房間的持久化意圖。 */
    data object ReturnToRoom : PendingGameTransition
}
