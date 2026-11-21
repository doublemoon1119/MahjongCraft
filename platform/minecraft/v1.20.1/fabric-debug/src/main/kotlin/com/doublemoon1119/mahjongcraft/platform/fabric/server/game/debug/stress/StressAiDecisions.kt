package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionObserver
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * 壓力測試各輪共用的 AI 決策執行器，以及決策結果的分派。
 *
 * 所有輪次共用 [executor] 的名額：上一輪留下、不理會取消而尚未真正結束的策略工作，仍計入下一輪的上限。每局在建立時以 [register]
 * 登記建立它的那一輪的接收者，決策結果只依對局交給該接收者；對局識別碼在建立時決定，因此決策在開始時就屬於該輪。對局以
 * [unregister] 取消登記後，它之後才有結果的決策不交給任何一輪。
 *
 * @param createExecutor 以負責分派的接收者建立執行器。
 */
internal class StressAiDecisions(createExecutor: (AiDecisionObserver) -> AiDecisionExecutor) {
    /** 每局所屬那一輪的接收者。 */
    private val observers = ConcurrentHashMap<Uuid, AiDecisionObserver>()

    /** 各輪共用的執行器。 */
    val executor: AiDecisionExecutor = createExecutor { gameId, outcome, latency -> observers[gameId]?.onDecision(gameId, outcome, latency) }

    /**
     * 登記 [gameId] 屬於以 [observer] 接收結果的那一輪。
     *
     * @param gameId 對局。
     * @param observer 建立這一局的那一輪的接收者。
     */
    fun register(gameId: Uuid, observer: AiDecisionObserver) {
        observers[gameId] = observer
    }

    /**
     * 取消 [gameId] 的登記；登記的接收者已不是 [observer] 時不做事。
     *
     * @param gameId 對局。
     * @param observer 登記時的接收者。
     */
    fun unregister(gameId: Uuid, observer: AiDecisionObserver) {
        observers.remove(gameId, observer)
    }
}
