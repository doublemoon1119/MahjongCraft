package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.SettledWinPresentation
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * 一次胡牌結算完成後，把已建構好的呈現內容從胡牌 use case 交給
 * `GameFlowCoordinator` 的暫存交接點，每桌最多一筆。
 *
 * 為什麼需要這個東西：胡牌 use case（`DeclareTsumoUseCase`／`RespondToDiscardUseCase`／
 * `RespondToKanUseCase`）擁有建構 [SettledWinPresentation] 所需的全部素材（算役結果、規則模組、
 * 各種 resolver registry），但它執行的當下還不知道本局會不會就此結束——那要等
 * `ResolveWinRoundContinuationUseCase` 詢問過規則模組才有答案，而那發生在 use case 回傳之後。
 * 讓 use case 直接回傳這份內容給呼叫端則不可行：`GameActionRouter` 對所有指令（含第三方擴充指令）
 * 統一回傳 `Outcome<Unit, GameError>`，要讓演出內容穿過它就得改動每一種指令的回傳型別，連與胡牌
 * 無關的摸牌／捨牌都會被迫認識胡牌演出型別。
 *
 * 刻意**不持久化**：一筆內容只在單次指令派發內存活（use case [stage]、同一次派發的收斂階段
 * [take]），伺服器重啟後沒有任何路徑會去消費殘留值，持久化換不到任何恢復能力。真正需要跨重啟的是
 * 演出本身，而那已經分別由平台的呈現時間軸與各實體自己的 NBT 動畫佇列負責。
 *
 * **不會殘留舊資料**：[take] 一律移除該桌的暫存內容，即使贏家不符也一樣（此時回傳 null 並丟棄）。
 * 因此就算某次胡牌的收斂流程中途失敗，下一次胡牌也不可能拿到上一次的內容。
 */
@Single
class WinPresentationHandoff {
    private val presentationsByGameId = ConcurrentHashMap<Uuid, SettledWinPresentation>()

    /** 暫存 [gameId] 這次胡牌的呈現內容，覆寫任何尚未被取走的舊值。 */
    fun stage(gameId: Uuid, presentation: SettledWinPresentation) {
        presentationsByGameId[gameId] = presentation
    }

    /**
     * 取出並清除 [gameId] 暫存的呈現內容。
     *
     * [expectedWinnerPlayerIds] 作為關聯鍵：暫存內容的贏家集合必須完全相符才會回傳，否則視為
     * 不屬於這次胡牌的殘留資料，一樣清除但回傳 null——避免把上一次胡牌的演出誤播成這一次的。
     *
     * @return 這次胡牌的呈現內容；沒有暫存內容或贏家不符時為 null。
     */
    fun take(gameId: Uuid, expectedWinnerPlayerIds: Set<Uuid>): SettledWinPresentation? {
        val staged = presentationsByGameId.remove(gameId) ?: return null
        return staged.takeIf { it.winnerPlayerIds == expectedWinnerPlayerIds }
    }

    /** 丟棄 [gameId] 任何尚未取走的暫存內容，供收斂流程提前中止時清理。 */
    fun discard(gameId: Uuid) {
        presentationsByGameId.remove(gameId)
    }
}
