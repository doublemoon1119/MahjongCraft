package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

/**
 * 規則或自動操作違反契約時中斷當次流程，不把矛盾當成一般非法請求。
 *
 * 已完成的權威寫入不會回滾；協調器會依目前仍屬同一場次的狀態中止對局。
 *
 * @property reasonId 規則中立的終止原因。
 * @param diagnostic 僅供伺服器診斷的說明，不直接傳給參與者。
 */
internal class RuleContractViolationException(
    val reasonId: String,
    diagnostic: String,
) : IllegalStateException(diagnostic)
